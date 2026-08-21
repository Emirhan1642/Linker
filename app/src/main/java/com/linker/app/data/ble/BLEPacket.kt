package com.linker.app.data.ble

import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.zip.CRC32

/**
 * BLE Mesh Packet Structure
 * 
 * Represents a packet transmitted over BLE mesh network.
 * Total size: Variable (max 512 bytes with MTU negotiation)
 * 
 * Header: 121 bytes
 * - version: 1 byte
 * - messageId: 36 bytes (UUID string)
 * - senderId: 36 bytes (User ID)
 * - recipientId: 36 bytes (User ID)
 * - ttl: 1 byte
 * - hopCount: 1 byte
 * - fragmentIndex: 2 bytes
 * - totalFragments: 2 bytes
 * - payloadLength: 2 bytes
 * - checksum: 4 bytes
 * 
 * Payload: Up to 391 bytes (512 - 121)
 */
data class BLEPacket(
    val version: Byte = 1,                    // Protocol version
    val messageId: String,                    // Message UUID
    val senderId: String,                     // Sender user ID
    val recipientId: String,                  // Recipient user ID
    val ttl: Byte,                           // Time-to-live in hops
    val hopCount: Byte,                      // Current hop count
    val fragmentIndex: Short = 0,            // Fragment index (0-based)
    val totalFragments: Short = 1,           // Total number of fragments
    val payloadLength: Short,                // Payload length in bytes
    val encryptedPayload: ByteArray,         // Encrypted message content
    val checksum: Int                        // CRC32 checksum
) {
    companion object {
        // Header size: version(1) + messageId(36) + senderId(36) + recipientId(36) 
        //              + ttl(1) + hopCount(1) + fragmentIndex(2) + totalFragments(2) 
        //              + payloadLength(2) + checksum(4)
        const val HEADER_SIZE = 1 + 36 + 36 + 36 + 1 + 1 + 2 + 2 + 2 + 4 // 121 bytes
        const val MAX_PAYLOAD_SIZE = 512 - HEADER_SIZE // 391 bytes
        const val MTU_SIZE = 512
        
        // ByteBuffer object pool for reducing allocation overhead
        private const val POOL_SIZE = 10
        private val bufferPool = ArrayBlockingQueue<ByteBuffer>(POOL_SIZE)
        
        /**
         * Get a ByteBuffer from the pool or create a new one
         * 
         * @param capacity Required capacity
         * @return ByteBuffer with at least the requested capacity
         */
        private fun getBuffer(capacity: Int): ByteBuffer {
            val buffer = bufferPool.poll()
            return if (buffer != null && buffer.capacity() >= capacity) {
                buffer.clear()
                buffer
            } else {
                ByteBuffer.allocate(capacity)
            }
        }
        
        /**
         * Return a ByteBuffer to the pool for reuse
         * 
         * @param buffer ByteBuffer to return
         */
        private fun returnBuffer(buffer: ByteBuffer) {
            buffer.clear()
            bufferPool.offer(buffer)
        }
        
        /**
         * Serialize packet to byte array for BLE transmission
         * 
         * Uses object pooling to reduce ByteBuffer allocation overhead.
         * 
         * @param packet The packet to serialize
         * @return Byte array representation of the packet
         */
        private fun encodeIdToBytes(id: String): ByteArray {
            val bytes = id.toByteArray(Charsets.UTF_8)
            return if (bytes.size >= 36) {
                bytes.copyOf(36)
            } else {
                ByteArray(36).apply {
                    System.arraycopy(bytes, 0, this, 0, bytes.size)
                }
            }
        }

        private fun decodeIdFromBytes(bytes: ByteArray): String {
            val zeroIndex = bytes.indexOf(0.toByte())
            val length = if (zeroIndex >= 0) zeroIndex else bytes.size
            return String(bytes, 0, length, Charsets.UTF_8).trim()
        }

        fun serialize(packet: BLEPacket): ByteArray {
            val capacity = HEADER_SIZE + packet.payloadLength.toInt()
            val buffer = getBuffer(capacity)
            
            try {
                // Write header
                buffer.put(packet.version)
                buffer.put(encodeIdToBytes(packet.messageId))
                buffer.put(encodeIdToBytes(packet.senderId))
                buffer.put(encodeIdToBytes(packet.recipientId))
                buffer.put(packet.ttl)
                buffer.put(packet.hopCount)
                buffer.putShort(packet.fragmentIndex)
                buffer.putShort(packet.totalFragments)
                buffer.putShort(packet.payloadLength)
                
                // Write payload
                buffer.put(packet.encryptedPayload)
                
                // Write checksum
                buffer.putInt(packet.checksum)
                
                return buffer.array().copyOf(capacity)
            } finally {
                returnBuffer(buffer)
            }
        }
        
        /**
         * Deserialize byte array to BLEPacket
         * 
         * @param data Byte array to deserialize
         * @return Deserialized BLEPacket
         * @throws IllegalArgumentException if data is invalid or checksum fails
         */
        fun deserialize(data: ByteArray): BLEPacket {
            if (data.size < HEADER_SIZE) {
                throw IllegalArgumentException("Data too short: ${data.size} bytes, expected at least $HEADER_SIZE")
            }
            
            val buffer = ByteBuffer.wrap(data)
            
            // Read header
            val version = buffer.get()
            
            val messageIdBytes = ByteArray(36)
            buffer.get(messageIdBytes)
            val messageId = decodeIdFromBytes(messageIdBytes)
            
            val senderIdBytes = ByteArray(36)
            buffer.get(senderIdBytes)
            val senderId = decodeIdFromBytes(senderIdBytes)
            
            val recipientIdBytes = ByteArray(36)
            buffer.get(recipientIdBytes)
            val recipientId = decodeIdFromBytes(recipientIdBytes)
            
            val ttl = buffer.get()
            val hopCount = buffer.get()
            val fragmentIndex = buffer.getShort()
            val totalFragments = buffer.getShort()
            val payloadLength = buffer.getShort()
            
            // Read payload
            if (payloadLength < 0 || payloadLength > MAX_PAYLOAD_SIZE) {
                throw IllegalArgumentException("Invalid payload length: $payloadLength")
            }
            if (buffer.remaining() < payloadLength.toInt() + 4) {
                throw IllegalArgumentException("Buffer underflow: remaining ${buffer.remaining()} bytes, need ${payloadLength.toInt() + 4}")
            }
            
            val payload = ByteArray(payloadLength.toInt())
            buffer.get(payload)
            
            // Read checksum
            val checksum = buffer.getInt()
            
            val packet = BLEPacket(
                version = version,
                messageId = messageId,
                senderId = senderId,
                recipientId = recipientId,
                ttl = ttl,
                hopCount = hopCount,
                fragmentIndex = fragmentIndex,
                totalFragments = totalFragments,
                payloadLength = payloadLength,
                encryptedPayload = payload,
                checksum = checksum
            )
            
            // Validate checksum
            if (!validateChecksum(packet)) {
                throw IllegalArgumentException("Invalid checksum for packet $messageId")
            }
            
            return packet
        }
        
        /**
         * Calculate CRC32 checksum for packet data
         * 
         * @param data Data to calculate checksum for (excluding checksum field)
         * @return CRC32 checksum as Int
         */
        fun calculateChecksum(data: ByteArray): Int {
            val crc = CRC32()
            crc.update(data)
            return crc.value.toInt()
        }
        
        /**
         * Validate packet checksum
         * 
         * Uses object pooling to reduce ByteBuffer allocation overhead.
         * 
         * @param packet Packet to validate
         * @return true if checksum is valid, false otherwise
         */
        fun validateChecksum(packet: BLEPacket): Boolean {
            val capacity = HEADER_SIZE - 4 + packet.payloadLength.toInt()
            val buffer = getBuffer(capacity)
            
            try {
                buffer.put(packet.version)
                buffer.put(encodeIdToBytes(packet.messageId))
                buffer.put(encodeIdToBytes(packet.senderId))
                buffer.put(encodeIdToBytes(packet.recipientId))
                buffer.put(packet.ttl)
                buffer.put(packet.hopCount)
                buffer.putShort(packet.fragmentIndex)
                buffer.putShort(packet.totalFragments)
                buffer.putShort(packet.payloadLength)
                buffer.put(packet.encryptedPayload)
                
                val data = buffer.array().copyOf(capacity)
                val calculatedChecksum = calculateChecksum(data)
                return calculatedChecksum == packet.checksum
            } finally {
                returnBuffer(buffer)
            }
        }
        
        /**
         * Create a new packet with calculated checksum
         * 
         * Uses object pooling to reduce ByteBuffer allocation overhead.
         * 
         * @param version Protocol version
         * @param messageId Message UUID
         * @param senderId Sender user ID
         * @param recipientId Recipient user ID
         * @param ttl Time-to-live
         * @param hopCount Current hop count
         * @param fragmentIndex Fragment index
         * @param totalFragments Total fragments
         * @param encryptedPayload Encrypted payload
         * @return BLEPacket with calculated checksum
         */
        fun create(
            version: Byte = 1,
            messageId: String,
            senderId: String,
            recipientId: String,
            ttl: Byte,
            hopCount: Byte = 0,
            fragmentIndex: Short = 0,
            totalFragments: Short = 1,
            encryptedPayload: ByteArray
        ): BLEPacket {
            val payloadLength = encryptedPayload.size.toShort()
            val capacity = HEADER_SIZE - 4 + payloadLength.toInt()
            val buffer = getBuffer(capacity)
            
            try {
                // Create packet without checksum
                buffer.put(version)
                buffer.put(encodeIdToBytes(messageId))
                buffer.put(encodeIdToBytes(senderId))
                buffer.put(encodeIdToBytes(recipientId))
                buffer.put(ttl)
                buffer.put(hopCount)
                buffer.putShort(fragmentIndex)
                buffer.putShort(totalFragments)
                buffer.putShort(payloadLength)
                buffer.put(encryptedPayload)
                
                val data = buffer.array().copyOf(capacity)
                val checksum = calculateChecksum(data)
                
                return BLEPacket(
                    version = version,
                    messageId = messageId,
                    senderId = senderId,
                    recipientId = recipientId,
                    ttl = ttl,
                    hopCount = hopCount,
                    fragmentIndex = fragmentIndex,
                    totalFragments = totalFragments,
                    payloadLength = payloadLength,
                    encryptedPayload = encryptedPayload,
                    checksum = checksum
                )
            } finally {
                returnBuffer(buffer)
            }
        }
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as BLEPacket
        
        if (version != other.version) return false
        if (messageId != other.messageId) return false
        if (senderId != other.senderId) return false
        if (recipientId != other.recipientId) return false
        if (ttl != other.ttl) return false
        if (hopCount != other.hopCount) return false
        if (fragmentIndex != other.fragmentIndex) return false
        if (totalFragments != other.totalFragments) return false
        if (payloadLength != other.payloadLength) return false
        if (!encryptedPayload.contentEquals(other.encryptedPayload)) return false
        if (checksum != other.checksum) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = version.toInt()
        result = 31 * result + messageId.hashCode()
        result = 31 * result + senderId.hashCode()
        result = 31 * result + recipientId.hashCode()
        result = 31 * result + ttl
        result = 31 * result + hopCount
        result = 31 * result + fragmentIndex
        result = 31 * result + totalFragments
        result = 31 * result + payloadLength
        result = 31 * result + encryptedPayload.contentHashCode()
        result = 31 * result + checksum
        return result
    }
}
