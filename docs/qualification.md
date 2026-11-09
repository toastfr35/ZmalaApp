# Zmala Phone qualification record

This record separates executed checks from planned qualification. Historical
observations apply to the versions and devices named below; they do not establish
that later changes or every Android phone have passed those checks. Keep private
enrollments, account identifiers and raw captures outside the repository.

## Checks on 9 November 2026

For 0.4.5 / 118, [PR #19](https://github.com/toastfr35/ZmalaApp/pull/19) passed
36 JVM tests and 17 combined emulator tests. Four action-size tests also passed
at 100% and 200% font scale in English and French. French enlarged-text screenshots
were inspected. The checks measured clickable footer, photo and dialog actions
against the 60 dp minimum and exercised setup, contact and update actions.

For 0.4.6 / 119, [PR #21](https://github.com/toastfr35/ZmalaApp/pull/21) passed
36 JVM tests and 11 emulator photo tests. These covered unchanged/expired polling,
blocked replies, call end, account replacement, image limits and cancelled reads.
The native fixture call retained its call/audio state through sharing and activity
recreation. Both changes passed Android lint, requirements validation and GitHub Verify.

These emulator runs do not establish physical speech or moving-video quality,
paired-device photo sharing, FCM/Doze reliability, cross-network interoperability,
the actual system picker provider or signed self-update behavior on target phones.
The [requirements review](../requirements/reviews/2026-11-09.md) records the
remaining clause-coverage and hardware gaps. See the [development guide](general.md)
for reproducing local checks; no hardware HLT is marked passed merely because a
test plan or debug build exists.

## Historical release checks

Development versions were exercised on a OnePlus KB2003/Android 14 and a POCO F1/
Android 10, including QR enrollment, permissions, foreground calls, camera switching,
rotation, native incoming-call notifications and secure relayed media. MIUI required
on-device APK installation. This is a bounded device sample, not universal OEM
compatibility. Private device identities, enrollments and captures are not published.

The 0.3.6 changes address deferred notification answering and cooperate with the Pi's
first-picture keyframe retries. A C20 USB timeout was recovered operationally by
cycling its power; its underlying trigger remains undetermined. Four Pi-to-phone
calls passed with remote video on both screens: two process-absent push starts and
two foreground starts. Six further calls alternated phone-to-Pi and Pi-to-phone
three times without restarts, all displaying remote video at both ends. Native
samples showed relay ICE, increasing received packets and nonzero phone decode
rates. Rates varied approximately 4–34 fps in the alternating run; neither uniform
smoothness, exact startup latency nor audible speech is inferred from those samples.
Temporary answering controllers were stopped and diagnostic logging disabled.

Nine portable Android tests, lint, two emulator notification checks, the focused Pi
native filter regression and requirement validation passed for the 0.3.6 changes.
The new native first-picture regression also failed against the old implementation.
Hardware observations do not mark unexecuted HLT plans passed. The signed 0.3.6
release also passed APK signature, alignment and manifest checks. Seven emulator
checks against the non-debuggable release covered scanner launch, enrollment/call
flow, native call-engine ownership/camera switching and notifications. The test APK
was signed privately with the same release identity for instrumentation; it is not
a distribution artifact. Release lint reported zero errors and six existing
warnings. Packaging checks rejected missing signing and missing Firebase inputs.
Physical call qualification used the same 0.3.6 sources in the development variant;
the release certificate was not installed over existing phones' debug enrollment.

For 0.3.7, portable tests, release lint and eight emulator instrumentation cases
passed, covering native calls, notices, callback submission, the contact-only version
and invalid installer inputs. A separate emulator run performed a real same-key
PackageInstaller self-update using a private higher-version fixture: APK transfer
was refused during an outgoing native call, then automatic installation completed
after hangup and retained enrollment. Only the HTTPS transport was replaced with
fixture metadata and bytes; this does not qualify GitHub availability or older OEM
approval behavior. The OnePlus was migrated to the signed release with enrollment
preserved, media/notification permissions restored and the contact screen checked.
The Pi's App callback API passed a deployed capability check. Cross-device callback
delivery and the complete physical update matrix remain hardware plans, not passed
runs. Nine focused server callback/API scenarios and requirements validation passed.


## Call controls check on 25 October 2026

On the connected OnePlus and term2, the native output changed from loudspeaker
to earpiece to muted and back while microphone mute remained independent.
The existing Android call-volume setting was 9 of 9; a downward video swipe
changed it to 7 and an upward swipe restored 9. This does not establish an
acoustic loudness improvement: the original low-volume report still needs a
human listening comparison.

The original preview reproduced a 0 by 0 Java capture size while the native
camera supplied 1280 by 720 and the sent portrait stream was 720 by 1280.
After the bridge correction, the corner surface followed 480 by 640 portrait,
640 by 480 landscape and back, then 720 by 1280 after camera changes, using a
216 by 384 pixel corner surface for that widescreen portrait image. Both camera
switches retained the call. Ten targeted emulator native/display scenarios also
passed, covering icon states, independent mute, volume gestures/bounds, preview
size propagation, pixel scaling, timed controls and call/rotation ownership.

## UI and live-call check on 26 October 2026

The signed 0.3.17 candidate was installed over 0.3.16 on the OnePlus 8T running
Android 14 without replacing its enrollment. Real contact cards, the person page,
offline Call-disabled behavior, caller portrait and separate answer/decline actions
were inspected. The disposable emulator separately covered request/cancel behavior,
French app-locale switching, enlarged fonts and permission failures.

Initial live calls exposed a terminal renewal-dispatch fault: its poll controller
ignored fresh relay credentials at an unchanged configuration revision. After the
backed-up terminal fix, phone-to-terminal and terminal-to-phone calls displayed
video on both devices. The handset logged first decoded frames 2.611 and 2.285
seconds after answer respectively. The reverse call began with the app behind Home
and was opened through its Firebase notification.

During an answered call, Home retained the foreground call service and paused the
phone camera; returning through the ongoing notification restored both video views.
Hangup removed the foreground service. These observations do not qualify audible
speech, cellular relay connectivity, deep Doze or vendor battery restrictions.

## Callback notification check on 27 October 2026

Verification on 27 October 2026: JVM state/proof tests, server lifecycle/HTTP tests
and three emulator notification tests passed. The OnePlus was updated in place to
signed 0.3.19. Real FCM delivery displayed the requester, tapping opened that
contact without dialing, and cancelling the test request removed the reminder.
Foreground and Home-screen cases passed; emulator fixtures also covered stale
snapshots, deadline changes, enrollment isolation and ordinary call-alert regression.
These checks do not qualify every vendor battery policy or deep Doze behavior.


