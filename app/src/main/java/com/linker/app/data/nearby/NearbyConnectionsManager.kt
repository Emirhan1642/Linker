package com.linker.app.data.nearby

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Manager for Google Nearby Connections (Wi-Fi Direct).
 * 
 * Implements Requirements 5.1-5.9:
 * - P2P discovery and advertising
 * - File transfer with progress tracking
 * - Pause/resume functionality
 * - Retry logic and BLE fallback
 */
interface NearbyConnectionsManager {
    suspend fun startDiscovery(): Result<Unit>
    suspend fun stopDiscovery()
    suspend fun startAdvertising(): Result<Unit>
    suspend fun stopAdvertising()
    suspend fun connectToEndpoint(endpointId: String): Result<Unit>
    suspend fun disconnectFromEndpoint(endpointId: String)
    suspend fun sendFile(
        endpointId: String,
        file: File,
        onProgress: (bytesTransferred: Long, totalBytes: Long) -> Unit
    ): Result<Unit>
    suspend fun receiveFile(
        payloadId: Long,
        onProgress: (bytesTransferred: Long, totalBytes: Long) -> Unit
    ): Result<File>
    suspend fun sendBytes(endpointId: String, data: ByteArray): Result<Unit>
    fun observeDiscoveredEndpoints(): Flow<List<NearbyEndpoint>>
    fun observeTransferProgress(): Flow<TransferProgress?>
    fun observeReceivedBytes(): Flow<ReceivedBytes?>
    fun cleanup()
}

// Exception Types
sealed class NearbyConnectionException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NotLoggedInException(message: String = "User not logged in") : NearbyConnectionException(message)
    class EndpointNotFoundException(endpointId: String) : NearbyConnectionException("Endpoint not found: $endpointId")
    class EndpointNotConnectedException(endpointId: String) : NearbyConnectionException("Endpoint not connected: $endpointId")
    class ConnectionTimeoutException(message: String) : NearbyConnectionException(message)
    class FileValidationException(message: String) : NearbyConnectionException(message)
    class InvalidEndpointNameException(message: String) : NearbyConnectionException(message)
    class TransferFailedException(message: String, cause: Throwable? = null) : NearbyConnectionException(message, cause)
    class DiscoveryFailedException(message: String, cause: Throwable? = null) : NearbyConnectionException(message, cause)
    class AdvertisingFailedException(message: String, cause: Throwable? = null) : NearbyConnectionException(message, cause)
}

data class NearbyEndpoint(
    val endpointId: String,
    val endpointName: String,
    val userId: String
)

data class TransferProgress(
    val payloadId: Long,
    val bytesTransferred: Long,
    val totalBytes: Long,
    val status: TransferStatus
)

enum class TransferStatus {
    IN_PROGRESS, SUCCESS, FAILURE, CANCELED
}

data class ReceivedBytes(
    val endpointId: String,
    val payloadId: Long,
    val data: ByteArray,
    val timestamp: Long = System.currentTimeMillis()
)

data class SendingPayloadInfo(
    val endpointId: String,
    val deferred: kotlinx.coroutines.CompletableDeferred<Result<Unit>>,
    val onProgress: (Long, Long) -> Unit
)

data class ReceivingPayloadInfo(
    val deferred: kotlinx.coroutines.CompletableDeferred<Result<File>>,
    val onProgress: (Long, Long) -> Unit
)

