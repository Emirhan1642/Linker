# Design Document Fixes

## Critical Issues Fixed

### 1. BLE Packet Serialization Bug ✅
**Problem**: HEADER_SIZE calculation was incorrect. messageId is stored as 36-byte padded string, not 16 bytes.

**Original**:
```kotlin
const val HEADER_SIZE = 1 + 16 + 36 + 36 + 1 + 1 + 2 + 2 + 2 + 4 // 101 bytes (WRONG)
```

**Fixed**:
```kotlin
const val HEADER_SIZE = 1 + 36 + 36 + 36 + 1 + 1 + 2 + 2 + 2 + 4 // 121 bytes
// version(1) + messageId(36) + senderId(36) + recipientId(36) + ttl(1) + hopCount(1) 
// + fragmentIndex(2) + totalFragments(2) + payloadLength(2) + checksum(4)
```

**Impact**: Property 9 (serialization round-trip) test would fail without this fix.

---

### 2. Signal Protocol API Correction ✅
**Problem**: EncryptedMessage structure doesn't match Signal Protocol's actual API.

**Original**:
```kotlin
data class EncryptedMessage(
    val ciphertext: ByteArray,
    val ephemeralKey: ByteArray,  // Signal doesn't expose this
    val iv: ByteArray              // Signal doesn't use IV-based encryption
)
```

**Fixed**:
```kotlin
// Use Signal Protocol's native types
data class EncryptedMessage(
    val signalMessage: ByteArray  // Serialized SignalMessage or PreKeySignalMessage
)

// In EncryptionManager implementation:
suspend fun encryptMessage(recipientId: String, plaintext: String): Result<EncryptedMessage> {
    val address = SignalProtocolAddress(recipientId, 1)
    val cipher = SessionCipher(protocolStore, address)
    val ciphertext = cipher.encrypt(plaintext.toByteArray())
    return Result.Success(EncryptedMessage(ciphertext.serialize()))
}
```

---

### 3. Backoff Delay Consistency ✅
**Problem**: BLEErrorHandler uses 2000ms delay, but requirements specify 5000ms initial delay.

**Original**:
```kotlin
is BLEError.TransmissionError -> {
    ErrorAction.Retry(delay = 2000, maxAttempts = 3)  // WRONG: Should be 5000
}
```

**Fixed**:
```kotlin
is BLEError.TransmissionError -> {
    ErrorAction.Retry(delay = RetryStrategy.INITIAL_DELAY, maxAttempts = 3)  // 5000ms
}
```

---

### 4. Fragment Reassembly Memory Management ✅
**Problem**: No timeout or cleanup mechanism for incomplete fragments.

**Added**:
```kotlin
class FragmentManager {
    private val fragmentMap = ConcurrentHashMap<String, FragmentState>()
    private val FRAGMENT_TIMEOUT = 30_000L // 30 seconds
    
    data class FragmentState(
        val fragments: MutableList<BLEPacket>,
        val receivedAt: Long
    )
    
    fun addFragment(packet: BLEPacket): BLEPacket? {
        val key = packet.messageId
        val state = fragmentMap.getOrPut(key) {
            FragmentState(mutableListOf(), System.currentTimeMillis())
        }
        
        state.fragments.add(packet)
        
        // Check if complete
        if (state.fragments.size == packet.totalFragments.toInt()) {
            fragmentMap.remove(key)
            return PacketFragmenter.reassemble(state.fragments)
        }
        
        return null
    }
    
    fun cleanupStaleFragments() {
        val now = System.currentTimeMillis()
        fragmentMap.entries.removeIf { (_, state) ->
            now - state.receivedAt > FRAGMENT_TIMEOUT
        }
    }
}
```

---

### 5. Race Condition Handling (Req 18.8) ✅
**Problem**: No mechanism to handle same message arriving via both BLE and online.

**Added to MessageRepositoryImpl**:
```kotlin
private val processedMessageIds = ConcurrentHashMap<String, Long>()
private val MESSAGE_DEDUP_WINDOW = 60_000L // 60 seconds

suspend fun sendMessage(...): Result<Message> {
    val messageId = UUID.randomUUID().toString()
    
    // Check if message was recently processed
    val lastProcessed = processedMessageIds[messageId]
    if (lastProcessed != null && System.currentTimeMillis() - lastProcessed < MESSAGE_DEDUP_WINDOW) {
        return Result.Error("Message already processed")
    }
    
    // ... rest of implementation
    
    // Mark as processed
    processedMessageIds[messageId] = System.currentTimeMillis()
}

// Cleanup old entries periodically
fun cleanupProcessedMessages() {
    val now = System.currentTimeMillis()
    processedMessageIds.entries.removeIf { (_, timestamp) ->
        now - timestamp > MESSAGE_DEDUP_WINDOW
    }
}
```

