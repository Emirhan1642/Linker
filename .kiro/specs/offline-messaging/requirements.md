# Requirements Document

## Introduction

Linker Android uygulaması için internetsiz mesajlaşma (offline messaging) özelliği, kullanıcıların internet bağlantısı olmadan bile mesaj gönderebilmelerini ve alabilmelerini sağlar. Bu özellik, BLE (Bluetooth Low Energy) mesh networking ve Wi-Fi Direct teknolojilerini kullanarak mesajların cihazlar arasında hop ederek hedefe ulaşmasını sağlar. Sistem, online ve offline modlar arasında sorunsuz geçiş yaparak kullanıcı deneyimini kesintisiz tutar.

## Glossary

- **BLE_Mesh_Manager**: Bluetooth Low Energy mesh ağını yöneten ve mesajların cihazlar arasında yönlendirilmesini sağlayan sistem bileşeni
- **Nearby_Connections_Manager**: Google Nearby Connections API kullanarak Wi-Fi Direct üzerinden büyük medya dosyalarının transferini yöneten bileşen
- **Connectivity_Monitor**: Cihazın internet bağlantı durumunu izleyen ve online/offline geçişlerini tespit eden bileşen
- **Message_Queue_Processor**: Offline mesaj kuyruğunu işleyen ve uygun delivery method ile gönderimi yöneten bileşen
- **Encryption_Manager**: Signal Protocol kullanarak mesajların uçtan uca şifrelenmesini sağlayan güvenlik bileşeni
- **Hop**: BLE mesh ağında bir mesajın bir cihazdan diğerine aktarılması işlemi
- **TTL (Time-To-Live)**: Bir mesajın mesh ağında maksimum kaç hop yapabileceğini belirten değer
- **Delivery_Method**: Mesajın gönderim yöntemi (ONLINE, BLE, WIFI_DIRECT)
- **Message_Status**: Mesajın durumu (SENDING, SENT, DELIVERED, READ, FAILED)
- **Foreground_Service**: Android'de arka planda BLE tarama yapabilmek için gerekli olan servis türü
- **Peer_Discovery**: BLE veya Wi-Fi Direct ile yakındaki cihazları keşfetme süreci
- **Sync_Manager**: Online bağlantı geldiğinde offline mesajları senkronize eden bileşen

## Requirements

### Requirement 1: BLE Mesh Network Initialization

**User Story:** As a user, I want the app to automatically establish BLE mesh connections with nearby devices, so that I can send messages even without internet.

#### Acceptance Criteria

1. WHEN the app starts, THE BLE_Mesh_Manager SHALL initialize BLE adapter and check availability
2. WHEN BLE is available, THE BLE_Mesh_Manager SHALL start advertising the device as a mesh node
3. WHEN BLE is available, THE BLE_Mesh_Manager SHALL start scanning for nearby mesh nodes
4. WHEN a nearby mesh node is discovered, THE BLE_Mesh_Manager SHALL establish GATT connection within 5 seconds
5. WHEN GATT connection is established, THE BLE_Mesh_Manager SHALL exchange device identifiers and routing information
6. WHERE location permission is not granted, THE BLE_Mesh_Manager SHALL request runtime permission with explanation
7. IF location permission is denied, THEN THE BLE_Mesh_Manager SHALL disable BLE mesh features and notify user
8. WHILE the app is in background, THE Foreground_Service SHALL maintain BLE scanning with reduced frequency to conserve battery

### Requirement 2: Connectivity State Detection

**User Story:** As a user, I want the app to automatically detect my internet connection status, so that it can choose the best delivery method for my messages.

#### Acceptance Criteria

1. THE Connectivity_Monitor SHALL continuously monitor network connectivity state
2. WHEN internet connection becomes available, THE Connectivity_Monitor SHALL validate internet access by checking network capabilities
3. WHEN internet connection is lost, THE Connectivity_Monitor SHALL notify all dependent components within 2 seconds
4. WHEN connectivity state changes, THE Connectivity_Monitor SHALL update delivery method preference (ONLINE → BLE or BLE → ONLINE)
5. THE Connectivity_Monitor SHALL distinguish between connected-but-no-internet and truly-online states
6. WHILE on metered connection, THE Connectivity_Monitor SHALL flag the connection type for sync optimization

