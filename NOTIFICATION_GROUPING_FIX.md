# Bildirim Gruplama ve Mesaj Geçmişi Düzeltmesi (v2)

## Sorunlar

### 1. Çoklu Bildirimler (Gruplama Sorunu)
- Aynı sohbet için birden fazla bildirim gösteriliyordu
- Her mesaj ayrı bir bildirim olarak görünüyordu
- Bildirimler tek bir grup altında toplanmıyordu

### 2. Mesaj Geçmişi Kaybı
- Bildirimden yanıt verildiğinde önceki mesajlar kayboluyordu
- Örnek: "inanılmaz" mesajı gönderildi, "Değil mi ya" ile yanıt verildi
- Sonuç: Sadece "Değil mi ya" mesajı iki kez gösterildi, "inanılmaz" kayboldu

### 3. Bildirim Başlığı Formatı
- Önceki format: "Linker • Emirhan"
- İstenen format: "Linker • Emirhan bir mesaj gönderdi"

### 4. Grup Sohbetlerinde Gönderen Karışıklığı (YENİ SORUN)
**Senaryo:**
- A hesabı "Message A" gönderir → B ve C'ye bildirim gelir
- B hesabı bildirimden "Message B" yanıtlar
- **Problem**: C hesabında "Message A" kayboluyor, sadece "Message B" görünüyor
- **Neden**: `ChatNotificationState` içinde `senderId` ve `senderName` sabit tutuluyordu

## Çözümler

### 1. ChatNotificationState Yapısı Değiştirildi

#### ❌ ÖNCEKİ (YANLIŞ):
```kotlin
data class ChatNotificationState(
    val recipientUid: String,
    val chatId: String,
    val senderId: String,      // ❌ Sabit gönderen - grup sohbetlerinde hata!
    val senderName: String,    // ❌ Sabit gönderen adı
    val messages: MutableList<String> = mutableListOf(),
    val isGroupChat: Boolean = false
)
```

**Problem**: Grup sohbetlerinde birden fazla gönderen olabilir. İlk mesajı RTX gönderdiğinde `senderId = "RTX_ID"` olarak kaydediliyor. İkinci mesaj Emirhan'dan geldiğinde aynı `notificationId` kullanıldığı için mevcut state döndürülüyor ama `senderId` hala RTX!

#### ✅ YENİ (DOĞRU):
```kotlin
data class ChatNotificationState(
    val recipientUid: String,
    val chatId: String,
    // senderId ve senderName kaldırıldı - mesajlarda zaten var!
    val messages: MutableList<String> = mutableListOf(),
    val isGroupChat: Boolean = false
)
```

**Çözüm**: Gönderen bilgisi mesaj formatında zaten var:
- Grup: `"Emirhan: Message B"`
- Özel: `"Message B"`
- Kullanıcı: `"Siz: Message B"`

### 2. ChatNotificationStore Basitleştirildi

```kotlin
fun getOrCreate(
    notificationId: Int,
    recipientUid: String,
    chatId: String,
    isGroupChat: Boolean = false
): ChatNotificationState {
    return store.getOrPut(notificationId) {
        ChatNotificationState(
            recipientUid = recipientUid,
            chatId = chatId,
            isGroupChat = isGroupChat
        )
    }
}

fun addIncoming(
    notificationId: Int,
    recipientUid: String,
    chatId: String,
    message: String,  // Mesaj zaten formatlanmış: "SenderName: content"
    isGroupChat: Boolean = false
) {
    val state = getOrCreate(notificationId, recipientUid, chatId, isGroupChat)
    state.messages.add(message)  // Mesaj olduğu gibi ekleniyor
}
```

### 3. LinkerMessagingService Güncellendi

```kotlin
// FCM'den gelen mesaj zaten formatlanmış geliyor
val body = message.notification?.body ?: data["body"] ?: "Sent you a message"
// Grup: "Emirhan: Message B"
// Özel: "Message B"

ChatNotificationStore.addIncoming(
    notificationId,
    recipientUid = recipientId,
    chatId = chatId,
    message = body,  // Formatlanmış mesaj
    isGroupChat = isGroup
)
```

### 4. NotificationActionReceiver Güncellendi

```kotlin
// Kullanıcı yanıtı "Siz: " öneki ile ekleniyor
ChatNotificationStore.addOutgoing(notificationId, replyText)
// Store'a "Siz: Message B" olarak eklenir
```

## Mesaj Akışı Örneği

### Senaryo: 3 Kişilik Grup (A, B, C)

#### Adım 1: A hesabı "Message A" gönderir
```
B hesabı store:
  notificationId = hash(B + chatId)
  messages = ["RTX: Message A"]
  
C hesabı store:
  notificationId = hash(C + chatId)
  messages = ["RTX: Message A"]
```

#### Adım 2: B hesabı bildirimden "Message B" yanıtlar
```
B hesabı store (yanıt öncesi):
  messages = ["RTX: Message A"]

B hesabı store (yanıt sonrası - addOutgoing):
  messages = ["RTX: Message A", "Siz: Message B"]
```

#### Adım 3: A ve C hesaplarına "Message B" bildirimi gelir

