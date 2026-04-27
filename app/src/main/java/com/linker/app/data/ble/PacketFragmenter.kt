package com.linker.app.data.ble

/**
 * Handles fragmentation and reassembly of large BLE packets
 * 
 * When a message payload exceeds MAX_PAYLOAD_SIZE (391 bytes), it must be
 * split into multiple fragments for transmission over BLE.
 */
class PacketFragmenter {
    
    /**
     * Fragment a large payload into multiple BLE packets
     * 
     * @param messageId Message UUID
     * @param senderId Sender user ID
     * @param recipientId Recipient user ID
     * @param ttl Time-to-live
     * @param encryptedPayload Full encrypted payload to fragment
     * @return List of BLE packets (fragments)
     */
    fun fragment(
        messageId: String,
        senderId: String,
        recipientId: String,
        ttl: Byte,
        encryptedPayload: ByteArray
    ): List<BLEPacket> {
        if (encryptedPayload.size <= BLEPacket.MAX_PAYLOAD_SIZE) {
            // No fragmentation needed
            return listOf(
                BLEPacket.create(
                    messageId = messageId,
                    senderId = senderId,
                    recipientId = recipientId,
                    ttl = ttl,
                    hopCount = 0,
                    fragmentIndex = 0,
                    totalFragments = 1,
                    encryptedPayload = encryptedPayload
                )
            )
        }
        
        // Calculate number of fragments needed
        val totalFragments = (encryptedPayload.size + BLEPacket.MAX_PAYLOAD_SIZE - 1) / BLEPacket.MAX_PAYLOAD_SIZE
        
        if (totalFragments > Short.MAX_VALUE) {
            throw IllegalArgumentException("Payload too large: requires $totalFragments fragments, max is ${Short.MAX_VALUE}")
        }
        
        val fragments = mutableListOf<BLEPacket>()
        var offset = 0
        
        for (i in 0 until totalFragments) {
            val fragmentSize = minOf(BLEPacket.MAX_PAYLOAD_SIZE, encryptedPayload.size - offset)
            val fragmentPayload = encryptedPayload.copyOfRange(offset, offset + fragmentSize)
            
            val packet = BLEPacket.create(
                messageId = messageId,
                senderId = senderId,
                recipientId = recipientId,
                ttl = ttl,
                hopCount = 0,
                fragmentIndex = i.toShort(),
                totalFragments = totalFragments.toShort(),
                encryptedPayload = fragmentPayload
            )
            
            fragments.add(packet)
            offset += fragmentSize
        }
        
        return fragments
    }
    
    /**
     * Reassemble fragments into original payload
     * 
     * @param fragments List of packet fragments (must be complete set)
     * @return Original encrypted payload
     * @throws IllegalArgumentException if fragments are invalid or incomplete
     */
    fun reassemble(fragments: List<BLEPacket>): ByteArray {
        if (fragments.isEmpty()) {
            throw IllegalArgumentException("No fragments provided")
        }
        
        // Validate all fragments have same metadata
        val firstFragment = fragments[0]
        val messageId = firstFragment.messageId
        val totalFragments = firstFragment.totalFragments.toInt()
        
        if (fragments.size != totalFragments) {
            throw IllegalArgumentException(
                "Incomplete fragments: expected $totalFragments, got ${fragments.size}"
            )
        }
        
        // Validate all fragments belong to same message
        for (fragment in fragments) {
            if (fragment.messageId != messageId) {
                throw IllegalArgumentException(
                    "Fragment messageId mismatch: expected $messageId, got ${fragment.messageId}"
                )
            }
            if (fragment.totalFragments != totalFragments.toShort()) {
                throw IllegalArgumentException(
                    "Fragment totalFragments mismatch: expected $totalFragments, got ${fragment.totalFragments}"
                )
            }
        }
        
        // Sort fragments by index
        val sortedFragments = fragments.sortedBy { it.fragmentIndex }
        
        // Validate fragment indices are sequential
        for (i in sortedFragments.indices) {
            if (sortedFragments[i].fragmentIndex != i.toShort()) {
                throw IllegalArgumentException(
                    "Missing fragment at index $i"
                )
            }
        }
        
        // Reassemble payload
        val totalSize = sortedFragments.sumOf { it.payloadLength.toInt() }
        val reassembledPayload = ByteArray(totalSize)
        var offset = 0
        
        for (fragment in sortedFragments) {
            System.arraycopy(
                fragment.encryptedPayload,
                0,
                reassembledPayload,
                offset,
                fragment.payloadLength.toInt()
            )
            offset += fragment.payloadLength.toInt()
        }
        
        return reassembledPayload
    }
    
    /**
     * Check if fragments form a complete set
     * 
     * @param fragments List of fragments to check
     * @return true if fragments are complete, false otherwise
     */
    fun isComplete(fragments: List<BLEPacket>): Boolean {
        if (fragments.isEmpty()) return false
        
        val totalFragments = fragments[0].totalFragments.toInt()
        if (fragments.size != totalFragments) return false
        
        val messageId = fragments[0].messageId
        val indices = mutableSetOf<Short>()
        
        for (fragment in fragments) {
            if (fragment.messageId != messageId) return false
            if (fragment.totalFragments != totalFragments.toShort()) return false
            if (fragment.fragmentIndex < 0 || fragment.fragmentIndex >= totalFragments) return false
            if (!indices.add(fragment.fragmentIndex)) return false // Duplicate index
        }
        
        // Check all indices from 0 to totalFragments-1 are present
        return indices.size == totalFragments
    }
}
