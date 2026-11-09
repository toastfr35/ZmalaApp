# Zmala Phone implementation guide

Zmala Phone is the Android companion in `zmala_phone/`, package `org.zmala.phone`.

The implementation supports encrypted calls through the server's TLS/TURN relay from
separate NAT networks, alongside LAN mode and Firebase incoming-call alerts while
the activity is closed. Remote mode needs a publicly reachable, configured secure
deployment and HTTPS enrollment. Answered/user-started calls retain audio while the screen is hidden;
missed-call and callback indicators are shown in the contact list.

Normative scope and verification plans live in `requirements/android/`; this
file describes the implementation and its current limitations. See the
[dated qualification record](qualification.md) for executed checks and remaining gaps.
User-facing text is in English and French Android resources, following phone language
or Android's per-app language selection. `UiText` translates stable engine status keys;
translated words do not drive call state. Plural counts use Android quantity resources.
Setup, contact actions, incoming calls and update explanations scroll for enlarged text.
Call controls keep the five-second default, extend it through Android's recommended
accessibility timeout, and stay visible while focused or touch exploration is active.

Notification footer actions, photo Share/Cancel/Stop and app-created dialog buttons use
the same 60 dp minimum on the clickable view itself. Text buttons wrap and grow with
larger text. `UiActions` also applies this policy to the update preference switch;
Android permission, picker and installer screens retain their system layouts.