```
A hesabı store (yeni):
  notificationId = hash(A + chatId)
  messages = ["Emirhan: Message B"]
  
B hesabı store (FCM'den gelen):
  notificationId = hash(B + chatId)  // AYNI ID!
  messages = ["RTX: Message A", "Siz: Message B", "Emirhan: Message B"]
  // ✅ Mevcut mesajlar korundu, yeni mesaj eklendi
  
C hesabı store (FCM'den gelen):
  notificationId = hash(C + chatId)  // AYNI ID!
  messages = ["RTX: Message A", "Emirhan: Message B"]
  // ✅ Mevcut mesajlar korundu, yeni mesaj eklendi
```

## Bildirim Gruplama Mekanizması

### Notification ID Hesaplama
```kotlin
private fun stableChatNotificationId(
    recipientId: String, 
    chatId: String, 
    senderId: String, 
    isGroup: Boolean
): Int {
    val branch = if (isGroup) "g|$chatId" else "u|$senderId"
    val key = "$recipientId|$branch"
    return key.hashCode() and 0x7fff_fffe
}
```

**Nasıl Çalışır:**
- **Grup Sohbet**: `hash(recipientId + "g|" + chatId)` → Tüm gönderenler için aynı ID
- **Özel Sohbet**: `hash(recipientId + "u|" + senderId)` → Her gönderen için farklı ID

### Gruplama Anahtarı
```kotlin
.setGroup("linker_chat_${chatId}_${targetAccountUid}")
.setGroupSummary(false)
```

## Mesaj Formatları

### Backend'den Gelen Format (MessageRepositoryImpl)
```kotlin
val displayText = content?.take(50) ?: "[Media]"
val notificationMessage = when (chat.chatType) {
    ChatType.PRIVATE -> displayText              // "Message B"
    ChatType.GROUP -> "$senderName: $displayText" // "Emirhan: Message B"
}
```

### Kullanıcı Yanıtı Format (ChatNotificationStore)
```kotlin
fun addOutgoing(notificationId: Int, message: String) {
    state.messages.add("Siz: $message")  // "Siz: Message B"
}
```

### Bildirimde Gösterim (ChatNotificationHelper)
```kotlin
for (msg in messages) {
    val (messageSender, messageText) = if (msg.startsWith("Siz: ")) {
        // "Siz: Message B" → Person("Siz"), "Message B"
        val me = Person.Builder().setName("Siz").build()
        me to msg.substring(5)
    } else if (isGroupChat && msg.contains(": ")) {
        // "Emirhan: Message B" → Person("Emirhan"), "Message B"
        val colonIndex = msg.indexOf(": ")
        val senderNameInMsg = msg.substring(0, colonIndex)
        val messageContent = msg.substring(colonIndex + 2)
        val sender = Person.Builder().setName(senderNameInMsg).build()
        sender to messageContent
    } else {
        // "Message B" → Person(senderName), "Message B"
        person to msg
    }
    
    messagingStyle.addMessage(messageText, System.currentTimeMillis(), messageSender)
}
```

## Test Senaryosu

### Senaryo: 3 Kişilik Grup (RTX, Emirhan, C)

#### Adım 1: RTX "Nasılsınız" gönderir
```
Emirhan bildirimi:
  - RTX: Nasılsınız

C bildirimi:
  - RTX: Nasılsınız
```

#### Adım 2: Emirhan bildirimden "İyiyiz" yanıtlar
```
Emirhan bildirimi (yanıt sonrası):
  - RTX: Nasılsınız
  - Siz: İyiyiz

RTX bildirimi (yeni):
  - Emirhan: İyiyiz

C bildirimi (güncellendi):
  - RTX: Nasılsınız
  - Emirhan: İyiyiz  ✅ Önceki mesaj korundu!
```

#### Adım 3: C "Harika" yanıtlar
```
C bildirimi (yanıt sonrası):
  - RTX: Nasılsınız
  - Emirhan: İyiyiz
  - Siz: Harika

RTX bildirimi (güncellendi):
  - Emirhan: İyiyiz
  - C: Harika

Emirhan bildirimi (güncellendi):
  - RTX: Nasılsınız
  - Siz: İyiyiz
  - C: Harika  ✅ Tüm mesajlar korundu!
```

## Değiştirilen Dosyalar

1. **`ChatNotificationStore.kt`** - Kritik Değişiklik
   - `senderId` ve `senderName` alanları kaldırıldı
   - `getOrCreate()` ve `addIncoming()` basitleştirildi
   - Mesaj formatı zaten gönderen bilgisini içeriyor

2. **`LinkerMessagingService.kt`**
   - `addIncoming()` çağrısı güncellendi (senderId/senderName kaldırıldı)

3. **`NotificationActionReceiver.kt`**
   - `addIncoming()` çağrıları güncellendi (senderId/senderName kaldırıldı)

4. **`ChatNotificationHelper.kt`** (önceki versiyonda)
   - Bildirim başlığı formatı
   - MessagingStyle grup/özel ayrımı
   - Mesaj formatı ayrıştırma

5. **`MessageRepositoryImpl.kt`** (önceki versiyonda)
   - Özel sohbetlerde mesaj içeriği düzeltmesi

## Sonuç

✅ Bildirimler doğru şekilde gruplanıyor
✅ Mesaj geçmişi korunuyor (tüm gönderenlerden)
✅ Bildirim başlığı formatı güncellendi
✅ Grup ve özel sohbetler doğru şekilde ayrıştırılıyor
✅ Özel sohbetlerde mesaj içeriği düzgün gösteriliyor
✅ **Grup sohbetlerinde birden fazla gönderen desteği** ✨
