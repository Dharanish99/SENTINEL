# Dialer upgrade validation

Validated on 2026-10-07. Changes are in the existing project folder. The debug APK is `app/build/outputs/apk/debug/app-debug.apk`.

## Completed checks

| Check | Result |
| --- | --- |
| Debug APK and Android test APK | Built successfully. |
| Unit tests | 24 passed: 14 dialer behavior tests, 8 call-presentation/proximity/audio-selection tests and 2 existing example tests. |
| Android intent tests | 7 passed: empty dial intents, international prefixes, browser links, extensions, service codes and unsupported intents/schemes. |
| Android navigation tests | 3 passed: warm external links, tab/rotation state, long-press plus, clear and delete. |
| Contacts flow test | Passed with a synthetic contact: number matching, contact actions, favorites, and replacing a partial keypad number with the contact's number before calling. The synthetic contact was removed afterward. |
| Android lint | Passed with 0 errors, 47 warnings and 1 hint. Remaining findings primarily concern available dependency/SDK updates, existing resources and API/style suggestions. |
| Incoming notification | Simulated incoming call reached the app. Answering the notification opened the call screen and Telecom entered ACTIVE. |
| Outgoing placement and returning to a call | Keypad Call started a simulated outgoing call. The return banner restored the call with its elapsed time preserved. |
| Ongoing notification and cleanup | Confirmed a phone-call foreground service and ongoing CallStyle notification. Notification Hang Up ended the call; Telecom became empty and the call notification/foreground service were removed. |
| Hold/resume and DTMF | Simulated call entered ON_HOLD and resumed. Keypad digits were sent/displayed. This does not verify a real carrier IVR. |
| Locked incoming call | Full-screen incoming UI appeared over a PIN-locked emulator. Sliding to answer entered ACTIVE while keyguard remained locked and occluded. |
| Personal-screen unlock gate | Add call requested the emulator's PIN instead of exposing the keypad/contact/history screens. |
| End call while locked | Call UI closed and keyguard remained showing, with occlusion removed. The temporary emulator PIN was then cleared. |
| Missed-call integration | One app notification after a simulated missed call. Multiple missed calls updated the same notification/count. Opening it showed Recents and cleared the alert. |
| Missed-call callback | Notification Call back prefilled the correct normalized number; Telecom still had no call until Call was pressed. |
| Permission denial | Revoked Contacts and Call log permissions on the emulator. Keypad continued to work; each affected tab showed its recovery prompt without a crash. |
| Secure feature boundary | Git diff confirmed `CallBridgeController.kt` and `SecuritySettings.kt` unchanged. No Secure/Exotel calls were initiated. |

Tests used an isolated Android 16/API 36 x86_64 emulator, serial `emulator-5560`. No app installation, calls, contact writes, messages, call-history changes, or permission grants were performed on the personal phone.

The emulator exposed Speaker as its audio endpoint. Endpoint enumeration and the picker were inspected; it cannot validate physical earpiece, Bluetooth or wired audio. A second simultaneous emulator GSM incoming call was not delivered by this image, so call-waiting/swap/conference acceptance remains on the phone checklist. Call-selection priorities have unit-test coverage.

## Requested polish

- Proximity now uses a dedicated sensor controller and Android's native proximity screen-off lock. Audio-route confirmation is independent of available-device callback ordering. Dialing/talking through the handset arms it; speaker, Bluetooth, wired headset, hold and call end release it. A black touch-blocking surface also protects the in-call UI when a device supplies sensor readings without supporting the native display lock.
- Ended calls retain the caller identity, avatar and existing control layout. The timer changes to “Call ended,” stale controls disable, and the call task dismisses after approximately 2.2 seconds. Presentation-only ended data never becomes an actionable call target.
- A new generated emerald handset/shield symbol is packaged as adaptive, themed and legacy launcher assets. The startup background matches the dark app theme. Master art, preview and the built-in generation prompt are in `design/app-icon`.
- Contact filtering/grouping runs off the UI thread, search keys are cached and lazy rows have separate reusable content types. Alphabet dragging reads continuous motion during layout; current gesture callbacks and gesture cleanup make repeated letter jumps work after a fling.
- Keypad matches use larger, full-width bordered cards with avatars, 16sp names and 14sp numbers. Space for two matches remains reserved as digits change, and tapping a match still prefills the number.

Additional checks:

| Check | Result |
| --- | --- |
| Final Android regression run | 13 passed together on the final APK: 7 intent, 3 navigation, 1 contacts-flow and 2 polish tests. The separate proximity test also passed, for 14 Android checks in total. |
| Call-ended UI | Passed on an emulated outgoing call: caller bounds unchanged, controls retained and disabled, followed by call-task dismissal. |
| Large contact list | Passed with 520 synthetic contacts: flings, Z/A jumps and repeating A after manually scrolling. Test contacts were removed afterward. |
| Sensor/display integration | Passed with emulator proximity injections: NEAR (0) caused the native display state OFF; FAR (5) restored ON. This exercises the controller directly; handset-route lifecycle eligibility is covered by the focused unit tests. |
| Icon/UI inspection | Launcher, call-ended layout and contact list screenshots inspected. Keypad match and dark startup inspection recorded in local QA evidence. |

