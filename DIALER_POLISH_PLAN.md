# Dialer polish — 7 October 2026

Requested scope: handset proximity, an inline call-ended state, a new launcher icon, smooth contacts scrolling, and readable keypad contact matches. Keep the unique Secure/Exotel bridge behavior unchanged.

1. Separate the confirmed audio route from endpoint-list callback order. Arm Android's native proximity screen-off lock while dialing/talking through the earpiece, register the proximity sensor explicitly, release on speaker/headset/hold/end, and block cheek touches with a black fallback surface on unsupported devices.
2. Retain the last caller snapshot for display only. Keep the existing caller, avatar and control positions; replace the timer with “Call ended,” disable stale actions and dismiss the call task after a short pause.
3. Generate an original emerald handset/shield symbol, package adaptive and themed Android launcher assets, and inspect the launcher at small size. Match the launch screen to the app's dark background to avoid a bright startup flash.
4. Move contacts filtering/grouping off the UI thread, cache contact search keys, reuse matching row types, and defer scrubber motion to layout. Correct stale gesture callbacks and repeat-letter jumps.
5. Use full-width bordered keypad match cards with avatars and two clear text lines. Reserve space so matches do not move the keypad as digits change.

Validation: compile, focused call/proximity lifecycle tests, existing navigation/contact regression tests, large synthetic contacts scrolling and call-ended UI on an isolated Android emulator. Physical handset sensor behavior and real audio routes require a phone acceptance check.

Design/platform references consulted:
- [Phone by Google screenshots](https://play.google.com/store/apps/details?id=com.google.android.dialer&hl=en) and [Google's Phone design announcement](https://support.google.com/phoneapp/thread/351208139/a-new-material-3-expressive-experience-in-the-phone-by-google-app?hl=en): inspiration for spacing, readable caller identity and discrete rows. This is an adaptation to SENTINAL's existing dark theme.
- [AOSP dialer proximity lifecycle](https://android.googlesource.com/platform/packages/apps/Dialer/+/refs/heads/main/java/com/android/incallui/ProximitySensor.java) and [Android PowerManager](https://developer.android.com/reference/android/os/PowerManager#PROXIMITY_SCREEN_OFF_WAKE_LOCK): keep the native sensor lock armed during handset use so Android handles near/far display changes.
- [AOSP caller-card presentation](https://android.googlesource.com/platform/packages/apps/Dialer/+/b33463c7cdf3a4d481767452d3a21c09527ffcb1/java/com/android/incallui/CallCardPresenter.java): historical reference for briefly presenting a disconnected caller in the existing call layout.
- [Compose performance guidance](https://developer.android.com/develop/ui/compose/performance/bestpractices): background preparation, stable item keys and deferred state reads.