Contents: [setup and calls](#account-setup), [updates and notices](#app-updates-and-contact-notices),
[source ownership](#sources-and-ownership), [build and verification](#build-and-verification),
[incoming calls](#firebase-incoming-calls), [release builds](#signed-release-builds),
[callback notifications](#callback-request-notifications), [photo sharing](#sharing-a-photo-during-a-call).

Configuration is validated by `Enrollment` before `CallEngine` or the UI uses it.
Versions are JSON integers, and explicit URL ports are in 1–65535. Contacts have
unique account IDs, names of at most 80 characters, matching SIP addresses and
Boolean call/receive permissions. Portraits are omitted, empty strings or local
hash-named paths. Invalid responses fail as a whole; strings such as `"true"`
never grant call permission. `ServerApi` owns authenticated HTTP connections and
`HttpPolicy` supplies timeouts, cancellation and bounded reads.

## Account setup

The administrator creates an **App** account, adds it to a calling group, and
downloads the server QR PDF from its connection dialog. They give the phone user
the QR by email or on paper. The phone user does not need server admin access.

Install the supplied signed release APK, tap **Scan setup QR**, allow camera and microphone access (also local network
access on Android 17), scan the code and confirm the displayed server address.

An HTTP enrollment explicitly warns that LAN setup and calls are unencrypted.

To set up directly from an email attachment, save the setup PDF or a QR image on
the phone and tap **Open QR file**. Choose the file and confirm the displayed server
address. The app reads the selected document locally and looks for the App setup
QR in the first ten PDF pages. It accepts files up to 20 MiB and skips the APK
download QR. Cancelling the picker or the confirmation leaves setup unchanged.

To remove the saved account, touch and hold the Zmala Phone launcher icon and
choose **Forget setup**. Confirm to end any current call and return to QR setup.
The shortcut clears the account from this phone and keeps general app preferences.

LAN mode needs a mutually reachable trusted network. Secure relay mode supports
separate networks through the administrator's public Zmala server.

Allow notifications when prompted to receive calls while using another app or
when the phone is asleep. The phone and Pi need internet access for Firebase.

If notifications are denied, enable them in Android Settings > Apps > Zmala Phone > Notifications, then reopen Zmala Phone. Android Force stop blocks delivery until
the app is opened again; swiping it from Recents is not the same operation.

A blue App contact may be reachable through push even when its activity is closed.

An account has exactly one type, App or Terminal. The two types have separate
device identities and enrollment tokens. A Terminal QR is rejected by the app;
an app token cannot read Pi configuration or use terminal management APIs.

Switching types rotates the device identity, bearer token and SIP password.

Print a fresh QR after conversion. This does not turn one account into two devices.

One account still supports one active SIP registration and call.

The contact view uses server group permissions, account names and portraits.

Each contact is one tappable card containing a rounded 64 dp portrait, name,
availability and readable missed/callback notices. Cards have an 80 dp minimum height.
Green Available means recently active; blue Try calling means unconfirmed push eligibility;
red Offline means neither. All cards open a dedicated `ContactScreen`, including offline
contacts. There are no separate row callback buttons. The page offers Call (enabled only
when registered and the peer is callable), Request callback or Cancel request, and Clear
missed calls when needed. Back or Contacts returns to the list. Long names and notices wrap.

The contact screen omits the phone account name and routine ready-to-call status;
connection errors remain visible.

Long names may wrap and increase row height rather than truncate.

Short lists are vertically centered between the header and contact footer;
longer lists start at the top and scroll. The contact ScrollView fills its viewport
and its row container centers its children when extra height is available.

During outgoing and connected calls, video replaces the logo banner and contact
name. The local camera preview sits at bottom right. Tap the video to reveal one
row of four round icons: microphone, listening output, switch camera and hangup.

The microphone control mutes only outgoing speech. The listening control cycles
loudspeaker (sound waves), earpiece (phone icon), and received sound muted
(crossed speaker), skipping an unavailable earpiece. Each control has a state/action
accessibility label; brief text feedback confirms changes. A single vertical swipe
on the video changes Android's voice-call stream by one step per 32 dp; horizontal,
cancelled and multi-finger gestures do not trigger volume changes or video taps.
A percentage appears briefly, and changing volume does not unmute either control.
Hardware volume keys target the call stream while a call owns audio focus and return
to Android's default stream selection afterward.

New calls select the built-in loudspeaker without changing Android's stored volume.
`CallEngine` sets both the future-call default and current-call output device and
reapplies the chosen route/mute at media startup. Previously, routing only the
idle core left the next-call default unspecified. This could explain a quiet call
using the earpiece despite the loudspeaker icon. The routing defect is corrected;
other causes of low acoustic level still need physical listening checks.

The controls start hidden and normally disappear after five seconds without interaction,
subject to the accessibility timeout and focus behavior described above;
short Mic, Sound, Camera and End labels appear with them. The first call briefly
explains tapping and volume swipes. A compact status remains visible when microphone
or received sound is muted, independently of the auto-hiding controls.

Using a control restarts that timer. The preview sits above the visible row and
returns to the corner when it hides. The call view adapts to portrait and landscape
without replacing the call or video surfaces, and sends display rotation to the
native camera engine. Physical remote-video orientation still needs qualification.

A pulsing phone indicator shows Calling or Ringing until the call connects.

After answer, a spinner and “Connecting video…” remain until the current call's
first decoded remote frame. After fifteen seconds without a frame, a calm delayed-video
message replaces the spinner; a later frame clears it and the call remains connected.
Transient HTTP configuration outages show a reconnecting notice and preserve an active
call; revoked access or invalid configuration still release it. The progress container spans the available width with
24 dp side margins so its label is not clipped to the spinner's width. `VideoStartup` re-arms the SDK's one-shot frame
notification during initial stream restarts and ignores stale call callbacks.

It logs only the monotonic answer-to-frame duration under `ZmalaMedia`; no account,
address or media content is included. This measures decoder delivery, not display scanout.

The engine explicitly enters foreground after start: it is created asynchronously
after the activity has resumed, so the SDK lifecycle observer otherwise misses
that transition and eventually drops to its 500 ms background iteration timer.

The foreground timer runs at 20 ms. A foreground call service retains that cadence while an active call is hidden; idle closure releases the core.

Incoming calls retain the caller name and answer/decline actions before answering.
`IncomingCallScreen` centres a rounded 132 dp caller portrait, name and incoming-call
label above separate 64 dp minimum answer/decline actions. Its scroll container preserves
access with enlarged text. Portrait loading is asynchronous and scoped to that view;
missing images keep the common logo.

Call and answer controls are green; decline and hangup are red. The dark palette
is defined by the app’s `MainActivity` palette and shared screen styles. The shared transparent A1 mark
appears in the app header and as the portrait fallback. The adaptive launcher
icon uses the compact A3 dark mark. See `docs/branding/` for the artwork sources.
Use the launcher’s **Forget setup** shortcut to remove enrollment while keeping general
preferences. Android Settings > Apps > Zmala Phone > Storage > Clear storage is a full
local reset instead: it also removes preferences and stops the app. Neither action
deletes the server account; obtain the appropriate App QR before reconnecting.

There is no reset action within the app. There is no manual SIP editor or public Linphone account assistant.

## App updates and contact notices

The contact screen alone displays the installed version and an Updates action.
Its compact footer distinguishes server support, disabled notifications, registration
progress and enabled/unavailable alerts. Disabled notifications open app or channel
settings as appropriate; failed registration offers retry and missing server support
explains how the person who supplied the QR can help. Setup does not show this footer.
`AppUpdates` checks the fixed public GitHub latest-release API at most every six
hours on opening; **Check now** can bypass that interval while call admission allows it.
`UpdateJob` schedules a daily network-constrained metadata check.
No server bearer token, account identity or GitHub token goes to GitHub. Only
stable newer releases with the exact uploaded APK asset and SHA256 digest qualify.
The exact release asset must fit the 512 MiB limit. Downloads use bounded HTTPS redirects to GitHub asset hosts and private cache
storage, with length, digest, package, version, debug-flag and signer checks.
Android PackageInstaller performs final APK signature validation.
New release discovery changes the footer action to **Update available** without opening
a dialog. A selected update shows a percentage during transfer, then checking, preparing
and installation stages. User interaction restarts the automatic-update idle timer;
a selected person's action page is not considered idle for installation.

Automatic updates default off and are offered on Android 12+. They require the
user's install-apps permission, an unmetered connection and 30 seconds with the
app visible and idle. These conditions are checked again before installation commit,
so activity during download/staging starts a fresh idle period. Android 9-11 uses the Update button and system confirmation.
Android 12+ may also require confirmation; an automatic attempt then falls back
to manual Update without repeatedly launching approval. Later defers reminders
and automatic work for 24 hours. Background jobs check metadata only; APK transfer
and installation require the visible app. Android scheduling and Force stop can
delay checks. Interrupted transfers delete the partial APK and start again on retry.
The first updater-enabled release still needs a manual installation.
Debug builds cannot update to the differently signed public release.

`UpdateGate` serializes native call admission, verified push rings and installation
commit. Ringing and calls prevent commit and cancel transfer/staging at chunk
boundaries; backgrounding cancels it too. Once committed, installation briefly
refuses new calls until Android reports completion/cancellation. Session identity
is persisted for recovery; stale staging sessions and failed downloads are removed.
A completed update retains enrollment. Neither update prompts nor version labels
appear over a call.

`NoticeBook` deduplicates native/push incoming calls by call ID, retains up to 128
recent observations and 128 contact counts capped at 99, and settles unacknowledged
rings at expiry. `CallNotices` saves this per-enrollment state in private app
preferences. Answered/declined calls are not missed; connected calls clear the
peer's count. Contact cards state missed counts, Please call back for incoming
requests and Callback requested for sent requests; simultaneous requests retain both
labels. The contact page offers request/cancel actions and missed-call clearing. Unanswered outgoing calls also offer a callback. Badges are derived
from the current allowed directory and the server's pending callback snapshot.

The server advertises `callbacks_enabled` and returns `callbacks` in app config.
The contact screen requests portrait orientation; the call screen restores normal
display rotation, and hangup returns to portrait.

A pending outgoing callback changes Request callback to Cancel request on the
contact page. Its action sends `DELETE /api/app/callbacks/:id`, then refreshes the server snapshot.
Failure keeps the pending state and reports an error. The contact card always opens that person's page with notice details and missed-call clearing. Cancellation does not place or end a call.

`POST /api/app/callbacks` accepts only a recipient `to`; authenticated App identity
is the requester. Shared server callbacks deduplicate, expire and complete on
connection for App and Terminal accounts. Old servers omit the capability, so
the action stays hidden. App callbacks need a server upgrade as well as the APK.

### Contact presence colours

Contacts distinguish confirmed activity from unconfirmed push delivery: **Available**
(green) means a current SIP registration remains reachable under the server's
five-second checks; **Try calling** (blue) means only push eligibility remains;
**Offline** (red) means neither. Both green and blue permit an attempted call.
A push registration is not proof that the app is installed, online or ready to answer.
The API adds `presence` (`available`, `push`, `offline`) to full, delta and preflight
responses; `online` remains true for both callable states for older clients.
Terminal live information expires after six seconds without a successful refresh,
and cached configurations discard both live fields. Android retains the last validated
directory during a temporary configuration failure, shows reconnect help and disables
new calls until its registration returns. Revoked or invalid configuration clears access.
Contact cards show a smaller status line;
terminal cards show the status with a check, question mark or cross. New terminal
labels are translated in English and French, with English fallback elsewhere.

## Sources and ownership

`CallSession` owns live audio state and exposes an immutable snapshot to the
activity. Its weak observer belongs to the current screen; an old screen cannot
detach its replacement. It supplies photo eligibility from the live call and
configuration, revalidating picker completion. Update admission remains in
`UpdateGate`, including when there is no activity.

`SetupCoordinator` owns enrollment file operations, permission policy and
generation tickets. `MainActivity` owns presentation and starts asynchronous
refresh/import work; stale results are rejected after stop or enrollment replacement.
`UiActions` applies the shared clickable-action minimum and text wrapping.

`HttpPolicy` shares timeout/no-redirect setup and bounded interruptible reads.
Each transport retains its own authentication, URL trust and error policy.

This is a small Zmala UI around the Linphone SDK, not a wholesale copy of Linphone's
messaging/account UI. Linphone Android 6.2.8, commit
`dcaa165b77ed374b7beaedc8deae3e8a9b4fd30c`, was the source reference; its Gradle
wrapper is retained. The Android SDK dependency is pinned to 5.5.32.

See `zmala_phone/NOTICE.md` for source and license provenance.

`Enrollment` validates QR and configuration identity and verifies the incoming
`X-Family-Auth` HMAC over `callId:callerUsername`. `ServerApi` sends the bearer token
only to paths on the enrolled origin, refuses redirects, applies six-second
connection/read timeouts and bounds configuration and photo response sizes.

`Enrollment.validate` checks contact objects, unique valid account IDs and nonblank string
names before the configuration reaches rendering or notice reconciliation. Calls build
their target from the validated account ID and enrolled SIP domain, rather than a contact URL.

`SetupCoordinator` stores the QR enrollment in `getNoBackupFilesDir()`; fetched SIP
and TURN credentials stay in memory. Android-derived trust roots also have a private
no-backup file for native TLS. Feature preferences, bounded notices and temporary
update state use private app storage. Backup is disabled. The app does not save SIP
passwords into Linphone’s configuration or a call-history database.

`PortraitQueue` runs best-effort portrait downloads on a separate single worker with at
most forty queued jobs. A new contact view clears obsolete queued work; leaving the
view invalidates its tickets, so an old in-flight image cannot update a newer screen.
Configuration, presence and callback requests retain their own executor and cannot
be delayed behind image downloads. Missing or discarded images keep the logo placeholder.

`PortraitLoader` retains an 8 MiB LRU cache of decoded static bitmaps, sampled to
at most 256 pixels per side. Immutable photo paths are reused across directory
refreshes; changing enrollment clears the cache. View tickets reject obsolete
results before they reach the cache or a view. Disposal clears memory and stops
queued work; no portrait disk cache is introduced.

Animated server portraits use the authenticated GIF route with a 16 MiB encoded
response cap; PNG/JPEG retain the 2 MiB cap. BitmapFactory displays a sampled static
first frame, so animations do not loop in contact or incoming-call screens.
Transport remains same-origin and rejects redirects.

`UpdateHttp` owns public update HTTP connections, bounded metadata reads and explicit
redirect checks. Metadata uses only the fixed API URL and never redirects; APK redirects
are revalidated before each connection and limited to six connections. Rejected or failed
connections are disconnected by the helper; the downloader owns a successful APK connection.
This channel receives no enrolled-server credentials. `AppUpdates` retains scheduling,
call priority, dialogs and PackageInstaller lifecycle ownership.

`CallSession` owns the engine, current validated configuration and audio focus on the
main thread. It weakly observes the current activity; screens do not own the call.
`CallService` starts from a visible Call/Answer action and declares microphone/camera
foreground-service types. Its private ongoing notification returns to the screen or
ends only the matching call ID. It is not sticky and does not recreate calls after
process death. Wake ownership is bounded/renewed while the service exists and released
with the call. Idle closure has neither a foreground service nor a SIP registration.
Repeated service starts retain one renewable wake lock rather than acquiring more.

On hiding an active call, `CallEngine.background` detaches native surfaces, disables
that call's camera through `Call.setCameraEnabled(false)` and sets the SDK background
iteration schedule to 20 ms. Core-wide capture defaults alone do not change a current
call. Return reattaches fresh surfaces and resumes the same call's camera without
another SIP offer. Background active-call configuration polling still handles revocation;
transport failures preserve the current call and show reconnection status. Audio focus
and listening/microphone state survive activity recreation. The activity bundle retains selected/callback contact IDs and the photo-picker call
ID for UI recovery; it does not save enrollment, SIP or TURN credentials.

These choices follow Android's [foreground-service declaration](https://developer.android.com/develop/background-work/services/fgs/declare)
and [visible-start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start).
Force stop, process/service termination and vendor restrictions can still end calls;
physical device/Doze/audio qualification is separate from emulator lifecycle evidence.

`CallEngine` owns the SDK on Android's main thread. It uses the supplied proxy,
Digest credentials, one call, audio/video codecs, no RTP bundle and no ICE in
LAN mode. `SecureCalls` validates encrypted relay policy and configures SIP TLS,
TURN TLS, forced ICE relay and mandatory SRTP in secure mode. Certificates and
names are verified using Android's trusted roots; the server cannot inject a CA.

The roots are exported atomically into the app's private no-backup directory and
passed as a CA file, because native TURN TLS needs a file rather than SIP's PEM-data API.

`TurnTransport` loads the native framing correction before the SDK. Its global
ELF symbol replaces the pinned SDK's fragmented TURN packet reader; the opt-in
hardware test checks that this reader actually handles relay traffic. The
same library corrects TLS writes that the SDK otherwise drops on socket
backpressure. The shared writer in `vendor/terminal-contracts/turn-write.h`
retains buffers across retries and short writes, waits in 100 ms polls, and
fails after three seconds. A failed write shuts down the connection, preventing
subsequent frames from following an incomplete TLS payload. Live tests check
the writer invocation counter as well as the reader counter.

Checksum-pinned SDK headers preserve the native class ABI. Changing the SDK
requires requalifying the correction; mismatched source or binary hashes stop the build.

Both TLS endpoints accept explicit ports so one public IP can host all services.

The native core enables AVPF feedback and NACK retransmission to recover video
packets and request fresh decoder initialization after ICE path changes.

Incoming calls must match both the directory and the server proof.

It binds the remote TextureView and the SDK CaptureTextureView for local video,
which receives actual camera dimensions and sensor/display transforms to preserve
proportions and orientation. `CameraPreview` requests layout when native capture
reports a new display-oriented image size. On the pinned SDK, in-call Camera2
size events leave the Java view at 0 by 0, even while the native stream adapts
from VGA to widescreen. `CallEngine` supplies the current call's sent-video
definition on binding and statistics updates, including rotation/camera changes.
Before child measurement, `CallScreen`
fits that ratio into a 96 by 128 dp portrait or 128 by 96 dp landscape maximum;
the surface follows actual capture proportions instead of always being a fixed
4:3 rectangle. The SDK retains ownership of sensor/display texture rotation.
Tests compare transformed raw camera pixel lengths as well as fitted bounds, so
a test cannot mistake a correctly shaped container for undistorted video. It exposes answer, hangup, mute,
speaker and camera controls. Camera switching uses `Call.update(null)` after selecting
the other camera, keeping the operation local rather than initiating a new SDP/ICE
negotiation that can defer or disrupt successive switches. `CallScreen` owns the overlay layout, original line
icons, five-second idle timer and outgoing progress animation; it cancels timers
and animation on teardown. The ringing circle and phone glyph animate their drawn
size and opacity inside a fixed 108 dp surface with at least 12 dp of clear margin;
the view itself is not scaled or faded, avoiding clipping by composited view bounds.

Rotation is handled in the existing activity through
configuration and display callbacks. On activity stop, an ongoing user-started or
answered call stays with `CallSession` and its foreground service; idle closure
unregisters and releases the core.

Secure configuration requires an HTTPS setup QR and complete, unexpired TURN
credentials. There is no plaintext retry after a TLS or relay failure. Hourly
credential changes remain pending while in a call and rebuild the core at the
next idle refresh. Expired applied credentials cannot start another call.

Server configuration polls occur every ten seconds while open.

## Build and verification

Use native Windows Android Studio and SDK for device access and emulator testing;
WSL is not needed for app development. The repository wrapper pins Gradle 9.5.1
and AGP 9.2.1. Use JDK 21 or newer compatible with these tools. Compile/target API
is 37; minimum API is 28. APKs include arm64-v8a and x86_64. The native correction
uses NDK 29.0.14206865 and Python 3. The Gradle build uses the repository virtual
environment when present, otherwise `python3`; `PYTHON` can select another interpreter.

`build_tools/android/build-turn.py` downloads checksum-pinned SDK headers and builds
the correction for both ABIs, using the AAR's own C++ runtime. Its JNI regression
helper is packaged only in the test APK.

Run `make phone test-android` from a disposable worktree under top-level `tmp/`.
Follow the [engineering guide](general.md#device-checks-and-evidence) for native
test selection. `tests/android/LLT/clear_storage.py` is an explicitly destructive
storage check and requires a selected disposable emulator.

The APK is `zmala_phone/app/build/outputs/apk/debug/app-debug.apk`. Debug signing
is for development. Do not use it as a public release. GitHub Verify runs requirements/version checks, the debug build, JVM tests, lint,
tooling tests and guide generation. Device tests run explicitly on a selected
disposable emulator. See [development setup](general.md) for workspace placement
and commands.

Keep the disposable emulator unlocked and awake for UI scenarios. Some tests replace
enrollment or clear storage; do not run the complete suite on a personal phone. No connected device
means those tests were not run.

Tests remain in the repository's top-level tree: JVM LLTs under
`tests/android/LLT/unit`, device LLTs under `tests/android/LLT/device`, and HLTs
under `tests/android/HLT`. Shared disposable HTTP and SIP fixtures live under
`tests/support/android`. They test QR-result handling, identity boundaries,
redirect rejection, contact UI and Android app-data clearing and native Digest registration, outgoing
video SDP, signed incoming admission, answer and cleanup. They do not claim
optical QR scanning, media quality or full physical Pi interoperability.

Before accepting a release, scan a real printed App QR on a phone, register with
the Pi, call both directions and check moving video, intelligible audio, mute,
earpiece/speaker and front/back camera switching. Also exercise decline, remote
hangup, unavailable contact, Wi-Fi loss, account conversion, app backgrounding,
rotation and large-font layouts. Record phone model/Android version, Pi build,
network and observed media results. A signalling fixture is not a passed media test.

## Firebase incoming calls

The optional local `app/google-services.json` is compiled into Firebase resource
values; CI fixture builds can omit it. Firebase Messaging uses BoM 35.0.2 with no
Analytics dependency. This integration uses Firebase Messaging without a Functions
or database backend. `PushService` validates an HMAC using the
private enrollment token before showing a native CallStyle notification. The
payload contains caller ID, call ID, event, expiry, device ID and proof; the caller
name is taken from a local cache, not sent through Google. SIP credentials and
media do not pass through Firebase.

`MainActivity` verifies notification answer data again. `PushAnswer` retains one
call-ID/expiry pair and consumes it only for the matching authenticated incoming
SIP call. The queued answer rechecks identity, state and visibility before starting
the foreground call service; a stale notification cannot answer a different call.

`PushAlerts` requests notification permission, registers the token against the
App bearer and renews it at most hourly while open. Disabling notifications removes
the destination on the next app open when both call and callback channels are disabled. New tokens replace old ones; reinstalling or
clearing storage requires enrollment again. Server tokens expire after 30 days
without renewal: reopen the app to renew. Invalid FCM destinations are removed.

An enabled footer means token registration succeeded, not a delivery guarantee.

The server waits for fresh SIP registration for up to 60 seconds, including when
an older registration still exists. The signed notification has an expiry and

Answer/Decline actions (Android may label video answer as **Video**). Answer opens
the activity, reloads the permitted directory and registers; only the matching
unexpired, independently authenticated SIP INVITE can be auto-answered.
Notification answering is queued onto the next main-loop turn, so native
incoming-state handling finishes before acceptance. The queued
action rechecks call identity, incoming state, activity visibility and expiry.
Decline sends a bearer-authenticated cancellation. Caller cancellation sends a signed
cancel message. Expiry dismisses the alert even if cancellation is delayed; a
short local tombstone rejects reordered ring messages after cancellation.

Android can show the incoming screen over the lock screen when full-screen-call
permission is available; otherwise it presents the call notification. No permanent
foreground service, microphone or camera is started by push alone. After a visible user starts or answers a call, CallService may retain microphone audio while the screen is hidden; camera sending is paused. Notification/channel
permissions, Android Force stop, OEM battery restrictions, connectivity and Google
Play services affect delivery. Qualify these on the actual target phone; emulator
callback tests do not establish FCM delivery or Doze reliability. Return through the ongoing call notification to resume video after switching apps or locking the screen. Sender setup is in the [ZmalaServer technical guide](https://github.com/toastfr35/ZmalaServer/blob/main/docs/server.md).

## Signed release builds

Release packaging is separate from runtime requirements. Normal publication uses
the protected **Release** workflow on `main`, with the original signing identity
and Firebase configuration. `build_tools/android/release.py` is the packaging
helper used by that workflow. It accepts the existing signing identity and
defaults to `~/.zmala/signing/android-release.json`; `--signing` selects another
private file outside the checkout. Its fields are `storeFile`, `storePassword`,
`keyAlias`, `keyPassword` and the pinned public `certificateSha256` fingerprint.
The keystore must also be outside the checkout. Restrict both files to the release
operator and back them up securely off this PC. Losing this identity prevents
updates to APKs already distributed with it. Never create a replacement key merely
because a build machine is missing the original.

The helper runs portable JVM tests plus release lint and assembly, verifies the APK signature
against the pinned certificate, checks 16 KiB ZIP alignment and rejects debuggable,
test-only, wrong-package or backup-enabled APKs. Output is in
`build_tools/dist/ZmalaPhone-<version>/ZmalaPhone.apk`, with local SHA-256 checksum,
public signature details and license notices alongside it. ZmalaApp releases
publish the signed APK, checksum, signature details and the Android installation
and user guides. Gradle requires the release signing configuration and Firebase
client configuration before it will assemble a release APK. The Verify workflow
builds and checks debug variants without either input. Dispatch Release from
main after verification of the intended release commit. The publishing helper rejects
a superseded main commit or an unexpected asset list. Only its protected release environment supplies the
Android signing and Firebase inputs.

The historical first signed release was 0.3.6, version code 15. The app supports Android 9/API 28 or newer,
arm64-v8a and x86_64. It uses the pinned native libraries without adding code
shrinking. Subsequent releases must increase the version code and retain the same
signing identity. Release and development APKs have different certificates:
Android cannot install one over the other. Obtain a fresh enrollment QR before
uninstalling an existing development copy; uninstalling removes its setup. Do not
remove users' working development installations automatically.

For website or GitHub distribution, users download the APK, authorize installation
from that browser/file manager, install and scan their private setup QR. Users of the
historical ZmalaCall release can install the first ZmalaApp release manually to
switch to the new update feed while preserving enrollment. Subsequent
signed release APKs install as updates and preserve setup. The download is public;
account QR codes, server keys, signing files and diagnostic captures are private.
The source repository does not contain a deployment's `google-services.json`;
builders supply their own Firebase Android configuration. Firebase client project
identifiers/API configuration are necessarily embedded in the configured APK and
are not server sender credentials. See `zmala_phone/NOTICE.md` for corresponding
source access and packaged dependency notices. The APK includes the AGPL and Apache
licenses and the pinned SDK/component notice inventory under assets/licenses/.

## Callback request notifications

`CallbackBook` and `CallbackAlerts` reconcile ordinary callback reminders from the
authenticated configuration. Incoming pending requests use the separate Callback
requests channel, the current permitted contact name and a timeout matching the
request deadline. Tapping waits for fresh contact permissions and opens the contact
without dialing; an active call retains its screen. Seen IDs survive user dismissal
and restart. Reconciliation clears cancelled, completed, expired or forbidden
requests, silently updates changed names/deadlines and limits state to 512 seen IDs
and 32 active reminders per enrollment identity. Disabled channels do not consume
unseen requests. Call and callback channels share a push destination; either may
remain enabled independently.

FCM `callback` messages reuse the enrollment HMAC/device/deadline checks but cannot
enter the incoming-call or media path. They schedule unique expedited WorkManager
work (2.12.0), falling back to a regular job when quota is unavailable.
The application supplies job IDs 10000–19999, separate from update job 9037.
The default WorkManager initializer is removed so this configuration takes effect;
other AndroidX startup components remain enabled. This follows Android's
[custom initialization guidance](https://developer.android.com/develop/background-work/background-tasks/persistent/configuration/custom-configuration). The worker
revalidates the hint, fetches current bearer-authorized configuration and applies
only the latest refresh for the same enrollment. Temporary network failures retry
at most twice; expired or revoked hints stop. Android 9–11 may briefly show a silent
checking-requests foreground notification. No idle SIP connection or camera/microphone
is started. This follows [Firebase background processing guidance](https://firebase.google.com/docs/cloud-messaging/android-message-priority).

## Sharing a photo during a call

`CallPhotoShare` belongs to `CallSession`, so rotating the Activity or opening the
system image picker keeps the share and the native call. `MainActivity` offers
**Share a photo** only for connected App or Terminal contacts when the server advertises
`call_photo_sharing`. `ACTION_OPEN_DOCUMENT` selects one image without requesting
broad library permission. Picker cancellation leaves the call unchanged.

`CallPhotoImage` bounds source data to 20 MiB/64 million pixels, uses ImageDecoder
orientation and downsampling, and uploads a JPEG up to 1600 pixels per side/2 MiB.
`CallPhotoHttp` uses the enrollment origin and bearer token with redirects disabled.
Selected-image reads use the same bounded, interruptible reader as HTTP. Received
JPEG dimensions are checked before allocating pixels, using the same normalized
image limits as the sender.
The image overlays both video views with FIT_CENTER; microphone, listening volume
and hangup remain usable. Cameras/media surfaces are retained beneath the layer.
Stop clears it immediately and requests deletion; renewals every three seconds
maintain the server's 12-second lease. Call identity plus `PhotoShareEpoch` reject
late results after stop or a new call. Sender upload or renewal failure stops the
share and shows a message; the voice call continues. A receiving phone polls the
same authenticated endpoint, preserving the last lease deadline through failed
requests. It hides sender-only actions and clears the image on stop, expiry or
call change. Only one caller shares at a time; either app can claim the next idle
share. Shared photos are temporary and are not saved to the recipient's album.

Photo state and timers belong to the main thread; the single worker handles I/O
and returns immutable receive results. Unchanged revisions reuse their displayed
bitmap and refresh their local expiry deadline without notifying the screen. Only
the sender renews the server lease. An already-expired status
does not trigger an image download, and a separate timer clears an expired image
even while HTTP is blocked. Only unpublished results are explicitly recycled;
displayed bitmaps can still be referenced by a view. Sender operations capture the
initiating enrollment, so queued cleanup cannot use a replacement account.
