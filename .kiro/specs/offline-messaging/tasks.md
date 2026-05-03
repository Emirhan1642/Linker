# Implementation Plan: Offline Messaging

## Overview

Bu implementation plan, Linker Android uygulamasına offline messaging özelliğini eklemek için gerekli tüm adımları içerir. Özellik, BLE mesh networking, Wi-Fi Direct, Signal Protocol encryption ve otomatik senkronizasyon kullanarak internet bağlantısı olmadan mesajlaşma sağlar.

**Teknoloji Stack:**
- Kotlin 2.1.0
- Jetpack Compose
- MVVM + Clean Architecture
- BLE GATT API
- Google Nearby Connections
- Signal Protocol (libsignal-client - Rust-based JNI)
- Room Database
- Hilt Dependency Injection

**Kritik Parametreler:**
- BLE Packet HEADER_SIZE: 121 bytes
- MAX_PAYLOAD_SIZE: 391 bytes
- MTU_SIZE: 512 bytes
- INITIAL_DELAY (retry): 5000ms
- Fragment timeout: 30 seconds
- Deduplication window: 60 seconds
- Priority: 0 = text (high), 1 = media (low)

## Tasks

- [x] 1. Phase 1: Core Infrastructure Setup
  - [x] 1.1 Create database entities and DAOs
    - Create `BleNodeEntity` with indices for lastSeen and isConnected
    - Create `MessageIdCacheEntity` with index for receivedAt
    - Create `BleNodeDao` with queries for node management
    - Create `MessageIdCacheDao` with queries for deduplication
    - _Requirements: 13.1, 13.7_
  
  - [x] 1.2 Implement Room database migration (MIGRATION_7_8)
    - Add migration from version 7 to 8
    - Create ble_nodes table with all columns
    - Create message_id_cache table
    - Add indices for performance optimization
    - Update LinkerDatabase to include new DAOs
    - _Requirements: 18.7_
  
  - [x] 1.3 Implement ConnectivityMonitor
    - Create ConnectivityMonitorImpl with NetworkCallback
    - Implement network state detection (Online/Offline/Limited)
    - Add internet validation using NET_CAPABILITY_VALIDATED
    - Implement metered connection detection
    - Expose StateFlow for connectivity state changes
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6_
  
  - [ ]* 1.4 Write property test for ConnectivityMonitor
    - **Property 13: Connectivity State to Delivery Method Mapping**
    - **Validates: Requirements 2.4**
    - Test that Online state maps to ONLINE delivery method
    - Test that Offline/Limited states map to BLE delivery method
  
  - [x] 1.5 Implement PermissionManager
    - Create PermissionManager with API level checks
    - Implement BLE permission handling (API 31+ vs legacy)
    - Implement Nearby Connections permission handling (API 33+)
    - Add permission rationale and settings navigation
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6, 10.7, 10.8_
  
  - [x] 1.6 Create Hilt DI modules
    - Create ConnectivityModule with all offline messaging providers
    - Create EncryptionModule with Signal Protocol providers
    - Update DatabaseModule to include new DAOs
    - _Requirements: 18.1_

- [ ] 2. Checkpoint - Core infrastructure complete
  - Ensure all tests pass, ask the user if questions arise.

- [x] 3. Phase 2: BLE Mesh Networking Core
  - [x] 3.1 Implement BLEPacket data structure
    - Create BLEPacket data class with all fields (version, messageId, senderId, recipientId, ttl, hopCount, fragmentIndex, totalFragments, payloadLength, encryptedPayload, checksum)
    - Implement serialize() method with ByteBuffer
    - Implement deserialize() method with proper parsing
    - Implement calculateChecksum() using CRC32
    - Set HEADER_SIZE = 121 bytes, MAX_PAYLOAD_SIZE = 391 bytes
    - _Requirements: 16.1, 16.2_
  
  - [ ]* 3.2 Write property test for BLEPacket serialization
    - **Property 9: Packet Serialization Round-Trip**
    - **Validates: Requirements 16.1**
    - Test that serialize then deserialize preserves all fields
  
  - [ ]* 3.3 Write property test for packet checksum validation
    - **Property 10: Checksum Validation**
    - **Validates: Requirements 16.6**
    - Test that valid checksums are accepted
    - Test that invalid checksums are rejected
  
  - [x] 3.4 Implement PacketFragmenter
    - Create fragment() method to split large payloads
    - Create reassemble() method to combine fragments
    - Handle fragmentIndex and totalFragments correctly
    - _Requirements: 16.3, 16.4, 16.5_
  
  - [ ]* 3.5 Write property test for packet fragmentation
    - **Property 8: Packet Fragmentation and Reassembly Round-Trip**
    - **Validates: Requirements 16.3, 16.4, 16.5**
    - Test that fragment then reassemble produces original packet
  
  - [x] 3.6 Implement FragmentManager
    - Create FragmentManager with ConcurrentHashMap for fragment storage
    - Implement addFragment() with completion detection
    - Implement cleanupStaleFragments() with 30-second timeout
    - _Requirements: 16.5_
  
  - [x] 3.7 Implement BLERoutingTable
    - Create RouteInfo data class
    - Implement addRoute() with route quality comparison
    - Implement getRoute() with 60-second staleness check
    - Implement removeStaleRoutes()
    - Implement calculateRouteQuality() using RSSI and hop count
    - _Requirements: 4.9, 4.10_
  
  - [x] 3.8 Implement MessageIdCache for deduplication
    - Create MessageIdCache with LruCache
    - Implement contains() and add() methods
    - Implement cleanup() for old entries
    - Use MessageIdCacheDao for persistence
    - _Requirements: 13.1, 13.2, 13.3, 13.4, 13.5, 13.6_
  
  - [ ]* 3.9 Write property test for message deduplication
    - **Property 6: Message Deduplication**
    - **Validates: Requirements 4.4**
    - Test that duplicate message IDs are rejected

