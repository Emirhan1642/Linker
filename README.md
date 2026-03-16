# Linker - Offline-First Social Media App

A revolutionary social media application combining features from Instagram, TikTok, WhatsApp, and Twitter with unique **offline-first** capabilities using BLE mesh networking and Wi-Fi Direct.

## 🚀 Features

### Core Functionality
- **Hybrid Connectivity**: Seamless online/offline communication
- **BLE Mesh Networking**: Messages hop between devices to reach destinations
- **Wi-Fi Direct Media Transfer**: Fast photo/video sharing using Nearby Connections API
- **E2E Encryption**: All messages are end-to-end encrypted

### Content Types
- **Stories**: 24-hour expiring content (max 30 sec, <10MB)
- **Links (Posts)**: Feed, Video, and Reels (Instagram/TikTok style)
- **Notes**: Text, Music, or Countdown with 24-hour expiration

### Social Features
- **Chat System**: Private & Group chats with offline fallback
- **Interactions**: Like, Save, Share, Comment, Relink (Repost)
- **Profile**: Tabbed views with fast account switching

## 🛠️ Tech Stack

| Category | Technology |
|----------|------------|
| **Language** | Kotlin 2.1.0 |
| **UI** | Jetpack Compose + Material 3 |
| **Architecture** | MVVM + Clean Architecture |
| **DI** | Hilt |
| **Local DB** | Room |
| **Auth** | Firebase Authentication |
| **Backend** | Supabase Edge Functions |
| **Media** | Cloudinary |
| **Networking** | Retrofit + OkHttp |
| **BLE** | Android BLE API |
| **P2P** | Google Nearby Connections API |

## 📁 Project Structure

```
app/
├── src/main/
│   ├── java/com/linker/app/
│   │   ├── LinkerApp.kt                 # Application class
│   │   ├── MainActivity.kt              # Single Activity
│   │   │
│   │   ├── core/                        # Core utilities
│   │   │   ├── di/                      # Hilt modules
│   │   │   ├── notification/            # FCM service
│   │   │   └── util/                    # Extensions
│   │   │
│   │   ├── data/                        # Data Layer
│   │   │   ├── local/                   # Room DB
│   │   │   ├── remote/                  # API services
│   │   │   └── repository/              # Repositories
│   │   │
│   │   ├── domain/                      # Domain Layer
│   │   │   ├── model/                   # Domain models
│   │   │   ├── repository/              # Interfaces
│   │   │   └── usecase/                 # Business logic
│   │   │
│   │   ├── connectivity/                # Offline Layer
│   │   │   ├── ble/                     # BLE mesh
│   │   │   └── nearby/                  # Wi-Fi Direct
│   │   │
│   │   └── presentation/                # UI Layer
│   │       ├── navigation/              # Nav graph
│   │       ├── theme/                   # Material Theme
│   │       └── screens/                 # UI screens
│   │
│   └── res/                             # Resources
```

## 🔧 Setup Instructions

### Prerequisites
- Android Studio Ladybug or newer
- JDK 17
- Android SDK 26+ (Android 8.0+)
- Firebase project
- Supabase project
- Cloudinary account

### Configuration

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd Linker
   ```

2. **Configure credentials**
   
   The `local.properties` file is already configured with:
   - Cloudinary credentials
   - Supabase URL and anon key
   
   ⚠️ **Never commit `local.properties` to version control!**

3. **Firebase Setup**
   
   The `google-services.json` is already included. Firebase Authentication is configured for:
   - Google Sign-In
   - Email/Password
   - Phone Authentication

4. **Build the project**
   ```bash
   ./gradlew build
   ```

5. **Run on device/emulator**
   ```bash
   ./gradlew installDebug
   ```

## 📱 Current Status

### ✅ Completed (Phase 1)
- [x] Project structure setup
- [x] Gradle configuration with all dependencies
- [x] Firebase integration
- [x] Supabase configuration
- [x] Material 3 theming (Dark/Light/Dynamic)
- [x] Navigation architecture
- [x] Hilt dependency injection
- [x] Basic screens (Splash, Auth, Home)
- [x] Room database foundation

### 🚧 In Progress (Phase 2)
- [ ] Authentication implementation
- [ ] Room entities and DAOs
- [ ] Repository pattern
- [ ] ViewModels

### 📋 Upcoming (Phase 3+)
- [ ] BLE mesh networking
- [ ] Wi-Fi Direct integration
- [ ] E2E encryption
- [ ] Content creation
- [ ] Chat system
- [ ] Social features

## 🔐 Security

- **E2E Encryption**: All messages encrypted using Signal Protocol
- **Secure Storage**: Sensitive data stored in encrypted preferences
- **Permission Management**: Runtime permissions for camera, BLE, location
- **ProGuard**: Code obfuscation enabled for release builds

## 📄 License

This project is proprietary and confidential.

## 👥 Team

- **Developer**: Emirhan
- **Project**: Linker
- **Started**: February 2026

## 📞 Support

For questions or issues, please contact the development team.

---

**Built with ❤️ using Kotlin and Jetpack Compose**
