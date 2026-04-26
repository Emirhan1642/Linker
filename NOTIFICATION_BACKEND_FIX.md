# FCM Bildirim Backend Düzeltmesi

## Sorun

FCM bildirimleri `notification` payload'ı ile gönderildiğinde, uygulama arka plandaysa Android otomatik olarak bildirimi gösterir ve `onMessageReceived()` çağrılmaz. Bu durum:

- `ChatNotificationStore`'un boş kalmasına
- Bildirimden reply yapıldığında state'in bulunamamasına
- Bildirim güncelleme sorunlarına neden oluyor

## Çözüm

FCM bildirimlerini **data-only** formatında göndermek gerekiyor.

## Backend Değişikliği (Supabase Edge Function)

### Dosya: `supabase/functions/send-chat-notification/index.ts`

#### ❌ ÖNCEKİ KOD (Yanlış):

```typescript
const message = {
  notification: {
    title: senderName,
    body: messageText
  },
  data: {
    type: 'MESSAGE',
    chatId: chatId,
    messageId: messageId,
    senderId: senderId,
    senderName: senderName,
    recipientId: recipientId,
    chatType: chatType,
    body: messageText
  },
  token: fcmToken
};

await admin.messaging().send(message);
```

#### ✅ YENİ KOD (Doğru):

```typescript
const message = {
  data: {
    type: 'MESSAGE',
    title: senderName,        // ← notification'dan data'ya taşındı
    body: messageText,         // ← notification'dan data'ya taşındı
    chatId: chatId,
    messageId: messageId,
    senderId: senderId,
    senderName: senderName,
    recipientId: recipientId,
    chatType: chatType
  },
  token: fcmToken,
  android: {
    priority: 'high'  // Yüksek öncelik için
  }
};

await admin.messaging().send(message);
```

## Neden Bu Değişiklik Gerekli?

### Notification + Data (Eski Yöntem):
- ✅ Uygulama ön plandaysa: `onMessageReceived()` çağrılır
- ❌ Uygulama arka plandaysa: Android otomatik gösterir, `onMessageReceived()` çağrılmaz
- ❌ Bildirim içeriği ve davranışı üzerinde tam kontrol yok

### Data-Only (Yeni Yöntem):
- ✅ Her durumda `onMessageReceived()` çağrılır
- ✅ Bildirim içeriği ve davranışı üzerinde tam kontrol
- ✅ `ChatNotificationStore` her zaman dolu
- ✅ Inline reply, actions, ve diğer özellikler düzgün çalışır

## Android Tarafında Değişiklik Gerekli mi?

**HAYIR!** Android kodu zaten her iki durumu da destekliyor:

```kotlin
val body = message.notification?.body ?: data["body"] ?: "Sent you a message"
```

Bu kod:
1. Önce `notification.body`'ye bakar
2. Yoksa `data["body"]`'yi kullanır
3. O da yoksa default mesaj gösterir

## Test

Backend değişikliğinden sonra:

1. Uygulama arka plandayken bildirim gönderin
2. Logcat'te şunu görmelisiniz:
   ```
   LinkerMessaging: onMessageReceived data={...}
   LinkerMessaging: handleChatNotification data={...}
   ChatNotificationStore: addIncoming: notificationId=..., chatId=...
   ```

3. Bildirimden reply yapın
4. Logcat'te şunu görmelisiniz:
   ```
   NotificationAction: handleReply: notificationId=...
   ChatNotificationStore: get: notificationId=..., found=true
   NotificationAction: Updating notification with X messages
   ```

## Geçici Çözüm

Backend değişikliği yapılana kadar, Android kodu fallback mekanizması içeriyor:
- Eğer `ChatNotificationStore` boşsa, minimal bir state oluşturuluyor
- Bu sayede bildirim güncellemesi çalışıyor
- Ama orijinal mesaj içeriği kaybolmuş oluyor

## Öncelik

🔴 **YÜKSEK** - Bu değişiklik en kısa sürede yapılmalı çünkü:
- Kullanıcı deneyimini doğrudan etkiliyor
- Bildirim özelliklerinin düzgün çalışması için gerekli
- Geçici çözüm ideal değil

## İlgili Dosyalar

### Backend:
- `supabase/functions/send-chat-notification/index.ts`

### Android:
- `app/src/main/java/com/linker/app/core/notification/LinkerMessagingService.kt`
- `app/src/main/java/com/linker/app/core/notification/NotificationActionReceiver.kt`
- `app/src/main/java/com/linker/app/core/notification/ChatNotificationStore.kt`

## Referanslar

- [FCM Data Messages](https://firebase.google.com/docs/cloud-messaging/concept-options#data_messages)
- [FCM Notification Messages](https://firebase.google.com/docs/cloud-messaging/concept-options#notifications)
- [Android FCM Best Practices](https://firebase.google.com/docs/cloud-messaging/android/receive)