### Requirement 3: Offline Message Queueing

**User Story:** As a user, I want my messages to be queued when I'm offline, so that they can be delivered when connectivity is restored.

#### Acceptance Criteria

1. WHEN a user sends a message without internet, THE Message_Queue_Processor SHALL add the message to MessageQueueEntity with PENDING status
2. WHEN queueing a message, THE Message_Queue_Processor SHALL set delivery method to BLE
3. WHEN queueing a message, THE Message_Queue_Processor SHALL assign TTL value of 5 hops
4. WHEN queueing a message, THE Message_Queue_Processor SHALL set priority based on message type (text=0, media=1)
5. THE Message_Queue_Processor SHALL store encrypted message payload in the queue
6. WHEN queue size exceeds 1000 messages, THE Message_Queue_Processor SHALL remove oldest SENT messages first
7. WHILE a message is in queue, THE Message_Queue_Processor SHALL update its status as delivery progresses

### Requirement 4: BLE Mesh Message Routing

**User Story:** As a user, I want my messages to hop through nearby devices to reach the recipient, so that I can communicate over longer distances without internet.

#### Acceptance Criteria

1. WHEN a message is ready for BLE delivery, THE BLE_Mesh_Manager SHALL identify available mesh routes to recipient
2. WHEN multiple routes exist, THE BLE_Mesh_Manager SHALL select the route with fewest hops
3. WHEN transmitting a message, THE BLE_Mesh_Manager SHALL include sender ID, recipient ID, message ID, TTL, and hop count in the packet
4. WHEN a mesh node receives a message, THE BLE_Mesh_Manager SHALL verify the message is not a duplicate using message ID cache
5. WHEN a mesh node receives a message for another recipient, THE BLE_Mesh_Manager SHALL decrement TTL and forward if TTL > 0
6. WHEN TTL reaches 0, THE BLE_Mesh_Manager SHALL drop the message and log routing failure
7. WHEN a mesh node receives a message for itself, THE BLE_Mesh_Manager SHALL decrypt and deliver to local message store
8. WHEN message delivery fails after 3 retry attempts, THE BLE_Mesh_Manager SHALL mark message as FAILED in queue
9. THE BLE_Mesh_Manager SHALL maintain a routing table of known mesh nodes and their last seen timestamps
10. WHEN a mesh node hasn't been seen for 60 seconds, THE BLE_Mesh_Manager SHALL remove it from routing table

### Requirement 5: Wi-Fi Direct Media Transfer

**User Story:** As a user, I want to send photos and videos quickly to nearby contacts without using mobile data, so that I can share media efficiently offline.

#### Acceptance Criteria

1. WHEN a user sends media larger than 5MB without internet, THE Nearby_Connections_Manager SHALL initiate Wi-Fi Direct discovery
2. WHEN recipient device is discovered via Nearby Connections, THE Nearby_Connections_Manager SHALL establish P2P connection within 10 seconds
3. WHEN P2P connection is established, THE Nearby_Connections_Manager SHALL transfer media file using Payload API
4. WHEN transfer starts, THE Nearby_Connections_Manager SHALL show progress indicator to user
5. WHEN transfer completes successfully, THE Nearby_Connections_Manager SHALL update message status to DELIVERED
6. IF transfer fails, THEN THE Nearby_Connections_Manager SHALL fall back to BLE delivery with compressed media
7. WHERE Nearby Connections permission is not granted, THE Nearby_Connections_Manager SHALL request runtime permissions
8. WHILE transferring media, THE Nearby_Connections_Manager SHALL support pause and resume functionality
9. WHEN transfer is interrupted, THE Nearby_Connections_Manager SHALL retry up to 3 times before marking as FAILED

### Requirement 6: End-to-End Encryption for Offline Messages

**User Story:** As a user, I want my offline messages to be encrypted, so that my privacy is protected even when messages hop through other devices.

#### Acceptance Criteria