These functional scrolling checks do not certify a frame rate on a physical phone. The emulator uses software graphics and a debug build. In particular, verify near/far behavior on your phone during both dialing and an answered call, then switch speaker/headset and end the call while covering the sensor. OEM virtual sensors and real headset routing remain phone acceptance checks. Design/platform references and execution scope are in `DIALER_POLISH_PLAN.md`.

Latest evidence: `.local/build-polish-final.log` (successful APK/test APK/lint build), `.local/polish-final-instrumentation.log` (13 passing tests), `.local/proximity-qa.log` (native NEAR/FAR display test), and screenshots `qa-keypad-match.png`, `qa-call-ended.png`, `qa-contacts-before-fling.png`, `qa-contacts-after-fling.png`, `polish-dark-startup.png` and `polish-final-launcher.png`. The old failed QA logs record accessibility-cache/selector issues corrected in the test harness; use the final logs for acceptance results.

## Issues found and fixed during validation

- Empty carrier caller names crashed Android's CallStyle notification. Nonblank fallbacks and a regression test now cover this.
- Separate app/service missed-call alerts duplicated Android's notification. The dialer now handles Telecom's protected missed-call broadcast and unread count.
- Ongoing CallStyle notifications lacked the required foreground association. Ongoing calls now use the declared phone-call foreground service, with cleanup at call end and an ordinary actionable notification fallback if foreground promotion is refused.
- Pending audio requests could leave controls stuck. Requests now have timeout/error recovery and handle endpoint disconnection.
- Contact action sheets could remain partly expanded after Back. They now open fully and scroll on small screens or larger fonts.
- Selected tabs used inconsistent theme colors; system icons lacked contrast in initial frames. Explicit dialer colors and dark system-bar styling were applied.

## Run on your phone

1. Open this project in Android Studio and run the `app` configuration on your phone, or install the debug APK above.
2. Select SENTINAL/SpoofGuardDialer as your default phone app. Allow Phone permission. Contacts, call history and notifications enable their corresponding features.
3. In the app's Dialer settings, check call notifications and full-screen incoming-call access. The app provides links to Android's settings for these.
4. Use the following checks with someone you can call. Keep Secure disabled for this dialer acceptance pass.

| Phone check | Expected result |
| --- | --- |
| WhatsApp/browser/contact `tel:` link, with the app closed and already open | Correct number appears on the keypad; a call starts only after pressing Call. Some apps place calls directly through Android Telecom; those should also use this dialer's call UI when it is the default. |
| Incoming call with screen off and a secure lock enabled | Caller UI appears, answer/decline works, conversation stays on the call screen, and hang-up returns to the locked phone. |
| Phone, speaker, Bluetooth and wired headset | Available routes follow connections/disconnections; the selected route matches where audio is heard. Check connecting and removing a device during a call. |
| Mute, handset proximity and hardware volume buttons | Mute affects the microphone; handset use blanks the screen near the ear; speaker/headset use does not unnecessarily blank it. |
| Dual SIM/default calling account | System preference and explicitly chosen SIM work; account labels are accurate. |
| Voicemail, pause/wait digits, IVR | Hold 1 reaches the configured mailbox; pauses/waits and DTMF digits work with your carrier. |
| Call waiting, hold, swap and ordinary conference | Incoming call takes focus; supported actions work; participants/merge controls appear only when the carrier supplies those capabilities. |
| Background call and notification actions | Return-to-call banner/ongoing notification restores the call; notification end/decline affects that call. |
| Missed calls, history actions and blocking | Correct missed count, callback prefill, search/filter/details, confirmed deletion, and reversible blocking. |
| Larger text, landscape, rotation, process/background transitions | Controls remain reachable; dialed number and relevant screen state persist. |

Android 10–15 devices, OEM background-call restrictions and real carrier behavior are not certified by the API 36 emulator run. Visual voicemail, carrier video/RTT and call recording are outside this voice-dialer pass. This is a tested debug build for phone acceptance, not a signed production release or a claim that every carrier capability is supported.

## Reproducing automated checks

From the project root:

```powershell
.\gradlew.bat :app:assembleDebug :telecom-core:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug
```

Run `PhoneIntentTest` and `DialerNavigationTest` on a test emulator with this app selected as default dialer. `ContactsFlowTest` creates and removes a synthetic contact and is intended for a disposable test emulator.

This host required a process-local JVM workaround for a Windows Unix-domain socket error: `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:\nonexistent-spoofguard-socket-directory`, using the installed Java 21 toolchain. The named directory must remain nonexistent so the JVM falls back to TCP. No global Java or Android Studio configuration was changed.

Local evidence is in the ignored `.local` folder: build logs, instrumentation output, emulator screenshots, UI hierarchy captures, and Telecom/keyguard/notification state dumps. Build reports are under `app/build/reports` and `telecom-core/build/reports`.

Platform references: [InCallService](https://developer.android.com/reference/android/telecom/InCallService), [phone intents](https://developer.android.com/guide/components/intents-common#Phone), [call notifications](https://developer.android.com/develop/ui/compose/notifications/call-style), [phone-call foreground services](https://developer.android.com/develop/background-work/services/fgs/service-types#phone-call), and [AOSP missed-call delegation](https://android.googlesource.com/platform/packages/services/Telecomm/+/master/src/com/android/server/telecom/ui/MissedCallNotifierImpl.java).
