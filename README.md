# SENTINEL - SpoofGuard Dialer

A secure, custom Android Dialer replacement application built with Jetpack Compose and Android Telecom API (`InCallService`). Features native in-call management, automated call bridging/merging (SpoofGuard) for call authentication and security, and programmatic DTMF tone injection.

---

## 📱 Features

- **Custom Dialer Interface:** Modern UI built with Jetpack Compose (Material 3), featuring:
  - **Dialpad:** Dynamic dialpad with speed dial and quick actions (`DialpadScreen`)
  - **Contacts:** Contact browsing with search and direct call triggering (`ContactsScreen`)
  - **Call Logs (Recents):** Categorized call history (`RecentsScreen`)
  - **Settings:** In-call preferences, default dialer prompts, and SpoofGuard controls (`SettingsScreen`)
- **In-Call Experience:**
  - **Incoming Call Screen:** Full-screen incoming call UI via `FullScreenIntent` with Answer/Decline and Answer with SpoofGuard options.
  - **Active Call Screen:** Control active calls with Mute, Hold, Keypad, Speakerphone, and Hangup.
- **SpoofGuard (Call Bridging & Merging):**
  - Connects to an external security bridge (Exotel) upon call connection.
  - Automatically places the active call on hold, dials the secure bridge, waits for connection, injects DTMF authentication sequences, and conferences/merges the calls seamlessly.
- **Programmatic DTMF Tone Injection:** Automates IVR navigation and identity verification.

---

## 🏗 Architecture & Modules

The project is structured into modular components:

- **`app`**: Application module containing Jetpack Compose UI screens, Navigation, ViewModels (`CallViewModel`), and Material 3 theme.
- **`telecom-core`**: Headless telecom library containing:
  - `SpoofGuardInCallService`: Extends `InCallService` to handle incoming/outgoing call lifecycles.
  - `TelecomCallRegistry`: Reactive singleton holding `StateFlow<List<Call>>`.
  - `CallBridgeController`: State machine managing multi-call bridging, DTMF injection, and conference merging.
  - `CallPlacer`: Manages outgoing call requests via `TelecomManager`.

---

## 🛠 Tech Stack

- **Language:** Kotlin (JVM Target 17)
- **UI Toolkit:** Jetpack Compose, Material 3
- **Asynchronous:** Kotlin Coroutines & Flows
- **Android APIs:**
  - `android.telecom.InCallService`
  - `android.telecom.TelecomManager`
  - `android.telecom.Call`
- **Build System:** Gradle (Kotlin DSL `.gradle.kts`) with Version Catalog (`libs.versions.toml`)

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Hedgehog or newer
- JDK 17
- Android SDK 34+

### Setup
1. Clone the repository:
   ```bash
   git clone https://github.com/Kanishkar843/SENTINEL.git
   ```
2. Open the project in Android Studio.
3. Sync Gradle and build the project.
4. Run on an Android device or emulator (Android 8.0+ / API 26+).
5. Set **SpoofGuard Dialer** as the default Phone/Dialer app in Android system settings when prompted.

---

## 📄 Documentation

For in-depth architectural and sequence details, check out [`SpoofGuardDialer_Docs.md`](SpoofGuardDialer_Docs.md).