1. WHEN a message is queued for offline delivery, THE Encryption_Manager SHALL encrypt the message content using Signal Protocol
2. WHEN encrypting, THE Encryption_Manager SHALL use recipient's public key from local key store
3. IF recipient's public key is not available, THEN THE Encryption_Manager SHALL mark message as FAILED and notify user
4. WHEN a mesh node forwards a message, THE BLE_Mesh_Manager SHALL NOT decrypt the message payload
5. WHEN recipient receives an encrypted message, THE Encryption_Manager SHALL decrypt using recipient's private key
6. THE Encryption_Manager SHALL rotate encryption keys every 30 days
7. WHEN key rotation occurs, THE Encryption_Manager SHALL re-encrypt pending messages with new keys
8. THE Encryption_Manager SHALL store encryption keys in Android Keystore for hardware-backed security

### Requirement 7: Online-Offline Synchronization

**User Story:** As a user, I want my offline messages to automatically sync when I get internet, so that all my conversations are up-to-date across delivery methods.

#### Acceptance Criteria

1. WHEN internet connection becomes available, THE Sync_Manager SHALL retrieve all PENDING and FAILED messages from queue
2. WHEN syncing messages, THE Sync_Manager SHALL update delivery method from BLE to ONLINE
3. WHEN syncing messages, THE Sync_Manager SHALL send messages to Firestore in chronological order
4. WHEN a message is successfully synced, THE Sync_Manager SHALL update message status to SENT and mark queue item as SENT
5. WHEN sync completes, THE Sync_Manager SHALL update chat's last message timestamp in Firestore
6. WHILE syncing, THE Sync_Manager SHALL respect rate limits (maximum 10 messages per second)
7. IF sync fails for a message, THEN THE Sync_Manager SHALL increment retry count and schedule retry with exponential backoff
8. WHEN all messages are synced, THE Sync_Manager SHALL clean up SENT queue items older than 7 days

### Requirement 8: Message Status Tracking

**User Story:** As a user, I want to see the delivery status of my messages, so that I know whether they were sent via internet or BLE mesh.

#### Acceptance Criteria

1. WHEN a message is being sent, THE Message_Queue_Processor SHALL update message status to SENDING
2. WHEN a message is transmitted via BLE, THE Message_Queue_Processor SHALL update status to SENT with delivery method BLE
3. WHEN a message is transmitted online, THE Message_Queue_Processor SHALL update status to SENT with delivery method ONLINE
4. WHEN recipient's device receives the message, THE Message_Queue_Processor SHALL update status to DELIVERED
5. WHEN recipient reads the message, THE Message_Queue_Processor SHALL update status to READ
6. THE ChatViewModel SHALL display appropriate status icon based on delivery method (cloud icon for ONLINE, bluetooth icon for BLE)
7. WHEN user taps on message info, THE ChatViewModel SHALL show detailed delivery information including hop count for BLE messages
8. WHILE message is in SENDING state for more than 30 seconds, THE ChatViewModel SHALL show retry option to user

### Requirement 9: Battery Optimization

**User Story:** As a user, I want the offline messaging feature to be battery efficient, so that my device battery lasts longer.

#### Acceptance Criteria

1. WHILE screen is off, THE BLE_Mesh_Manager SHALL reduce scan frequency from continuous to every 30 seconds
2. WHILE device battery is below 20%, THE BLE_Mesh_Manager SHALL reduce scan frequency to every 60 seconds
3. WHEN device enters Doze mode, THE Foreground_Service SHALL use AlarmManager for periodic wake-ups
4. THE BLE_Mesh_Manager SHALL use BLE scan filters to reduce CPU wake-ups
5. WHEN no messages are pending, THE BLE_Mesh_Manager SHALL stop advertising after 5 minutes of inactivity
6. THE BLE_Mesh_Manager SHALL batch message transmissions to reduce radio wake-ups
7. WHEN user disables offline messaging in settings, THE BLE_Mesh_Manager SHALL stop all BLE operations immediately

### Requirement 10: Permission Management

**User Story:** As a user, I want to be clearly informed about why permissions are needed, so that I can make informed decisions about granting them.

#### Acceptance Criteria