- [x] 4. Phase 2 (continued): BLE GATT Implementation
  - [x] 4.1 Implement GattServerManager
    - Create BluetoothGattServer with Linker Mesh Service UUID
    - Implement onCharacteristicWriteRequest() callback
    - Handle incoming BLE packets
    - Send response to client
    - _Requirements: 1.4, 1.5_
  
  - [x] 4.2 Implement GattClientManager
    - Manage Map of BluetoothGatt connections
    - Implement connectToDevice() with 5-second timeout
    - Implement writeCharacteristic() for packet transmission
    - Implement onCharacteristicChanged() callback
    - Implement disconnect() with cleanup
    - Enforce 7 concurrent connection limit
    - _Requirements: 1.4, 12.3, 12.4_
  
  - [x] 4.3 Implement BLEMeshManager interface
    - Create BLEMeshManagerImpl with all dependencies
    - Implement initialize() with BLE adapter check
    - Implement startMeshNetwork() and stopMeshNetwork()
    - Implement startScanning() with ScanSettings
    - Implement startAdvertising() with AdvertiseSettings
    - Use SERVICE_UUID: 00001234-0000-1000-8000-00805f9b34fb
    - Use CHARACTERISTIC_UUID: 00001235-0000-1000-8000-00805f9b34fb
    - _Requirements: 1.1, 1.2, 1.3, 1.6, 1.7_
  
  - [x] 4.4 Implement BLE peer discovery and connection
    - Implement ScanCallback to discover mesh nodes
    - Verify protocol version compatibility
    - Implement connectToPeer() with GATT connection
    - Implement disconnectFromPeer()
    - Update routing table with discovered peers
    - _Requirements: 12.1, 12.2, 12.3, 12.5, 12.6, 12.7, 12.8_
  
  - [x] 4.5 Implement BLE message routing logic
    - Implement sendMessage() to transmit packets
    - Implement forwardMessage() with TTL decrement
    - Implement onMessageReceived() callback
    - Check message ID cache for duplicates
    - Route to local delivery or forward to next hop
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7_
  
  - [ ]* 4.6 Write property test for optimal route selection
    - **Property 4: Optimal Route Selection**
    - **Validates: Requirements 4.2**
    - Test that route with minimum hop count is selected
  
  - [ ]* 4.7 Write property test for TTL-based forwarding
    - **Property 7: TTL-Based Forwarding**
    - **Validates: Requirements 4.5**
    - Test that messages with TTL > 0 are forwarded with decremented TTL
    - Test that messages with TTL = 0 are dropped
  
  - [ ]* 4.8 Write unit tests for BLEMeshManager
    - Test BLE adapter null handling
    - Test TTL exhaustion
    - Test duplicate message rejection
    - Test connection limit enforcement

- [ ] 5. Checkpoint - BLE mesh networking complete
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. Phase 3: Signal Protocol Encryption
  - [x] 6.1 Add Signal Protocol dependency
    - Add org.signal:libsignal-client to build.gradle.kts (Rust-based JNI library)
    - Sync project dependencies
    - Verify JNI binaries are included for target architectures (arm64-v8a, armeabi-v7a, x86_64)
    - _Requirements: 6.1_
  
  - [x] 6.2 Implement SignalProtocolStore
    - Create SignalProtocolStoreImpl with Room DB backing
    - Implement IdentityKeyStore interface
    - Implement PreKeyStore interface
    - Implement SignedPreKeyStore interface
    - Implement SessionStore interface
    - _Requirements: 6.2, 6.8_
  
  - [x] 6.3 Implement AndroidKeystoreWrapper
    - Create wrapper for Android Keystore access
    - Implement generateKeyPair() for hardware-backed keys
    - Implement getPrivateKey() with secure retrieval
    - Implement encrypt() and decrypt() methods
    - _Requirements: 6.8_
  
  - [x] 6.4 Implement EncryptionManager interface
    - Create EncryptionManagerImpl with Signal Protocol
    - Implement initialize() to set up protocol store
    - Implement encryptMessage() using SessionCipher
    - Implement decryptMessage() using SessionCipher
    - Implement hasKeysFor() to check key availability
    - Implement rotateKeys() for 30-day rotation
    - Use EncryptedMessage data class with signalMessage: ByteArray
    - _Requirements: 6.1, 6.2, 6.3, 6.7_
  
  - [ ]* 6.5 Write property test for encryption round-trip
    - **Property 11: Encryption Round-Trip**
    - **Validates: Requirements 6.5**
    - Test that encrypt then decrypt yields original content
  
  - [ ]* 6.6 Write property test for forwarding preserves encryption
    - **Property 12: Forwarding Preserves Encryption**
    - **Validates: Requirements 6.4**
    - Test that forwarded packets have identical encrypted payload
  
  - [ ]* 6.7 Write unit tests for EncryptionManager
    - Test missing public key error handling
    - Test encryption/decryption success
    - Test key rotation logic

