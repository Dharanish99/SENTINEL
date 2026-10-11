# Project overview

Reviewed the original app and telecom-core Kotlin sources, manifests, resources and build configuration before planning the dialer upgrade. The project uses Kotlin, Jetpack Compose/Material 3, Android Telecom, and the system contacts/call-log providers. It targets Android 16 (API 36), with Android 10 (API 29) as its minimum.

## Runtime responsibilities

| Area | Responsibility |
| --- | --- |
| `MainActivity`, `PhoneIntents`, `SentinalApp` | Default-phone-app setup, permissions, external phone links and the four main tabs. |
| `InCallActivity` | A separate call-only task that can appear above keyguard. Personal app screens require unlocking. |
| `SpoofGuardInCallService` | Android-managed call callbacks, incoming/ongoing notifications, foreground status while a call is ongoing, audio endpoints, mute and proximity behavior. |
| `TelecomCallRegistry`, `CallSnapshot`, `CallSelection` | Raw calls for the existing bridge integration, plus observable immutable call details for the dialer UI. |
| `CallViewModel`, incoming/in-call screens | Answer/reject/end, real connected-time duration, hold/resume, DTMF, call waiting, add/swap/merge and supported conference participant actions. |
| `CallPlacer`, `DialString` | Number normalization and validation, phone-account selection, carrier voicemail and placing calls through Telecom. Android owns emergency routing. |
| Contacts and recents view models | Background provider queries, permission guards, content observers, filtering and user-confirmed call-log deletion. |
| `NumberActionsSheet`, `PhoneActions` | Contact/message/copy/edit-number actions, local favorites and system number blocking. Message composition stays in the user's messaging app. |
| `MissedCallReceiver`, `MissedCallNotifications` | Telecom's missed-call count, a single missed-call notification, callback prefill and dismissal/read handling. |
| `DialerPreferences` | Local keypad feedback, favorites, speed dial and last-number recall. |
| Theme/components | Existing SENTINAL visual identity with consistent controls, readable system bars, accessible labels and adaptable layouts. |

The legacy `ui/calllog/CallLogScreen.kt` is not the main Recents tab. The files under `ui/calling` currently declare the `ui.calllog` package; this naming existed before the upgrade.

## Secure feature boundary

`CallBridgeController.kt` and `SecuritySettings.kt` are unchanged. The bridge number, DTMF identifier, hold/dial/identify/merge algorithm, preference defaults and existing security state meanings were preserved. Shared call screens and the registry were improved for normal dialing. Secure/Exotel calls were not exercised during validation.

The existing bridge is still a prototype integration: its state describes the bridge lifecycle, and it does not provide a verified spoof-detection verdict to the UI. Its existing fixed configuration and cancellation limitations belong to a future Secure-feature pass. This dialer upgrade does not certify that feature.

## Data and platform integration

Contacts and call history remain in Android's providers. Favorites and speed dial are app-local preferences. Contact editing uses the installed contacts application; composing messages uses the installed messaging application. Blocking uses Android's blocked-number provider, with confirmation and an emergency-number exclusion. Backup and device-transfer rules exclude app data, consistent with the existing disabled-backup setting.

See `DIALER_UPGRADE_PLAN.md` for the plan created before implementation and `DIALER_VALIDATION.md` for measured results and the phone acceptance checklist.
