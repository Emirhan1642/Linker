<div align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp" width="120" alt="Linker Logo" onerror="this.src='https://via.placeholder.com/120?text=Linker'">
  
  # Linker - The Offline-First Social Network

  **Connect Anywhere. Anytime. Even Without Internet.**

  [![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-blue.svg?logo=kotlin)](https://kotlinlang.org)
  [![Jetpack Compose](https://img.shields.io/badge/Compose-Material%203-4285F4.svg?logo=android)](https://developer.android.com/jetpack/compose)
  [![Clean Architecture](https://img.shields.io/badge/Architecture-Clean-brightgreen.svg)]()

</div>

---

A revolutionary social media application that brings together the best features of Instagram, TikTok, WhatsApp, and Twitter, supercharged with unique **offline-first** capabilities using BLE mesh networking and Wi-Fi Direct.

## ✨ Why Linker?

Most social apps die the moment you lose internet connection. Linker thrives in it. Whether you are at a crowded concert, deep in the mountains, or facing a network outage, Linker's hybrid connectivity ensures your messages and content still reach their destination through peer-to-peer device hopping.

## 🚀 Key Features

### 🌐 Hybrid Connectivity
- **Seamless Transition**: Gracefully switches between online and offline modes.
- **BLE Mesh Networking**: Messages hop securely between nearby devices to reach their destination.
- **Wi-Fi Direct**: Ultra-fast media sharing (photos/videos) using Google's Nearby Connections API.
- **End-to-End Encryption**: Total privacy, even when routing through offline peer nodes.

### 📱 Content & Engagement
- **Stories**: 24-hour expiring moments (up to 30s videos).
- **Link Feed**: A unified feed of posts, short-form videos (Reels style), and thoughts.
- **Notes**: Fleeting text, music, or countdowns.
- **Rich Interactions**: Like, Save, Share, Comment, and "Relink" (Repost).

### 💬 Seamless Chat
- **Private & Group Chats**: Rich messaging experience with full offline fallback.
- **E2E Encrypted**: Powered by the Signal Protocol.

---

## 🛠 Tech Stack

Built with the absolute cutting-edge Android development ecosystem:

| Component | Technology |
| :--- | :--- |
| **Language** | [Kotlin 2.1.0](https://kotlinlang.org/) |
| **UI Framework** | [Jetpack Compose](https://developer.android.com/jetpack/compose) + Material 3 |
| **Architecture** | MVVM & Clean Architecture |
| **Dependency Injection** | [Dagger Hilt](https://dagger.dev/hilt/) |
| **Local Database** | [Room](https://developer.android.com/training/data-storage/room) (SQLite) |
| **Backend & Auth** | Firebase (Auth/Firestore) + Supabase Edge Functions |
| **Media Delivery** | [Cloudinary](https://cloudinary.com/) |
| **Offline Networking** | Android BLE API + Google Nearby Connections API |

---

## 📂 Project Structure

Linker follows a strict Clean Architecture pattern to ensure scalability, testability, and separation of concerns. Dive deeper in our [Architecture Guide](ARCHITECTURE.md).

```text
app/src/main/java/com/linker/app/
├── core/           # Utilities, DI, Security, Notifications
├── data/           # Repositories, Local DB (Room), Remote API
├── domain/         # Use Cases, Domain Models, Repository Interfaces
├── connectivity/   # BLE Mesh & Wi-Fi Direct implementations
└── presentation/   # Compose UI, ViewModels, Navigation, Theme
```

---

## 🔧 Getting Started

### Prerequisites
- Android Studio Ladybug (or newer)
- JDK 17
- Android SDK 26+ (Target 34)

### 1. Clone & Setup
```bash
git clone https://github.com/Emirhan1642/linker.git
cd linker
```

### 2. Configuration & API Keys
Linker prioritizes security. You must provide your own API keys via a `local.properties` file in the root directory.

Create `local.properties` and add:
```properties
# Cloudinary
cloudinary.apiKey=your_api_key
cloudinary.apiSecret=your_api_secret

# Supabase
supabase.anonkey=your_anon_key
supabase.publishablekey=your_publishable_key

# Other APIs
spotify.clientSecret=your_spotify_secret
maps.apiKey=your_maps_key
giphy.apiKey=your_giphy_key
foursquare.apiKey=your_foursquare_key
```

*Don't worry, `local.properties` is strictly ignored by Git.* Check our [Security Guide](SECURITY.md) for more info.

### 3. Firebase Setup
Add your own `google-services.json` to the `app/` directory to enable Authentication and Firestore.

### 4. Build & Run
```bash
./gradlew installDebug
```

---

## 📚 Documentation

For deep dives into how Linker works under the hood, check out:
- [🏗 Architecture Documentation](ARCHITECTURE.md) - Learn about our MVVM + Clean Architecture implementation.
- [🔐 Security Guidelines](SECURITY.md) - Discover how we keep user data safe and encrypted.

---

## 🤝 Contributing

We welcome contributions! Whether it's fixing bugs, improving offline mesh routing, or adding new UI features, please check our contributing guidelines before submitting a PR.

---
<div align="center">
  <b>Built with ❤️ using Kotlin and Jetpack Compose</b>
</div>