- [ ] 7. Phase 4: Message Queue and Sync
  - [x] 7.1 Implement MessageDeduplicationManager
    - Create ConcurrentHashMap for processed MessageEntity.messageId (not BLE packet ID)
    - Implement isDuplicate() with 60-second window for race condition handling
    - Implement markAsProcessed() to track processed messages
    - Implement cleanupOldEntries() to prevent memory leak
    - Add detailed documentation explaining difference from BLE packet deduplication
    - _Requirements: 18.8_
  
  - [x] 7.2 Implement MessageQueueProcessor interface
    - Create MessageQueueProcessorImpl with all dependencies
    - Implement enqueueMessage() to add to queue
    - Set priority: 0 for text, 1 for media
    - Set TTL to 5 hops
    - Set delivery method to BLE
    - Implement processQueue() to handle pending messages
    - Implement retryFailedMessages() with exponential backoff
    - Implement cancelMessage() and clearSentMessages()
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7_
  
  - [ ]* 7.3 Write property test for message queueing completeness
    - **Property 1: Message Queueing Completeness**
    - **Validates: Requirements 3.1, 3.2, 3.3**
    - Test that queued messages have PENDING status, BLE delivery method, and TTL=5
  
  - [ ]* 7.4 Write property test for priority assignment
    - **Property 2: Priority Assignment by Message Type**
    - **Validates: Requirements 3.4**
    - Test that text messages get priority 0
    - Test that media messages get priority 1
  
  - [ ]* 7.5 Write property test for message encryption before queueing
    - **Property 3: Message Encryption Before Queueing**
    - **Validates: Requirements 3.5**
    - Test that encrypted payload differs from plaintext
  
  - [x] 7.6 Implement RetryStrategy with exponential backoff
    - Set INITIAL_DELAY = 5000ms
    - Set BACKOFF_MULTIPLIER = 3.0
    - Set MAX_RETRIES = 3
    - Implement calculateDelay() for retry delays (5s, 15s, 45s)
    - Implement retryWithBackoff() suspend function
    - _Requirements: 14.1, 14.2, 14.3_
  
  - [x] 7.7 Implement SyncManager interface
    - Create SyncManagerImpl with Firestore integration
    - Implement syncPendingMessages() to sync queue to Firestore
    - Update delivery method from BLE to ONLINE after sync
    - Send messages in chronological order
    - Respect rate limit of 10 messages per second
    - Implement syncFailedMessages() with retry logic
    - Clean up SENT queue items older than 7 days
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7, 7.8_
  
  - [ ]* 7.8 Write property test for sync delivery method update
    - **Property 14: Sync Updates Delivery Method**
    - **Validates: Requirements 7.2**
    - Test that synced BLE messages update to ONLINE delivery method
  
  - [ ]* 7.9 Write property test for sync chronological ordering
    - **Property 15: Sync Chronological Ordering**
    - **Validates: Requirements 7.3**
    - Test that messages are synced in ascending createdAt order
  
  - [ ]* 7.10 Write unit tests for MessageQueueProcessor
    - Test queue size limit (1000 messages)
    - Test retry count increment
    - Test status updates

- [ ] 8. Checkpoint - Message queue and sync complete
  - Ensure all tests pass, ask the user if questions arise.

- [x] 9. Phase 5: Foreground Service
  - [x] 9.1 Implement OfflineMessagingService
    - Create foreground service with notification
    - Implement onCreate() with notification channel creation
    - Implement onStartCommand() with action handling
    - Implement startOfflineMessaging() to initialize BLE mesh
    - Implement stopOfflineMessaging() to clean up
    - Implement toggleScanning() action
    - Use NOTIFICATION_ID = 1001, CHANNEL_ID = "offline_messaging_channel"
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5, 11.6, 11.7, 11.8_
  
  - [x] 9.2 Implement adaptive scanning based on battery and screen state
    - Create AdaptiveScanningStrategy
    - Implement calculateOptimalScanSettings()
    - Use SCAN_MODE_LOW_POWER (60s) when battery < 15%
    - Use SCAN_MODE_BALANCED (30s) when screen off
    - Use SCAN_MODE_LOW_LATENCY (continuous) when screen on
    - Observe battery level and screen state changes
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6, 9.7_
  
  - [x] 9.3 Implement BLEConnectionPool
    - Create connection pool with maxConnections = 7
    - Implement addConnection() with priority-based eviction
    - Implement calculatePriority() using pending messages, RSSI, and recency
    - Implement removeConnection() with cleanup
    - _Requirements: 12.3, 12.4, 12.5_
  
  - [x] 9.4 Implement MessageBatcher
    - Create batcher with batchSize = 5, batchTimeout = 5000ms
    - Implement addMessage() with batch accumulation
    - Implement flushBatch() when size or timeout reached
    - _Requirements: 9.6_
  
  - [x] 9.5 Implement OfflineMessagingServiceManager
    - Create manager to start/stop service
    - Implement startService() with API level check
    - Implement stopService()
    - Implement isServiceRunning() using StateFlow (NOT getRunningServices - deprecated)
    - Use SharedPreferences or internal StateFlow to track service status
    - _Requirements: 11.5_
  
  - [x] 9.6 Implement BootCompletedReceiver
    - Create BroadcastReceiver for BOOT_COMPLETED
    - DO NOT use @AndroidEntryPoint (causes issues with broadcast receivers)
    - Use EntryPointAccessors pattern to get Hilt dependencies
    - Auto-start service if offline messaging enabled
    - Add receiver to AndroidManifest.xml
    - _Requirements: 11.7_
  
  - [x] 9.7 Update AndroidManifest.xml
    - Add BootCompletedReceiver with BOOT_COMPLETED intent filter
    - Add OfflineMessagingService with foregroundServiceType="connectedDevice"
    - Add required permissions (BLUETOOTH_SCAN, BLUETOOTH_CONNECT, BLUETOOTH_ADVERTISE, ACCESS_FINE_LOCATION, NEARBY_WIFI_DEVICES)
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5_

- [x] 10. Phase 6: Wi-Fi Direct (Nearby Connections)
  - [x] 10.1 Add Nearby Connections dependency
    - Add play-services-nearby to build.gradle.kts
    - Sync project dependencies
    - _Requirements: 5.1_
  
  - [x] 10.2 Implement NearbyConnectionsManager interface
    - Create NearbyConnectionsManagerImpl with ConnectionsClient
    - Implement startDiscovery() with STRATEGY_P2P_POINT_TO_POINT
    - Implement startAdvertising() with service ID
    - Implement connectToEndpoint() with 10-second timeout
    - Implement sendFile() with progress callback
    - Implement receiveFile() with progress tracking
    - Use SERVICE_ID: "com.linker.app.OFFLINE_MESSAGING"
    - _Requirements: 5.1, 5.2, 5.3, 5.7_
  
  - [x] 10.3 Implement EndpointDiscoveryCallback
    - Handle onEndpointFound() to add discovered endpoints
    - Handle onEndpointLost() to remove endpoints
    - _Requirements: 5.1_
  
  - [x] 10.4 Implement ConnectionLifecycleCallback
    - Handle onConnectionInitiated() to accept connections
    - Handle onConnectionResult() to track connection status
    - Handle onDisconnected() to clean up
    - _Requirements: 5.2_
  
  - [x] 10.5 Implement PayloadCallback
    - Handle onPayloadReceived() for incoming files
    - Handle onPayloadTransferUpdate() for progress tracking
    - Support pause and resume functionality
    - Implement 3-retry logic on transfer failure
    - Fall back to BLE if Wi-Fi Direct fails
    - _Requirements: 5.3, 5.4, 5.5, 5.6, 5.8, 5.9_
  
  - [ ]* 10.6 Write unit tests for NearbyConnectionsManager
    - Test discovery and advertising
    - Test connection establishment
    - Test file transfer with progress
    - Test fallback to BLE on failure