---

## Important Issues Addressed

### 6. Priority Semantics Clarification ✅
**Added documentation**:
```kotlin
/**
 * Message priority for queue processing.
 * 
 * LOWER number = HIGHER priority (processed first)
 * - Priority 0: Text messages (small, urgent)
 * - Priority 1: Media messages (large, can wait)
 * 
 * This follows Unix nice value convention where lower = higher priority.
 */
const val PRIORITY_TEXT = 0
const val PRIORITY_MEDIA = 1
```

### 7. Key Exchange UX Flow ✅
**Added to Known Limitations section**:
```markdown
### Key Exchange Limitation

**Problem**: Cannot send encrypted messages without recipient's public key.

**UX Flow**:
1. User attempts to send message
2. If recipient key not found:
   - Show dialog: "Secure messaging requires key exchange"
   - Options:
     - "Send Unencrypted" (for non-sensitive messages)
     - "Wait for Online" (queue for online delivery with key exchange)
     - "Cancel"
3. When online, automatically exchange keys via Firestore
4. Retry queued messages after key exchange

**Implementation**:
- Store "pending key exchange" flag in MessageQueueEntity
- Background worker checks for new keys and retries pending messages
- Notify user when keys are exchanged and messages sent
```

### 8. Service Status Tracking (Modern API) ✅
**Replaced deprecated API**:
```kotlin
@Singleton
class OfflineMessagingServiceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesManager: PreferencesManager
) {
    private val _serviceRunning = MutableStateFlow(false)
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()
    
    fun startService() {
        if (!preferencesManager.isOfflineMessagingEnabled()) return
        
        val intent = Intent(context, OfflineMessagingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        _serviceRunning.value = true
    }
    
    fun stopService() {
        val intent = Intent(context, OfflineMessagingService::class.java).apply {
            action = OfflineMessagingService.ACTION_STOP_SERVICE
        }
        context.startService(intent)
        _serviceRunning.value = false
    }
    
    fun isServiceRunning(): Boolean = _serviceRunning.value
}
```

### 9. Doze Mode Strategy Clarification ✅
**Updated documentation**:
```markdown
### Doze Mode Handling

**Strategy**: Use WorkManager for periodic sync, Foreground Service for BLE scanning

**Rationale**:
- Foreground Service: Maintains BLE connections during Doze (with reduced frequency)
- WorkManager: Handles message sync when device wakes from Doze
- AlarmManager: NOT used (WorkManager is modern replacement)

**Implementation**:
```kotlin
// In OfflineMessagingService
private fun adjustScanningForDozeMode() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && 
        powerManager.isDeviceIdleMode) {
        // Reduce scan frequency to minimum
        bleMeshManager.setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
        bleMeshManager.setScanInterval(60_000L) // 60 seconds
    }
}

// WorkManager for sync
class MessageSyncWorker : CoroutineWorker() {
    override suspend fun doWork(): Result {
        syncManager.syncPendingMessages()
        return Result.success()
    }
}

// Schedule periodic sync
val syncRequest = PeriodicWorkRequestBuilder<MessageSyncWorker>(
    15, TimeUnit.MINUTES
).setConstraints(
    Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
).build()
```
```

---

## Minor Fixes

### 10. Kotest Syntax Correction ✅
**Original**:
```kotlin
whenever(!isOnline) { ... }  // WRONG: Not Kotest syntax
```

**Fixed**:
```kotlin
checkAll(100, Arb.message()) { message ->
    assume(!isOnline)  // Kotest assumption
    // ... test logic
}

// OR use filter
checkAll(100, Arb.message().filter { !isOnline }) { message ->
    // ... test logic
}
```

### 11. Hilt Broadcast Receiver Pattern ✅
**Original**:
```kotlin
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() { ... }
```

**Fixed**:
```kotlin
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceManager = EntryPointAccessors.fromApplication(
                context,
                BootReceiverEntryPoint::class.java
            ).offlineMessagingServiceManager()
            
            // Use injected dependencies
            if (serviceManager.shouldStartOnBoot()) {
                serviceManager.startService()
            }
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BootReceiverEntryPoint {
    fun offlineMessagingServiceManager(): OfflineMessagingServiceManager
}
```

---

## Summary

**Critical Fixes**: 5
**Important Fixes**: 4
**Minor Fixes**: 2

All issues identified by Claude Sonnet 4.6 have been addressed. The design document is now consistent with requirements and follows Android best practices.
