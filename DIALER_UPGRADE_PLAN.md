# Dialer foundation upgrade

Planned before implementation, 2026-10-06.

## Scope and constraints

Improve the everyday voice dialer and its accessibility, lifecycle handling, and integrations. Keep the Exotel bridge algorithm, identifier, number, security preferences, and security state meanings unchanged. Work directly in this checkout. A build or emulator test is not evidence of carrier, locked-device, Bluetooth, or wired-headset acceptance on the user's phone.

## Findings and implementation order

1. **External dialing and navigation.** Consume `ACTION_DIAL` and browsable `tel:` links on cold and warm starts; prefill without automatically calling; preserve edits and tabs through rotation. Give ongoing calls a return banner. Use a dedicated, non-exported in-call activity so answering and talking work over the lock screen without exposing contacts or history afterward.
2. **Telecom lifecycle.** Observe immutable snapshots of states, details, parents, children and conferenceable calls. Select ringing/outgoing/active calls intentionally. Add call waiting, swap, add call, normal conference merge/participant controls, carrier post-dial wait confirmation, real connection-based elapsed time, disconnect feedback and notification actions scoped to a specific call.
3. **Audio and call notifications.** Android 14+ endpoint selection with older supported-route fallback. Show actual selected route and connected devices; react to connect/disconnect and mute callbacks. Add proximity handling for handset audio. Incoming, ongoing and missed-call notifications; answer/decline/hang-up; full-screen access recovery.
4. **Keypad.** Editable/selectable/pastable phone number, long-press zero for `+`, long-press one for voicemail, pause/wait insertion, clear-all, last-number recall, T9/contact matching, real phone-account selection, touch feedback, and visible placement failures. Emergency routing remains owned by Android Telecom.
5. **Contacts and history.** Permission-safe background queries, content observers and resume refresh, empty/loading/error states, favorites, contact/message/copy/edit-number actions, missed-call filtering, search, date/duration details, confirmed history deletion, and system blocked-number management.
6. **Polish and setup.** Consistent Material interactions, meaningful accessibility labels, scalable/scrollable layouts, stable list keys, screen state restoration, useful settings for notifications/full-screen calls, dialpad feedback and system calling settings.
7. **Verification.** Build, unit tests for routing/selection/number handling, Android lint, and emulator interaction checks (external intents, rotation, permission denial and simulated calls where supported). Document a physical-device test matrix and deliver an installable debug APK.

## Deliberate boundaries

Visual voicemail requires a carrier integration; voicemail access will use the configured carrier mailbox. Carrier video/RTT and recording are separate capabilities and are not represented as implemented by this voice-dialer pass. Existing Secure behavior is preserved, including its known limitations. No real calls, messages, contact writes, history deletion, or number blocking will be performed as automated checks on the connected personal phone.

## References

- Android InCallService: https://developer.android.com/reference/android/telecom/InCallService
- Android common phone intents: https://developer.android.com/guide/components/intents-common#Phone
- Call notifications: https://developer.android.com/develop/ui/compose/notifications/call-style
- Call conferencing and call capabilities: https://developer.android.com/reference/android/telecom/Call

## Acceptance evidence

To be completed in `DIALER_VALIDATION.md` with actual results and remaining physical-device checks.