- [ ] 11. Checkpoint - Wi-Fi Direct complete
  - Ensure all tests pass, ask the user if questions arise.

- [x] 12. Phase 7: UI Integration
  - [x] 12.1 Create OfflineMessagingSettingsScreen
    - Create Compose screen with settings UI
    - Add toggle for enable/disable offline messaging
    - Add toggle for BLE mesh networking
    - Add toggle for Wi-Fi Direct media transfer
    - Display current mesh node count and connection status
    - Add option to clear offline message queue
    - Add option to set maximum TTL (1-10 hops)
    - Display battery usage statistics
    - Add option to enable/disable foreground service notification
    - _Requirements: 15.1, 15.2, 15.3, 15.4, 15.5, 15.6, 15.7, 15.8, 15.9_
  
  - [x] 12.2 Create OfflineMessagingSettingsViewModel
    - Create ViewModel with permission state management
    - Implement checkPermissions()
    - Implement requestPermissions() with rationale handling
    - Implement onPermissionResult() with permanent denial detection
    - Implement enableOfflineMessaging() and disableOfflineMessaging()
    - _Requirements: 10.6, 10.7, 10.8_
  
  - [x] 12.3 Create permission request dialogs
    - Create PermissionRationaleDialog with clear explanations
    - Create PermissionSettingsDialog for permanently denied permissions
    - Handle different permission types (Bluetooth, Location, Nearby)
    - _Requirements: 10.1, 10.4, 10.5, 10.6_
  
  - [x] 12.4 Update ChatScreen to show message delivery status
    - Add delivery method icons (cloud for ONLINE, Bluetooth for BLE, Wi-Fi for WIFI_DIRECT)
    - Update message status indicators
    - Add message info dialog showing delivery details and hop count
    - Show retry option for messages in SENDING state > 30 seconds
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7, 8.8_
  
  - [x] 12.5 Implement key exchange UX flow
    - Show dialog when recipient's encryption key not found
    - Provide options: "Send When Online", "Cancel" (NO "Send Unencrypted" for security)
    - Add pendingKeyExchange flag to MessageQueueEntity (requires new migration)
    - Implement background key exchange check via WorkManager
    - Notify user when secure connection established
    - _Requirements: 6.3_
  
  - [x] 12.6 Add Room migration for pendingKeyExchange field
    - Create MIGRATION_8_9 to add pendingKeyExchange column to MessageQueueEntity
    - Set default value to false for existing rows
    - Update LinkerDatabase version to 9
    - Test migration with existing data
    - _Requirements: 6.3_
  
  - [x] 12.7 Update MessageRepositoryImpl to integrate offline messaging
    - Add ConnectivityMonitor dependency
    - Add MessageQueueProcessor dependency
    - Update sendMessage() to check connectivity and queue if offline
    - Implement automatic delivery method selection
    - Handle race conditions with MessageDeduplicationManager
    - Use MessageEntity.messageId for deduplication (not BLE packet ID)
    - _Requirements: 18.1, 18.2, 18.3, 18.4, 18.5, 18.6, 18.8_
  
  - [ ]* 12.7 Write UI tests for ChatScreen
    - Test BLE message shows Bluetooth icon
    - Test online message shows cloud icon
    - Test message info dialog displays correctly
  
  - [ ]* 12.8 Write UI tests for OfflineMessagingSettingsScreen
    - Test toggle offline messaging on/off
    - Test permission request flow
    - Test settings dialog interactions

- [x] 13. Phase 8: Error Handling
  - [x] 13.1 Implement BLE error handling
    - Create BLEError sealed class hierarchy
    - Create BLEErrorHandler with retry logic
    - Handle connection errors with 3 retries
    - Handle transmission errors with exponential backoff
    - Handle routing errors with online sync fallback
    - _Requirements: 14.4, 14.5, 14.6, 14.7, 14.8_
  
  - [x] 13.2 Implement encryption error handling
    - Create EncryptionError sealed class hierarchy
    - Create EncryptionErrorHandler
    - Handle missing public key with user notification
    - Handle decryption failures with key re-exchange request
    - Mark encryption failures as FAILED immediately (no retry)
    - _Requirements: 6.3, 14.7_
  
  - [x] 13.3 Implement network error handling
    - Create NetworkError sealed class hierarchy
    - Create NetworkErrorHandler
    - Handle network unavailable with offline mode switch
    - Handle Firestore errors with retry logic
    - Handle sync errors with exponential backoff
    - _Requirements: 7.6, 7.7, 14.1, 14.2_
  
  - [x] 13.4 Implement ErrorLogger
    - Create ErrorLogger with Firebase Analytics integration
    - Implement logError() with severity levels (CRITICAL, ERROR, WARNING)
    - Log to Firebase Crashlytics for critical errors
    - Log locally for warnings
    - _Requirements: 17.6_
  
  - [ ]* 13.5 Write unit tests for error handlers
    - Test BLE error retry logic
    - Test encryption error handling
    - Test network error fallback