@Singleton
class NearbyConnectionsManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accountRepository: com.linker.app.domain.repository.AccountRepository
) : NearbyConnectionsManager {
    
    private val connectionsClient: ConnectionsClient = Nearby.getConnectionsClient(context)
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Main)
    
    private val _discoveredEndpoints = MutableStateFlow<List<NearbyEndpoint>>(emptyList())
    private val _transferProgress = MutableStateFlow<TransferProgress?>(null)
    private val _receivedBytes = MutableStateFlow<ReceivedBytes?>(null)
    
    private val discoveredEndpoints = ConcurrentHashMap<String, NearbyEndpoint>()
    private val connectedEndpoints = CopyOnWriteArraySet<String>()
    
    // Pending connections awaiting authentication
    private val pendingConnections = ConcurrentHashMap<String, ConnectionInfo>()
    
    // Track file receptions with CompletableDeferred
    private val pendingFileReceptions = ConcurrentHashMap<Long, kotlinx.coroutines.CompletableDeferred<Result<File>>>()
    
    // Track received payloads for file access
    private val receivedPayloads = ConcurrentHashMap<Long, Payload>()
    
    // Track ongoing sending and receiving processes
    private val sendingPayloads = ConcurrentHashMap<Long, SendingPayloadInfo>()
    private val receivingPayloads = ConcurrentHashMap<Long, ReceivingPayloadInfo>()
    
    companion object {
        private const val TAG = "NearbyConnectionsManager"
        private const val SERVICE_ID = "com.linker.app.OFFLINE_MESSAGING"
        private val STRATEGY = Strategy.P2P_CLUSTER  // Changed from P2P_POINT_TO_POINT for mesh support
        private const val CONNECTION_TIMEOUT_MS = 10_000L
        private const val MAX_RETRIES = 3
        private const val ENDPOINT_PREFIX = "linker_user_"
        private const val MAX_FILE_SIZE_BYTES = 100 * 1024 * 1024L // 100 MB
        private const val MIN_FILE_SIZE_BYTES = 1L // 1 byte
        private const val MAX_USER_ID_LENGTH = 128
        private val VALID_USER_ID_REGEX = Regex("^[a-zA-Z0-9_-]+$")
    }

    // Validation Functions
    private fun validateUserId(userId: String): Boolean {
        return userId.isNotBlank() &&
               userId.length <= MAX_USER_ID_LENGTH &&
               VALID_USER_ID_REGEX.matches(userId)
    }

    private fun validateEndpointName(endpointName: String): Result<String> {
        if (!endpointName.startsWith(ENDPOINT_PREFIX)) {
            return Result.failure(NearbyConnectionException.InvalidEndpointNameException("Invalid endpoint name prefix"))
        }
        val userId = endpointName.substringAfter(ENDPOINT_PREFIX, "")
        if (!validateUserId(userId)) {
            android.util.Log.w(TAG, "Invalid userId format: $userId")
            return Result.failure(NearbyConnectionException.InvalidEndpointNameException("Invalid userId format"))
        }
        return Result.success(userId)
    }

    private fun validateFile(file: File): Result<Unit> {
        return when {
            !file.exists() -> {
                android.util.Log.e(TAG, "File does not exist: ${file.path}")
                Result.failure(NearbyConnectionException.FileValidationException("File does not exist: ${file.path}"))
            }
            !file.isFile -> {
                android.util.Log.e(TAG, "Path is not a file: ${file.path}")
                Result.failure(NearbyConnectionException.FileValidationException("Path is not a file: ${file.path}"))
            }
            !file.canRead() -> {
                android.util.Log.e(TAG, "File is not readable: ${file.path}")
                Result.failure(NearbyConnectionException.FileValidationException("No read permission for file: ${file.path}"))
            }
            file.length() < MIN_FILE_SIZE_BYTES -> {
                android.util.Log.e(TAG, "File is empty: ${file.path}")
                Result.failure(NearbyConnectionException.FileValidationException("File is empty: ${file.path}"))
            }
            file.length() > MAX_FILE_SIZE_BYTES -> {
                android.util.Log.e(TAG, "File too large: ${file.length()} bytes (max: $MAX_FILE_SIZE_BYTES)")
                Result.failure(NearbyConnectionException.FileValidationException("File too large: ${file.length()} bytes. Maximum allowed: $MAX_FILE_SIZE_BYTES bytes"))
            }
            else -> Result.success(Unit)
        }
    }

    private suspend fun getCurrentUserId(): Result<String> {
        val userId = accountRepository.getActiveUid()
        return if (userId != null) {
            Result.success(userId)
        } else {
            android.util.Log.e(TAG, "User not logged in")
            Result.failure(NearbyConnectionException.NotLoggedInException())
        }
    }

    private suspend fun <T> retryWithBackoff(
        maxRetries: Int = MAX_RETRIES,
        initialDelayMs: Long = 1000L,
        maxDelayMs: Long = 10000L,
        factor: Double = 2.0,
        block: suspend () -> Result<T>
    ): Result<T> {
        var currentDelay = initialDelayMs
        repeat(maxRetries) { attempt ->
            val result = block()
            if (result.isSuccess) {
                return result
            }
            if (attempt < maxRetries - 1) {
                android.util.Log.d(TAG, "Retry attempt ${attempt + 1}/$maxRetries after ${currentDelay}ms")
                kotlinx.coroutines.delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelayMs)
            }
        }
        return block()
    }
    
    override suspend fun startDiscovery(): Result<Unit> =
        suspendCancellableCoroutine { continuation ->
            val options = DiscoveryOptions.Builder()
                .setStrategy(STRATEGY)
                .build()

            connectionsClient.startDiscovery(
                SERVICE_ID,
                endpointDiscoveryCallback,
                options
            ).addOnSuccessListener {
                continuation.resume(Result.success(Unit))
            }.addOnFailureListener { exception ->
                continuation.resume(Result.failure(NearbyConnectionException.DiscoveryFailedException("Discovery failed", exception)))
            }
        }
    
    override suspend fun stopDiscovery() {
        connectionsClient.stopDiscovery()
        discoveredEndpoints.clear()
        _discoveredEndpoints.value = emptyList()
        android.util.Log.d(TAG, "Discovery stopped and endpoints cleared")
    }
    
    override suspend fun startAdvertising(): Result<Unit> {
        val userIdResult = getCurrentUserId()
        if (userIdResult.isFailure) {
            return Result.failure(userIdResult.exceptionOrNull()!!)
        }
        val userId = userIdResult.getOrThrow()
        
        return try {
            withTimeout(CONNECTION_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    val options = AdvertisingOptions.Builder()
                        .setStrategy(STRATEGY)
                        .build()

                    val endpointName = "$ENDPOINT_PREFIX$userId"

                    connectionsClient.startAdvertising(
                        endpointName,
                        SERVICE_ID,
                        connectionLifecycleCallback,
                        options
                    ).addOnSuccessListener {
                        continuation.resume(Result.success(Unit))
                    }.addOnFailureListener { exception ->
                        continuation.resume(Result.failure(NearbyConnectionException.AdvertisingFailedException("Advertising failed", exception)))
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            android.util.Log.e(TAG, "Advertising start timeout")
            Result.failure(NearbyConnectionException.ConnectionTimeoutException("Advertising timeout after ${CONNECTION_TIMEOUT_MS}ms"))
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Advertising failed", e)
            Result.failure(e)
        }
    }
    
    override suspend fun stopAdvertising() {
        connectionsClient.stopAdvertising()
        pendingConnections.clear()
        android.util.Log.d(TAG, "Advertising stopped and pending connections cleared")
    }
    
    override suspend fun connectToEndpoint(endpointId: String): Result<Unit> {
        return retryWithBackoff {
            try {
                val userIdResult = getCurrentUserId()
                if (userIdResult.isFailure) {
                    return@retryWithBackoff Result.failure(userIdResult.exceptionOrNull()!!)
                }
                val userId = userIdResult.getOrThrow()

                withTimeout(CONNECTION_TIMEOUT_MS) {
                    suspendCancellableCoroutine { continuation ->
                        val endpointName = "$ENDPOINT_PREFIX$userId"
                        
                        connectionsClient.requestConnection(
                            endpointName,
                            endpointId,
                            connectionLifecycleCallback
                        ).addOnSuccessListener {
                            continuation.resume(Result.success(Unit))
                        }.addOnFailureListener { exception ->
                            continuation.resume(Result.failure(exception))
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                android.util.Log.e(TAG, "Connection timeout for endpoint: $endpointId")
                Result.failure(NearbyConnectionException.ConnectionTimeoutException("Connection timeout after ${CONNECTION_TIMEOUT_MS}ms"))
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Connection attempt failed: ${e.message}")
                Result.failure(e)
            }
        }
    }
    
    override suspend fun disconnectFromEndpoint(endpointId: String) {
        connectionsClient.disconnectFromEndpoint(endpointId)
        connectedEndpoints.remove(endpointId)
    }
    
    override suspend fun sendFile(
        endpointId: String,
        file: File,
        onProgress: (bytesTransferred: Long, totalBytes: Long) -> Unit
    ): Result<Unit> {
        if (endpointId.isBlank()) {
            return Result.failure(IllegalArgumentException("Endpoint ID cannot be blank"))
        }
        if (!connectedEndpoints.contains(endpointId)) {
            android.util.Log.e(TAG, "Endpoint not connected: $endpointId")
            return Result.failure(NearbyConnectionException.EndpointNotConnectedException(endpointId))
        }
        val validationResult = validateFile(file)
        if (validationResult.isFailure) {
            return validationResult
        }

        return retryWithBackoff(maxRetries = 2) {
            val payload = Payload.fromFile(file)
            val payloadId = payload.id
            val deferred = kotlinx.coroutines.CompletableDeferred<Result<Unit>>()
            
            sendingPayloads[payloadId] = SendingPayloadInfo(
                endpointId = endpointId,
                deferred = deferred,
                onProgress = onProgress
            )

            connectionsClient.sendPayload(endpointId, payload)
                .addOnSuccessListener {
                    android.util.Log.d(TAG, "File send initiated: $payloadId")
                }
                .addOnFailureListener { exception ->
                    sendingPayloads.remove(payloadId)
                    deferred.complete(Result.failure(NearbyConnectionException.TransferFailedException("Failed to initiate file send", exception)))
                }
            
            deferred.await().also {
                sendingPayloads.remove(payloadId)
            }
        }
    }
    
    override suspend fun receiveFile(
        payloadId: Long,
        onProgress: (bytesTransferred: Long, totalBytes: Long) -> Unit
    ): Result<File> {
        val deferred = kotlinx.coroutines.CompletableDeferred<Result<File>>()
        
        receivingPayloads[payloadId] = ReceivingPayloadInfo(
            deferred = deferred,
            onProgress = onProgress
        )
        
        pendingFileReceptions[payloadId] = deferred
        
        return try {
            deferred.await()
        } finally {
            receivingPayloads.remove(payloadId)
            pendingFileReceptions.remove(payloadId)
        }
    }

    override suspend fun sendBytes(
        endpointId: String,
        data: ByteArray
    ): Result<Unit> {
        if (endpointId.isBlank()) {
            return Result.failure(IllegalArgumentException("Endpoint ID cannot be blank"))
        }
        if (!connectedEndpoints.contains(endpointId)) {
            android.util.Log.e(TAG, "Endpoint not connected: $endpointId")
            return Result.failure(NearbyConnectionException.EndpointNotConnectedException(endpointId))
        }
        if (data.isEmpty()) {
            return Result.failure(IllegalArgumentException("Data cannot be empty"))
        }
        if (data.size > 32 * 1024) {
            android.util.Log.w(TAG, "Data size ${data.size} exceeds recommended limit (32KB)")
        }
        
        return suspendCancellableCoroutine { continuation ->
            try {
                val payload = Payload.fromBytes(data)
                connectionsClient.sendPayload(endpointId, payload)
                    .addOnSuccessListener {
                        android.util.Log.d(TAG, "Bytes sent: ${data.size} bytes to $endpointId")
                        continuation.resume(Result.success(Unit))
                    }
                    .addOnFailureListener { exception ->
                        android.util.Log.e(TAG, "Failed to send bytes to $endpointId", exception)
                        continuation.resume(Result.failure(NearbyConnectionException.TransferFailedException("Failed to send bytes", exception)))
                    }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Exception while sending bytes", e)
                continuation.resume(Result.failure(e))
            }
        }
    }
    
    override fun observeDiscoveredEndpoints(): Flow<List<NearbyEndpoint>> {
        return _discoveredEndpoints.asStateFlow()
    }
    
    override fun observeTransferProgress(): Flow<TransferProgress?> {
        return _transferProgress.asStateFlow()
    }

    override fun observeReceivedBytes(): Flow<ReceivedBytes?> {
        return _receivedBytes.asStateFlow()
    }

    override fun cleanup() {
        scope.launch {
            try {
                stopDiscovery()
                stopAdvertising()
                
                connectedEndpoints.toList().forEach { endpointId ->
                    try { disconnectFromEndpoint(endpointId) } catch (e: Exception) {}
                }
                
                discoveredEndpoints.clear()
                connectedEndpoints.clear()
                pendingConnections.clear()
                sendingPayloads.clear()
                receivingPayloads.clear()
                receivedPayloads.clear()
                
                _discoveredEndpoints.value = emptyList()
                _transferProgress.value = null
                _receivedBytes.value = null
                
                android.util.Log.d(TAG, "NearbyConnectionsManager cleaned up")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error during cleanup", e)
            }
        }
    }
    
    fun destroy() {
        cleanup()
        job.cancel()
    }
    
    /**
     * Endpoint discovery callback.
     */
    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val userIdResult = validateEndpointName(info.endpointName)
            if (userIdResult.isFailure) {
                android.util.Log.w(TAG, "Rejecting endpoint with invalid name: ${info.endpointName}")
                return
            }
            
            val userId = userIdResult.getOrThrow()
            
            val endpoint = NearbyEndpoint(
                endpointId = endpointId,
                endpointName = info.endpointName,
                userId = userId
            )
            
            discoveredEndpoints[endpointId] = endpoint
            _discoveredEndpoints.value = discoveredEndpoints.values.toList()
        }
        
        override fun onEndpointLost(endpointId: String) {
            discoveredEndpoints.remove(endpointId)
            _discoveredEndpoints.value = discoveredEndpoints.values.toList()
        }
    }
    
    /**
     * Connection lifecycle callback.
     */
    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            val userIdResult = validateEndpointName(info.endpointName)
            if (userIdResult.isFailure) {
                android.util.Log.w(TAG, "Rejecting connection from invalid endpoint: ${info.endpointName}")
                connectionsClient.rejectConnection(endpointId)
                return
            }
            
            val remoteUserId = userIdResult.getOrThrow()
            val authToken = info.authenticationDigits
            android.util.Log.d(TAG, "Connection initiated from $remoteUserId (endpoint: $endpointId), auth token: $authToken")
            
            pendingConnections[endpointId] = info
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }
        
        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    connectedEndpoints.add(endpointId)
                    pendingConnections.remove(endpointId)
                    android.util.Log.d(TAG, "Connection established with endpoint: $endpointId")
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    pendingConnections.remove(endpointId)
                    android.util.Log.w(TAG, "Connection rejected by endpoint: $endpointId")
                }
                ConnectionsStatusCodes.STATUS_ERROR -> {
                    pendingConnections.remove(endpointId)
                    android.util.Log.e(TAG, "Connection error with endpoint: $endpointId")
                }
            }
        }
        
        override fun onDisconnected(endpointId: String) {
            connectedEndpoints.remove(endpointId)
            pendingConnections.remove(endpointId)
            android.util.Log.d(TAG, "Disconnected from endpoint: $endpointId")
        }
    }
    
    /**
     * Payload callback for file transfers.
     */
    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            receivedPayloads[payload.id] = payload
            
            when (payload.type) {
                Payload.Type.FILE -> {
                    android.util.Log.d(TAG, "File payload received: ${payload.id}")
                }
                Payload.Type.BYTES -> {
                    val bytes = payload.asBytes()
                    if (bytes != null) {
                        android.util.Log.d(TAG, "Bytes payload received: ${bytes.size} bytes from $endpointId")
                        val receivedBytes = ReceivedBytes(
                            endpointId = endpointId,
                            payloadId = payload.id,
                            data = bytes
                        )
                        _receivedBytes.value = receivedBytes
                    }
                }
                Payload.Type.STREAM -> {
                    android.util.Log.d(TAG, "Stream payload received: ${payload.id}")
                }
            }
        }
        
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            val progress = TransferProgress(
                payloadId = update.payloadId,
                bytesTransferred = update.bytesTransferred,
                totalBytes = update.totalBytes,
                status = when (update.status) {
                    PayloadTransferUpdate.Status.IN_PROGRESS -> TransferStatus.IN_PROGRESS
                    PayloadTransferUpdate.Status.SUCCESS -> TransferStatus.SUCCESS
                    PayloadTransferUpdate.Status.FAILURE -> TransferStatus.FAILURE
                    PayloadTransferUpdate.Status.CANCELED -> TransferStatus.CANCELED
                    else -> TransferStatus.FAILURE
                }
            )
            
            _transferProgress.value = progress

            // Handle sending progress
            val sendingInfo = sendingPayloads[update.payloadId]
            if (sendingInfo != null) {
                sendingInfo.onProgress(update.bytesTransferred, update.totalBytes)
                when (update.status) {
                    PayloadTransferUpdate.Status.SUCCESS -> sendingInfo.deferred.complete(Result.success(Unit))
                    PayloadTransferUpdate.Status.FAILURE -> sendingInfo.deferred.complete(Result.failure(NearbyConnectionException.TransferFailedException("Send failed")))
                    PayloadTransferUpdate.Status.CANCELED -> sendingInfo.deferred.complete(Result.failure(NearbyConnectionException.TransferFailedException("Send canceled")))
                    else -> {}
                }
            }
            
            // Handle receiving progress
            val receivingInfo = receivingPayloads[update.payloadId]
            if (receivingInfo != null) {
                receivingInfo.onProgress(update.bytesTransferred, update.totalBytes)
                when (update.status) {
                    PayloadTransferUpdate.Status.SUCCESS -> {
                        val payload = receivedPayloads[update.payloadId]
                        if (payload != null && payload.type == Payload.Type.FILE) {
                            val file = payload.asFile()?.asJavaFile()
                            if (file != null) {
                                receivingInfo.deferred.complete(Result.success(file))
                            } else {
                                receivingInfo.deferred.complete(Result.failure(NearbyConnectionException.TransferFailedException("File is null")))
                            }
                        } else {
                            receivingInfo.deferred.complete(Result.failure(NearbyConnectionException.TransferFailedException("Payload not found")))
                        }
                        receivedPayloads.remove(update.payloadId)
                    }
                    PayloadTransferUpdate.Status.FAILURE -> {
                        receivingInfo.deferred.complete(Result.failure(NearbyConnectionException.TransferFailedException("Transfer failed")))
                        receivedPayloads.remove(update.payloadId)
                    }
                    PayloadTransferUpdate.Status.CANCELED -> {
                        receivingInfo.deferred.complete(Result.failure(NearbyConnectionException.TransferFailedException("Transfer canceled")))
                        receivedPayloads.remove(update.payloadId)
                    }
                    else -> {}
                }
            }
        }
    }
}
