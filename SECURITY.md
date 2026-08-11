<div align="center">
  
  # 🔐 Linker Security Guidelines
  
  **Privacy & Security First**

</div>

---

Linker handles sensitive user communications and private mesh network data. Security is not an afterthought; it is integrated into the foundation of the app.

## 🛡 Network & Data Protection

### 1. End-to-End Encryption (E2E)
- **Protocol**: Implementation based on the Signal Protocol.
- **Scope**: All private chats are encrypted before leaving the device, regardless of whether they route through the internet or the offline BLE mesh network.

### 2. API Key Management
**Zero Hardcoded Keys**: 
We strictly prohibit hardcoding API keys in the source code.
- During development, keys are read from `local.properties` and injected via `BuildConfig`.
- **Runtime Encryption**: Upon first launch, keys are migrated to `EncryptedSharedPreferences` (backed by the Android Hardware Keystore) and wiped from memory.

### 3. Local Storage Security
- **Encrypted Preferences**: AES-256-GCM encryption for all session data and tokens.
- **Room Database**: Excluded from Android Auto-Backup to prevent cloud extraction.

---

## 🕵️ Device & Runtime Security

Linker implements a multi-layered defense system through `RootDetector.kt` and `SecurityLogger.kt`.

### Detection Mechanisms
- **Root Detection**: Scans for `su` binaries, Magisk, SuperSU, and test-keys.
- **Emulator Detection**: Prevents automated scraping and analysis by identifying emulated environments.

### Input Validation
All user inputs are strictly sanitized via `InputValidator.kt`:
- HTML entity encoding to prevent XSS.
- Strict RegEx validation for Emails, Usernames (no squatting), and Passwords (entropy requirements).

---

## 🚨 Vulnerability Reporting

Security is a community effort. If you discover a vulnerability in Linker's mesh network protocol, encryption implementation, or backend configuration, please **DO NOT** open a public issue.

Instead, please email the development team directly. We strive to respond to all security reports within 24-48 hours.

---

## ✅ Pre-Release Security Checklist

Before cutting a production release, the following must be verified:
- [ ] `usesCleartextTraffic="false"` is enforced in Network Security Config.
- [ ] ProGuard / R8 obfuscation is enabled.
- [ ] Firebase App Check is activated.
- [ ] All `BuildConfig` keys are migrated to a backend proxy or Firebase Remote Config.