- [x] 14. Phase 9: Analytics and Testing
  - [x] 14.1 Implement analytics events
    - Create OfflineMessagingEvent sealed class
    - Implement AnalyticsLogger with Firebase Analytics
    - Log BLE connection events (established, failed)
    - Log message routing events (sent, forwarded, delivered, failed)
    - Log sync events (started, completed)
    - Log error events with context
    - _Requirements: 17.1, 17.2, 17.3, 17.4, 17.5, 17.6, 17.7, 17.8_
  
  - [x] 14.2 Implement performance metrics
    - Create PerformanceMetrics class
    - Track BLE discovery time, connection time, transmission time
    - Track encryption/decryption time
    - Track sync completion time
    - Generate metric reports with percentiles (p50, p95, p99)
    - _Requirements: 17.3, 17.4_
  
  - [ ]* 14.3 Write integration tests
    - Test end-to-end offline message delivery
    - Test Wi-Fi Direct media transfer
    - Test online-offline transition with automatic sync
    - Test multi-hop routing (3 devices)
  
  - [ ]* 14.4 Write performance tests
    - Test encryption completes within 100ms
    - Test sync handles 100 messages within 30 seconds
    - Test BLE peer discovery within 10 seconds (separate from GATT connection)
    - Test GATT connection establishment within 5 seconds (Requirement 1.4)