1. WHEN app first launches, THE Permission_Manager SHALL request Bluetooth permission with clear explanation
2. WHERE Android 12+ (API 31+), THE Permission_Manager SHALL request BLUETOOTH_SCAN and BLUETOOTH_CONNECT permissions
3. WHERE Android 12+ (API 31+), THE Permission_Manager SHALL request BLUETOOTH_ADVERTISE permission for mesh node advertising
4. WHEN BLE features are used, THE Permission_Manager SHALL request ACCESS_FINE_LOCATION permission with explanation
5. WHEN Nearby Connections is used, THE Permission_Manager SHALL request NEARBY_WIFI_DEVICES permission (Android 13+)
6. IF any required permission is denied, THEN THE Permission_Manager SHALL show in-app explanation and settings shortcut
7. THE Permission_Manager SHALL check permissions before each BLE or Wi-Fi Direct operation
8. WHEN permissions are revoked while app is running, THE Permission_Manager SHALL gracefully disable affected features

### Requirement 11: Foreground Service for Background Operation

**User Story:** As a user, I want to receive offline messages even when the app is in background, so that I don't miss important communications.

#### Acceptance Criteria

1. WHEN offline messaging is enabled, THE Foreground_Service SHALL start with a persistent notification
2. THE Foreground_Service SHALL display notification showing "Offline messaging active" with mesh node count
3. WHEN a message is received via BLE, THE Foreground_Service SHALL show a separate notification for the new message
4. THE Foreground_Service SHALL use notification channel with IMPORTANCE_LOW to minimize disruption
5. WHEN user disables offline messaging, THE Foreground_Service SHALL stop and remove notification
6. WHILE Foreground_Service is running, THE BLE_Mesh_Manager SHALL maintain mesh connections
7. IF system kills the Foreground_Service, THEN THE Foreground_Service SHALL restart automatically within 10 seconds
8. THE Foreground_Service SHALL include action buttons in notification for quick disable

### Requirement 12: Peer Discovery and Connection Management

**User Story:** As a user, I want the app to efficiently discover and connect to nearby devices, so that my messages can be routed through the mesh network.

#### Acceptance Criteria

1. THE BLE_Mesh_Manager SHALL scan for devices advertising Linker mesh service UUID
2. WHEN a potential mesh node is discovered, THE BLE_Mesh_Manager SHALL verify it's running compatible protocol version
3. WHEN connecting to a peer, THE BLE_Mesh_Manager SHALL limit concurrent connections to 7 devices (Android BLE limit)
4. WHEN connection limit is reached, THE BLE_Mesh_Manager SHALL prioritize peers with better signal strength (RSSI)
5. THE BLE_Mesh_Manager SHALL maintain connection to peers with pending messages for that peer
6. WHEN a peer disconnects, THE BLE_Mesh_Manager SHALL attempt reconnection up to 3 times with 5-second intervals
7. THE BLE_Mesh_Manager SHALL cache discovered peers for 5 minutes to reduce scan overhead
8. WHEN peer signal strength drops below -90 dBm, THE BLE_Mesh_Manager SHALL mark connection as unstable

### Requirement 13: Message Deduplication

**User Story:** As a user, I want to receive each message only once, so that I don't see duplicate messages when they arrive via multiple routes.

#### Acceptance Criteria

1. THE BLE_Mesh_Manager SHALL maintain a cache of received message IDs for the last 24 hours
2. WHEN a message is received via BLE, THE BLE_Mesh_Manager SHALL check if message ID exists in cache
3. IF message ID exists in cache, THEN THE BLE_Mesh_Manager SHALL discard the duplicate message
4. IF message ID is new, THEN THE BLE_Mesh_Manager SHALL add it to cache and process the message
5. THE BLE_Mesh_Manager SHALL use LRU (Least Recently Used) eviction policy for message ID cache
6. WHEN cache size exceeds 10,000 entries, THE BLE_Mesh_Manager SHALL remove oldest entries
7. WHEN app restarts, THE BLE_Mesh_Manager SHALL persist message ID cache to Room database

### Requirement 14: Error Handling and Retry Logic

**User Story:** As a user, I want the app to automatically retry failed message deliveries, so that my messages eventually reach the recipient.

#### Acceptance Criteria

1. WHEN a BLE transmission fails, THE Message_Queue_Processor SHALL increment retry count in MessageQueueEntity
2. WHEN retry count is less than maxRetries (3), THE Message_Queue_Processor SHALL schedule retry with exponential backoff
3. THE Message_Queue_Processor SHALL use backoff delays of 5s, 15s, 45s for retries 1, 2, 3
4. WHEN retry count reaches maxRetries, THE Message_Queue_Processor SHALL mark message as FAILED
5. WHEN a message is marked FAILED, THE Message_Queue_Processor SHALL notify user with actionable error message
6. IF GATT connection fails, THEN THE BLE_Mesh_Manager SHALL log error with error code and retry connection
7. WHEN encryption fails, THE Encryption_Manager SHALL mark message as FAILED immediately without retry
8. THE Message_Queue_Processor SHALL store error messages in MessageQueueEntity for debugging

