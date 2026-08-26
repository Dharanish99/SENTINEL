# SpoofGuard Dialer - Technical Documentation

This document provides a detailed overview of the SpoofGuard Dialer, explaining its architecture, how call merging (SpoofGuard) works, its feature set, and the underlying low-level code and libraries used.

## 1. Overview and Features

SpoofGuard Dialer is a fully functional, custom Android Dialer replacement application built using modern Android development practices. It leverages the Android `InCallService` API to intercept and manage phone calls natively, replacing the stock dialer interface.

### Features
*   **Custom Dialer Interface:** A complete suite of dialer screens built with Jetpack Compose, including a Dialpad (`DialpadScreen`), Contacts list (`ContactsScreen`), Recent calls/Call Log (`RecentsScreen`), and Settings (`SettingsScreen`).
*   **In-Call UI:** Custom screens for incoming calls (`IncomingCallScreen`) and active calls (`InCallScreen`) that handle standard telecom actions like Answer, Reject, Hold, Mute, and Speaker.
*   **SpoofGuard (Call Bridging/Merging):** The flagship feature of the dialer. It allows the app to automatically secure a call by bridging it through an external telecom provider (Exotel).
*   **Programmatic DTMF Injection:** The dialer can automatically navigate IVR menus or authenticate with a remote service by playing Dual-Tone Multi-Frequency (DTMF) tones.
*   **Full-Screen Notifications:** Utilizes Android's `FullScreenIntent` to present the user with a full-screen incoming call UI even when the device is locked.

---

## 2. How Call Merging (SpoofGuard) Works

The SpoofGuard feature is implemented as a programmatic call bridge. When a user chooses to answer a call securely, the dialer automatically connects a third-party service (Exotel) into the conversation to monitor, verify, or mask the call.

### The Bridging Workflow (`CallBridgeController.kt`)

The entire sequence is non-interactive and handles the native telecom complexities in the background:
1.  **Triggering:** When a call connects (and if the `armSecureOnConnect` flag is set to true in `CallViewModel`), the bridge sequence initiates.
2.  **Holding the Primary Call:** The active call is placed on hold (`primaryCall.hold()`) so the bridge call can be placed.
3.  **Placing the Bridge Call:** A secondary call is placed via `TelecomManager.placeCall(Uri.fromParts("tel", "9513886363", null), extras)`. This number is an Exotel-provisioned bridge number.
4.  **Awaiting Connection:** The controller waits up to 30 seconds for the new bridge call to reach the `STATE_ACTIVE` status.
5.  **Authentication (DTMF):** Once the bridge call answers, the dialer programmatically plays a DTMF sequence (`9944626918#`). This acts as an identifier for Exotel to know which account is connecting to the stream.
6.  **Merging (Conferencing):** After a brief delay (800ms) to allow the DTMF to be processed, the dialer invokes `primaryCall.conference(bridgeCall)`. This native Android telecom command merges the two calls into a single conference.
7.  **State Management:** The bridge state transitions from `Idle` -> `Securing` -> `Merged` (or `Failed` if a timeout or error occurs).

---

## 3. Low-Level Code and Libraries

The application is structured into two main modules to ensure clean architecture:
*   `app`: Contains all Jetpack Compose UI code, ViewModels, and overall application setup.
*   `telecom-core`: Contains the headless telecom logic (`InCallService`, `CallBridgeController`, `CallPlacer`).

### Key Libraries and Frameworks
*   **Language:** Kotlin (JVM Target 17).
*   **UI Toolkit:** Jetpack Compose (Material 3). It utilizes state hoisting and declarative UI for building the dialer interface dynamically.
*   **Concurrency:** Kotlin Coroutines (`kotlinx.coroutines`). Almost all asynchronous tasks, timeouts (`withTimeoutOrNull`), and delays are managed via Coroutines.
*   **State Management:** Kotlin `Flow` and `StateFlow`.
*   **Android APIs:**
    *   `android.telecom.InCallService`: The core service that allows the app to act as the default dialer.
    *   `android.telecom.TelecomManager`: Used to place outgoing calls.
    *   `android.telecom.Call`: Represents an ongoing call, providing methods to answer, reject, hold, conference, and play DTMF tones.

### Deep Dive into the Architecture

#### `SpoofGuardInCallService.kt`
This is the entry point for all telecom events. It extends `InCallService`. When a call is added (`onCallAdded`), it registers a callback and forwards the `Call` object to a centralized registry. It is also responsible for posting the high-priority Incoming Call notification channel using a `PendingIntent` with `setFullScreenIntent`.

#### `TelecomCallRegistry.kt`
A singleton object acting as the single source of truth for active calls. It exposes a `StateFlow<List<Call>>`. This reactive pattern allows any part of the app (like the UI or the Bridge Controller) to observe call state changes seamlessly without tight coupling to the `InCallService`.

#### `CallViewModel.kt`
The central ViewModel managing the in-call UI state. It heavily utilizes `flatMapLatest` and `stateIn` to transform the raw `Call` state from the `TelecomCallRegistry` into consumable UI states (e.g., `hasCall`, `callState`, `phoneNumber`, `callerName`).
*   It handles UI actions like `answer()`, `reject()`, `hangUp()`, `toggleHold()`.
*   It delegates the bridging logic to an instance of `CallBridgeController`.

#### `CallBridgeController.kt`
A dedicated class managing the complex state machine of call merging. Because Android's telecom APIs are callback-heavy, this controller uses Coroutines and `CompletableDeferred` to convert asynchronous telecom callbacks into sequential, easy-to-read, and non-blocking suspending functions (e.g., `waitForActive`, `waitForParent`).

#### Permissions
To function, the dialer requests heavily restricted system permissions in the `AndroidManifest.xml`, including:
*   `READ_PHONE_STATE`, `CALL_PHONE`, `ANSWER_PHONE_CALLS`
*   `READ_CALL_LOG`, `WRITE_CALL_LOG`, `READ_CONTACTS`
*   `BIND_INCALL_SERVICE` (System-level permission for the `InCallService`)