- [ ] 15. Final checkpoint - Complete feature verification
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 16. Phase 10: Bug Fixes and Improvements (P0 - Critical)
  - [x] 16.1 Fix encryption missing in BLE message sending (Issue #2 - CRITICAL SECURITY)
    - **File**: MessageQueueProcessorImpl.kt (Line 186-200)
    - **Problem**: BLE messages sent without encryption - `encryptedPayload = message.messagePayload.toByteArray()`
    - **Impact**: All offline messages transmitted as plaintext
    - **Solution**: Call `encryptionManager.encryptMessage()` before creating BLEPacket
    - Add proper error handling for encryption failures
    - _Analysis Report: Issue #2, Priority P0_
  
  - [x] 16.2 Fix encryption missing in test packet sending (Issue #1)
    - **File**: BLEMeshManagerImpl.kt (Line 387-398)
    - **Problem**: Test packets sent without encryption - `val encryptedTestPayload = testPayload`
    - **Impact**: Security vulnerability - test packets transmitted as plaintext
    - **Solution**: Use `encryptionManager.encryptMessage()` for test packets
    - _Analysis Report: Issue #1, Priority P0_
  
  - [x] 16.3 Fix decryption missing in packet reception (Issue #3)
    - **File**: BLEMeshManagerImpl.kt (Line 619-665)
    - **Problem**: Received packets not decrypted - `messageReceivedCallback?.invoke(completePayload)`
    - **Impact**: Encrypted messages delivered to app without decryption
    - **Solution**: Call `encryptionManager.decryptMessage()` before invoking callback
    - Add error handling for decryption failures
    - _Analysis Report: Issue #3, Priority P0_
  
  - [x] 16.4 Implement Wi-Fi Direct sending logic (Issue #5)
    - **File**: MessageQueueProcessorImpl.kt (Line 210-230)
    - **Problem**: Wi-Fi Direct completely unimplemented - returns failure exception
    - **Impact**: Large media files cannot be sent
    - **Solution**: Integrate NearbyConnectionsManager for file transfer
    - Implement proper error handling and fallback to BLE
    - Add progress tracking
    - _Analysis Report: Issue #5, Priority P0_
  
  - [x] 16.5 Fix connection timeout spec violation (Issue #8)
    - **File**: GattClientManager.kt (Line 32)
    - **Problem**: `CONNECTION_TIMEOUT = 20000L` (20 seconds) - spec requires 5 seconds
    - **Impact**: Poor user experience, slow connection attempts
    - **Solution**: Change to `CONNECTION_TIMEOUT = 5000L` per Requirement 1.4
    - _Analysis Report: Issue #8, Priority P0_

- [ ] 17. Phase 11: Bug Fixes and Improvements (P1 - High Priority)
  - [x] 17.1 Fix RSSI missing for incoming connections (Issue #4)
    - **File**: BLEMeshManagerImpl.kt (Line 577-582)
    - **Problem**: Default RSSI (-60) used instead of actual value
    - **Impact**: Routing quality calculated incorrectly
    - **Solution**: Get actual RSSI from GattClientManager or GattServerManager
    - Update RouteInfo with real RSSI values
    - _Analysis Report: Issue #4, Priority P1_
  
  - [ ] 17.2 Fix circular dependency risk (Issue #6)
    - **File**: BLEMeshManagerImpl.kt
    - **Problem**: `currentUserProvider` injected directly - circular dependency risk
    - **Impact**: Application may fail to start
    - **Solution**: Use lazy injection or Provider<CurrentUserProvider> pattern
    - _Analysis Report: Issue #6, Priority P1_
  
  - [x] 17.3 Fix memory leak risk in FragmentManager (Issue #7)
    - **File**: FragmentManager.kt (Line 30-35)
    - **Problem**: Manual CoroutineScope management - `CoroutineScope(Dispatchers.Default + SupervisorJob())`
    - **Impact**: Memory leak if scope not cancelled
    - **Solution**: Ensure `shutdown()` called in all cleanup paths
    - Add lifecycle observer or inject scope
    - _Analysis Report: Issue #7, Priority P1_
  
  - [x] 17.4 Fix scan cooldown too long (Issue #9)
    - **File**: BLEMeshManagerImpl.kt (Line 62)
    - **Problem**: `SCAN_PROCESS_COOLDOWN = 5000L` (5 seconds) too long
    - **Impact**: Slow device discovery, delayed mesh network formation
    - **Solution**: Reduce to 2000-3000L (2-3 seconds)
    - _Analysis Report: Issue #9, Priority P1_
  
  - [x] 17.5 Fix duplicate scan processing (Issue #10)
    - **File**: BLEMeshManagerImpl.kt (Line 519-527)
    - **Problem**: Scan cooldown insufficient - same device processed multiple times
    - **Impact**: Redundant connection attempts
    - **Solution**: Combine cooldown with connection attempt tracking
    - _Analysis Report: Issue #10, Priority P1_
  
  - [x] 17.6 Fix race condition in routing table update (Issue #25)
    - **File**: BLEMeshManagerImpl.kt (Line 569-595)
    - **Problem**: `userIdToMacAddress` map update and `routingTable.addRoute()` not atomic
    - **Impact**: Race condition in concurrent access
    - **Solution**: Add Mutex protection for both operations
    - _Analysis Report: Issue #25, Priority P1_
  
  - [x] 17.7 Fix race condition in fragment timeout (Issue #26)
    - **File**: FragmentManager.kt (Line 60-75)
    - **Problem**: Fragment addition and timeout job not synchronized
    - **Impact**: Race condition possible
    - **Solution**: Use synchronized block or Mutex
    - _Analysis Report: Issue #26, Priority P1_
  
  - [x] 17.8 Fix race condition in connection pool (Issue #27)
    - **File**: GattClientManager.kt (Line 73-78)
    - **Problem**: Connection limit check not atomic - `if (connections.size >= MAX_CONNECTIONS && connectionPool.isFull())`
    - **Impact**: Connection pool overflow possible
    - **Solution**: Add Mutex protection for connection limit check
    - _Analysis Report: Issue #27, Priority P1_
  
  - [x] 17.9 Fix message ID collision risk (Issue #28)
    - **File**: BLEPacket.kt
    - **Problem**: Timestamp used for message ID - `messageId = "test-${System.currentTimeMillis()}"`
    - **Impact**: Collision if two messages sent in same millisecond
    - **Solution**: Use UUID.randomUUID() for message IDs
    - _Analysis Report: Issue #28, Priority P1_
  
  - [x] 17.10 Fix TTL decrement logic (Issue #29)
    - **File**: BLEMeshManagerImpl.kt (Line 447-456)
    - **Problem**: TTL decremented before checking if already 0
    - **Impact**: Logic error in TTL handling
    - **Solution**: Check `if (packet.ttl > 0)` before decrementing
    - _Analysis Report: Issue #29, Priority P1_

- [ ] 18. Phase 12: Bug Fixes and Improvements (P2 - Medium Priority)
  - [x] 18.1 Implement message type detection (Issue #11)
    - **File**: MessageQueueProcessorImpl.kt (Line 73-75)
    - **Problem**: Hardcoded priority 0 for all messages - `val priority = MessagePriority.TEXT`
    - **Impact**: Media messages not prioritized correctly
    - **Solution**: Get messageType from MessageEntity and map to priority
    - _Analysis Report: Issue #11, Priority P2_
  
  - [x] 18.2 Add logging to SyncManager (Issue #12)
    - **File**: SyncManager.kt (Line 186)
    - **Problem**: Missing logging - `// TODO: Add proper logging`
    - **Impact**: Difficult to debug sync issues
    - **Solution**: Integrate ErrorLogger or Analytics
    - _Analysis Report: Issue #12, Priority P2_
  
  - [x] 18.3 Add message type mapping in SyncManager (Issue #13)
    - **File**: SyncManager.kt (Line 56, 127)
    - **Problem**: Hardcoded TEXT type - `messageType = com.linker.app.domain.model.MessageType.TEXT`
    - **Impact**: Message type lost during sync
    - **Solution**: Add messageType field to MessageQueueEntity (requires migration)
    - _Analysis Report: Issue #13, Priority P2_
  
  - [x] 18.4 Store Kyber pre-key (Issue #14)
    - **File**: EncryptionManagerImpl.kt (Line 227-229)
    - **Problem**: Kyber pre-key generated but not stored
    - **Impact**: Key not persisted for future use
    - **Solution**: Store Kyber pre-key in SignalProtocolStore
    - _Analysis Report: Issue #14, Priority P2_
  
  - [x] 18.5 Refactor connection state handling (Issue #21)
    - **File**: GattClientManager.kt
    - **Problem**: Duplicate code in `onConnectionStateChange` callback
    - **Impact**: Code duplication
    - **Solution**: Create helper function for continuation handling
    - _Analysis Report: Issue #21, Priority P2_
  
  - [x] 18.6 Create error handling extension function (Issue #22)
    - **File**: Multiple files
    - **Problem**: Try-catch blocks repeated everywhere
    - **Impact**: Code duplication
    - **Solution**: Create extension function or inline function for error handling
    - _Analysis Report: Issue #22, Priority P2_
  
  - [x] 18.7 Create status update transaction wrapper (Issue #23)
    - **File**: MessageQueueProcessorImpl.kt
    - **Problem**: Queue status update code duplicated
    - **Impact**: Code duplication
    - **Solution**: Create transaction wrapper function
    - _Analysis Report: Issue #23, Priority P2_
  
  - [x] 18.8 Create TimeProvider interface (Issue #24)
    - **File**: Multiple files
    - **Problem**: `System.currentTimeMillis()` repeated everywhere
    - **Impact**: Not testable, code duplication
    - **Solution**: Create TimeProvider interface for dependency injection
    - _Analysis Report: Issue #24, Priority P2_
  
  - [x] 18.9 Optimize route lookup performance (Issue #30)
    - **File**: BLERoutingTable.kt
    - **Problem**: Database query on every route lookup
    - **Impact**: Performance degradation
    - **Solution**: Implement batch query or cache warming strategy
    - _Analysis Report: Issue #30, Priority P2_
  
  - [x] 18.10 Add dispatcher check for encryption (Issue #31)
    - **File**: EncryptionManagerImpl.kt
    - **Problem**: Encryption may run on main thread
    - **Impact**: UI blocking
    - **Solution**: Add coroutine dispatcher control
    - _Analysis Report: Issue #31, Priority P2_
  
  - [x] 18.11 Implement connection pool priority eviction (Issue #32)
    - **File**: GattClientManager.kt
    - **Problem**: Connection pool exists but no priority-based eviction
    - **Impact**: Suboptimal connection management
    - **Solution**: Fully implement BLEConnectionPool with priority
    - _Analysis Report: Issue #32, Priority P2_
  
  - [x] 18.12 Add ByteBuffer object pooling (Issue #33)
    - **File**: BLEPacket.kt (Line 60-80)
    - **Problem**: ByteBuffer allocated on every serialization
    - **Impact**: Memory allocation overhead
    - **Solution**: Use object pool for ByteBuffer
    - _Analysis Report: Issue #33, Priority P2_
  
  - [x] 18.13 Implement batch database operations (Issue #34)
    - **File**: MessageQueueProcessorImpl.kt
    - **Problem**: Separate transaction for each message
    - **Impact**: Performance degradation
    - **Solution**: Use batch insert/update operations
    - _Analysis Report: Issue #34, Priority P2_

- [ ] 19. Phase 13: Bug Fixes and Improvements (P3 - Low Priority)
  - [x] 19.1 Optimize route lookup cache (Issue #15)
    - **File**: BLERoutingTable.kt (Line 88-115)
    - **Problem**: Database query on every lookup
    - **Impact**: Inefficient
    - **Solution**: Increase cache hit rate, optimize LRU cache size
    - _Analysis Report: Issue #15, Priority P3_
  
  - [x] 19.2 Add BuildConfig.DEBUG check for logging (Issue #16)
    - **File**: All files
    - **Problem**: Log.d() called in production
    - **Impact**: Performance loss
    - **Solution**: Add BuildConfig.DEBUG check or use Timber
    - _Analysis Report: Issue #16, Priority P3_
  
  - [x] 19.3 Create configuration class for hardcoded values (Issue #17)
    - **File**: Multiple files
    - **Problem**: Hardcoded values (MAX_CONNECTIONS=7, DEFAULT_TTL=5, etc.)
    - **Impact**: Not configurable at runtime
    - **Solution**: Create Configuration class
    - _Analysis Report: Issue #17, Priority P3_
  
  - [x] 19.4 Implement performance metrics collection (Issue #18)
    - **File**: All files
    - **Problem**: No metrics collected
    - **Impact**: Cannot measure performance
    - **Solution**: Track BLE discovery time, connection time, transmission time, etc.
    - _Analysis Report: Issue #18, Priority P3_
  
  - [x] 19.5 Add GATT error retry logic (Issue #19)
    - **File**: GattClientManager.kt
    - **Problem**: No retry for GATT errors
    - **Impact**: Transient failures not recovered
    - **Solution**: Add automatic retry on GATT_FAILURE
    - _Analysis Report: Issue #19, Priority P3_
  
  - [x] 19.6 Implement adaptive batching (Issue #20)
    - **File**: MessageBatcher.kt
    - **Problem**: Fixed batch size and timeout
    - **Impact**: Not optimal for all network conditions
    - **Solution**: Adjust batch parameters based on network state
    - _Analysis Report: Issue #20, Priority P3_
  
  - [x] 19.7 Implement automatic key rotation (Issue #38)
    - **File**: EncryptionManagerImpl.kt
    - **Problem**: `rotateKeys()` exists but not called automatically
    - **Impact**: Keys not rotated per security best practices
    - **Solution**: Use WorkManager for 30-day rotation schedule
    - _Analysis Report: Issue #38, Priority P3_
  
  - [x] 19.8 Implement key backup mechanism (Issue #39)
    - **File**: SignalProtocolStoreImpl.kt
    - **Problem**: No identity key backup
    - **Impact**: Messages unreadable if device lost
    - **Solution**: Add secure backup mechanism
    - _Analysis Report: Issue #39, Priority P3_
  
  - [x] 19.9 Add certificate pinning (Issue #40)
    - **File**: Network operations
    - **Problem**: No certificate pinning for Firestore
    - **Impact**: MITM attack risk
    - **Solution**: Add OkHttp interceptor with certificate pinning
    - _Analysis Report: Issue #40, Priority P3_
  
  - [x] 19.10 Disable sensitive data logging in production (Issue #41)
    - **File**: All files
    - **Problem**: Message contents may be logged
    - **Impact**: Privacy risk
    - **Solution**: Disable sensitive logging in production builds
    - _Analysis Report: Issue #41, Priority P3_
  
  - [x] 19.11 Add message sending progress indication (Issue #42)
    - **File**: MessageQueueProcessorImpl.kt
    - **Problem**: No progress shown during sending
    - **Impact**: Poor UX
    - **Solution**: Emit progress via Flow
    - _Analysis Report: Issue #42, Priority P3_
  
  - [x] 19.12 Add retry UI for failed messages (Issue #43)
    - **File**: MessageQueueProcessorImpl.kt
    - **Problem**: No user retry option for failed messages
    - **Impact**: Poor UX
    - **Solution**: Add retry button in UI
    - _Analysis Report: Issue #43, Priority P3_
  
  - [x] 19.13 Add mesh network status display (Issue #44)
    - **File**: BLEMeshManagerImpl.kt
    - **Problem**: Network status not shown to user
    - **Impact**: Poor UX
    - **Solution**: Show peer count and connection quality in notification
    - _Analysis Report: Issue #44, Priority P3_
  
  - [x] 19.14 Add battery warning (Issue #45)
    - **File**: AdaptiveScanningStrategy.kt
    - **Problem**: No warning when battery low
    - **Impact**: Poor UX
    - **Solution**: Show warning below 15% battery
    - _Analysis Report: Issue #45, Priority P3_
  
  - [x] 19.15 Decouple MessageQueueProcessor from BLEMeshManager (Issue #46)
    - **File**: MessageQueueProcessorImpl.kt
    - **Problem**: Tight coupling to BLEMeshManager
    - **Impact**: Poor architecture
    - **Solution**: Create DeliveryStrategy interface
    - _Analysis Report: Issue #46, Priority P3_
  
  - [x] 19.16 Inject CoroutineScope in FragmentManager (Issue #47)
    - **File**: FragmentManager.kt
    - **Problem**: Hardcoded CoroutineScope
    - **Impact**: Not testable
    - **Solution**: Inject scope via constructor
    - _Analysis Report: Issue #47, Priority P3_
  
  - [x] 19.17 Create BleNodeRepository (Issue #48)
    - **File**: BLEMeshManagerImpl.kt
    - **Problem**: Direct DAO access
    - **Impact**: Poor architecture
    - **Solution**: Create repository layer
    - _Analysis Report: Issue #48, Priority P3_
  
  - [x] 19.18 Create SendOfflineMessageUseCase (Issue #49)
    - **File**: MessageQueueProcessorImpl.kt
    - **Problem**: Business logic in data layer
    - **Impact**: Poor architecture
    - **Solution**: Create use case layer
    - _Analysis Report: Issue #49, Priority P3_
  
  - [x] 19.19 Implement event bus for component communication (Issue #50)
    - **File**: Multiple files
    - **Problem**: Callback-based communication
    - **Impact**: Tight coupling
    - **Solution**: Use EventBus or SharedFlow
    - _Analysis Report: Issue #50, Priority P3_
  
  - [x] 19.20 Add KDoc for public APIs (Issue #51)
    - **File**: All public interfaces
    - **Problem**: Missing KDoc
    - **Impact**: Poor documentation
    - **Solution**: Add KDoc to all public APIs
    - _Analysis Report: Issue #51, Priority P3_
  
  - [x] 19.21 Create architecture documentation (Issue #52)
    - **File**: Documentation
    - **Problem**: No component interaction diagram
    - **Impact**: Hard to understand system
    - **Solution**: Update Mermaid diagrams
    - _Analysis Report: Issue #52, Priority P3_
  
  - [x] 19.22 Document error codes (Issue #53)
    - **File**: Documentation
    - **Problem**: Error codes not documented
    - **Impact**: Hard to debug
    - **Solution**: Create error code enum with documentation
    - _Analysis Report: Issue #53, Priority P3_
  
  - [x] 19.23 Implement conflict resolution in SyncManager (Issue #54)
    - **File**: SyncManager.kt
    - **Problem**: No conflict resolution for duplicate messages
    - **Impact**: Same message may be processed twice
    - **Solution**: Integrate MessageDeduplicationManager
    - _Analysis Report: Issue #54, Priority P3_
  
  - [x] 19.24 Make sync operations idempotent (Issue #55)
    - **File**: SyncManager.kt
    - **Problem**: Sync not idempotent
    - **Impact**: Same message may be sent multiple times
    - **Solution**: Add message ID check
    - _Analysis Report: Issue #55, Priority P3_
  
  - [x] 19.25 Add transaction support for queue updates (Issue #56)
    - **File**: MessageQueueProcessorImpl.kt
    - **Problem**: Queue and message updates not atomic
    - **Impact**: Data inconsistency possible
    - **Solution**: Use Room transaction
    - _Analysis Report: Issue #56, Priority P3_

- [x] 20. Checkpoint - All bug fixes complete
  - Verify all critical and high priority issues resolved
  - Run full test suite
  - Perform security audit
  - Ask user if questions arise

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation at major milestones
- Property tests validate universal correctness properties from design document
- Unit tests validate specific examples and edge cases
- Integration tests validate end-to-end behavior
- Implementation follows Clean Architecture with clear separation of concerns
- All BLE operations require proper permission checks before execution
- Encryption is mandatory for all offline messages (except user explicitly chooses unencrypted)
- Battery optimization is critical - adaptive scanning based on battery level and screen state
- Service status must use StateFlow, not deprecated getRunningServices()
- Priority semantics: 0 = high priority (text), 1 = low priority (media)
- Fragment timeout is 30 seconds to prevent memory leaks
- Deduplication window is 60 seconds to handle race conditions
- Retry delays follow exponential backoff: 5s, 15s, 45s

## Implementation Order Rationale

1. **Phase 1**: Core infrastructure provides foundation (database, connectivity, permissions)
2. **Phase 2**: BLE mesh is the core feature - packet structure, routing, GATT communication
3. **Phase 3**: Encryption must be in place before any real message transmission
4. **Phase 4**: Queue and sync enable offline-to-online transition
5. **Phase 5**: Foreground service enables background operation
6. **Phase 6**: Wi-Fi Direct adds fast media transfer capability
7. **Phase 7**: UI integration makes feature accessible to users
8. **Phase 8**: Error handling ensures robustness
9. **Phase 9**: Analytics and testing validate correctness and performance

## Testing Strategy

- **Property-based tests**: Validate universal properties across all inputs (minimum 100 iterations)
- **Unit tests**: Validate specific examples, edge cases, and error conditions
- **Integration tests**: Validate end-to-end behavior with real components
- **UI tests**: Validate user interface behavior and interactions
- **Performance tests**: Validate timing requirements and resource usage

## Critical Implementation Notes

1. **BLE Packet Structure**: HEADER_SIZE must be exactly 121 bytes (36+36+36 for UUIDs)
2. **Signal Protocol**: Use libsignal-client (Rust-based), not deprecated libsignal-android
3. **Signal Protocol Data**: Use signalMessage: ByteArray in EncryptedMessage (not separate fields)
4. **Retry Strategy**: INITIAL_DELAY = 5000ms (not 1000ms or other values)
5. **Fragment Timeout**: 30 seconds for incomplete fragments
6. **Deduplication**: Two separate systems:
   - **BLE Packet Deduplication**: MessageIdCache prevents same packet from multiple routes
   - **Message Deduplication**: MessageDeduplicationManager prevents same message from BLE+Online
7. **Priority Semantics**: 0 = high (text), 1 = low (media) - follows Unix nice convention
8. **Service Status**: Use StateFlow or SharedPreferences, NOT getRunningServices() (deprecated)
9. **Broadcast Receiver**: Use EntryPointAccessors pattern, NOT @AndroidEntryPoint for BootCompletedReceiver
10. **Key Exchange UX**: Show "Send When Online", "Cancel" - NO "Send Unencrypted" (security risk)
11. **Room Migration**: pendingKeyExchange field requires MIGRATION_8_9
12. **Performance Metrics**: BLE discovery ≠ GATT connection (separate timing requirements)