### Requirement 15: Settings and User Controls

**User Story:** As a user, I want to control offline messaging settings, so that I can customize the feature according to my preferences.

#### Acceptance Criteria

1. THE Settings_Screen SHALL provide a toggle to enable/disable offline messaging
2. THE Settings_Screen SHALL provide a toggle to enable/disable BLE mesh networking
3. THE Settings_Screen SHALL provide a toggle to enable/disable Wi-Fi Direct media transfer
4. THE Settings_Screen SHALL display current mesh node count and connection status
5. THE Settings_Screen SHALL provide option to clear offline message queue
6. THE Settings_Screen SHALL provide option to set maximum TTL for BLE messages (range: 1-10 hops)
7. WHEN user disables offline messaging, THE Settings_Screen SHALL show confirmation dialog explaining consequences
8. THE Settings_Screen SHALL display battery usage statistics for offline messaging features
9. THE Settings_Screen SHALL provide option to enable/disable foreground service notification

### Requirement 16: Mesh Protocol Packet Structure

**User Story:** As a developer, I want a well-defined packet structure for BLE mesh messages, so that devices can reliably parse and route messages.

#### Acceptance Criteria

1. THE BLE_Mesh_Manager SHALL use packet structure containing: version (1 byte), message_id (16 bytes), sender_id (variable), recipient_id (variable), ttl (1 byte), hop_count (1 byte), payload_length (2 bytes), encrypted_payload (variable)
2. THE BLE_Mesh_Manager SHALL use protocol version 1 for initial implementation
3. WHEN packet size exceeds BLE MTU (512 bytes), THE BLE_Mesh_Manager SHALL fragment message into multiple packets
4. WHEN sending fragmented message, THE BLE_Mesh_Manager SHALL include fragment_index and total_fragments in packet header
5. WHEN receiving fragmented message, THE BLE_Mesh_Manager SHALL reassemble fragments before processing
6. THE BLE_Mesh_Manager SHALL validate packet checksum before processing
7. IF packet validation fails, THEN THE BLE_Mesh_Manager SHALL discard packet and log error

### Requirement 17: Analytics and Monitoring

**User Story:** As a developer, I want to monitor offline messaging performance, so that I can identify and fix issues.

#### Acceptance Criteria

1. THE Analytics_Logger SHALL log BLE mesh connection events (connected, disconnected, failed)
2. THE Analytics_Logger SHALL log message routing events (sent, forwarded, delivered, failed)
3. THE Analytics_Logger SHALL track average delivery time for BLE messages
4. THE Analytics_Logger SHALL track hop count distribution for delivered messages
5. THE Analytics_Logger SHALL track battery consumption attributed to offline messaging
6. THE Analytics_Logger SHALL log error events with error codes and context
7. THE Analytics_Logger SHALL aggregate metrics locally and sync to backend when online
8. WHERE user has disabled analytics, THE Analytics_Logger SHALL only log critical errors locally

### Requirement 18: Compatibility and Migration

**User Story:** As a user, I want offline messaging to work seamlessly with existing online messaging, so that I have a unified experience.

#### Acceptance Criteria

1. THE Message_Repository SHALL support both online and offline delivery methods transparently
2. WHEN sending a message, THE Message_Repository SHALL automatically select delivery method based on connectivity
3. THE Message_Repository SHALL store all messages in MessageEntity regardless of delivery method
4. WHEN a message sent via BLE is later synced online, THE Message_Repository SHALL update delivery method to ONLINE
5. THE ChatViewModel SHALL display messages from both delivery methods in chronological order
6. THE Message_Repository SHALL maintain backward compatibility with existing MessageEntity schema
7. WHEN upgrading from version without offline messaging, THE Migration_Manager SHALL add new columns to MessageQueueEntity
8. THE Message_Repository SHALL handle race conditions when same message arrives via both BLE and online

