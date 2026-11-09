# ZmalaApp engineering

This guide covers repository work, local checks and documentation maintenance.
Read the [implementation guide](android.md) for runtime ownership and the
[App/Server contract](../requirements/interfaces/app-server.md) before changing
client-facing behavior. ZmalaApp owns Android code, requirements and user guides;
the sibling repositories own the server, terminal and SD-card images.

## Work on an issue branch

Use a GitHub issue and a `codex/` feature branch, then open a pull request to
`main`. Follow [AGENTS.md](../AGENTS.md). Merge through GitHub only after Verify
passes on the current pull-request commit.

Keep temporary work under the workspace's top-level `tmp/`, including disposable
worktrees, environments, build output, logs and document previews. A worktree
inside `tmp/` keeps Gradle's normal build directories there too. Leave
`tmp/migration/` intact; it contains retained migration data and backups.

From the App repository, an example for an existing issue is:

```powershell
git worktree add -b codex/issue-N ../tmp/app-issue-N origin/main
Set-Location ../tmp/app-issue-N
```

Replace `N` with the issue number. Make changes in that worktree. Point `TEMP`,
`TMP`, `GRADLE_USER_HOME` and `PYTHONPYCACHEPREFIX` at task-specific directories
under the same top-level `tmp/` when running local tools.

## Build prerequisites

Use the pinned Gradle wrapper with JDK 21 or a compatible newer JDK, Android SDK
platform `android-37.0`, build tools `37.0.0` and NDK `29.0.14206865`. The app's
minimum API is 28; its compile/target API is 37. Python is also required for the
native adapter and requirement checks. `PYTHON` selects the build interpreter;
`JAVA_HOME` selects the JDK. The native adapter verifies pinned SDK inputs.

Debug fixture builds do not need signing secrets or Firebase configuration.
Real push delivery needs the configured client and server. Release inputs belong
to the protected release workflow, as described in the implementation guide.

## Requirements and tests

Install Python development dependencies and the pinned Doorstop tooling in the
disposable worktree. On Windows:

```powershell
python -m venv .venv
.venv/Scripts/python.exe -m pip install -r requirements-dev.txt -r build_tools/doorstop-requirements.txt
$env:PYTHON = (Resolve-Path .venv/Scripts/python.exe).Path
make requirements PYTHON="$env:PYTHON"
make phone test-android
make test-adhoc PYTHON="$env:PYTHON"
```

On Unix, use `.venv/bin/python` for the interpreter. `make requirements` validates
and publishes the Doorstop baseline locally; `make requirements-check` validates
without publishing. Generated `requirements/output/` is not committed.

`requirements/ownership.json` identifies the authoritative owner of each stable ID.
Imported parents retain their IDs. Update imported copies in a reviewed change
from their owner. For example, `python build_tools/requirements.py --next-id LLR AND CALL`
reports the next available calling ID. Keep requirement text, code references and
test references in sync. Read the [requirements maintenance guidance](../requirements/README.md).

Implementation uses a standalone language comment such as
`// @llr LLR-AND-CALL-007` immediately before the owning Java declaration or
cohesive block. Separate multiple IDs with a comma and a space.

A test begins with standalone `@test`, `@requirements`, `@environment`,
`@plan` and `@pass` comments. Link LLT directly to an LLR and HLT directly to
an HLR. State inputs, procedure and expected assertions. Refer to the test from
each Doorstop item. An inventory or a plan does not record an execution.

## Device checks and evidence

JVM tests live in `tests/android/LLT/unit`; native Android LLTs and HLTs live in
`tests/android/LLT/device` and `tests/android/HLT`. Shared HTTP/SIP peers live in
`tests/support/android`. Packaging/tooling tests belong in `tests/adhoc`.

Select a disposable emulator explicitly before running native tests. Several
scenarios replace enrollment or clear app data. Do not run the whole suite on a
personal phone. Keep the emulator unlocked and awake, and check test output for
failed or skipped cases.

```powershell
adb devices
$env:ANDROID_SERIAL = 'emulator-5582' # Replace with the selected disposable emulator.
make test-android-device
```

For a focused suite, build and install both debug APKs, then select the class:

```powershell
zmala_phone/gradlew.bat -p zmala_phone :app:assembleDebug :app:assembleDebugAndroidTest
adb -s $env:ANDROID_SERIAL install -r zmala_phone/app/build/outputs/apk/debug/app-debug.apk
adb -s $env:ANDROID_SERIAL install -r zmala_phone/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s $env:ANDROID_SERIAL shell am instrument -w -r -e class org.zmala.phone.CallPhotoShareTest org.zmala.phone.test/androidx.test.runner.AndroidJUnitRunner
```

The Verify workflow runs requirements, version validation, a debug build, JVM
tests, Android lint, tooling tests and guide generation. It does not run device
instrumentation or physical call qualification. Record the commit, app version,
device/API, selected tests and any skips separately from test plans. See the
[qualification record](qualification.md) for dated evidence and its limits.

## Maintain the guides

Edit `docs/phone_installation.docx` and `docs/phone_guide.docx` for user-facing
instructions. Their release filenames are `ZmalaPhone-Installation.pdf` and
`ZmalaPhone-Guide.pdf`. Keep button names aligned with Android resources and
explain the user's action rather than internal implementation details.

```powershell
.venv/Scripts/python.exe -m pip install -r build_tools/release/requirements.txt
.venv/Scripts/python.exe build_tools/release/guides.py --output ../app-doc-preview
```

The example output stays under top-level `tmp/` when run from the worktree above.
Inspect every rendered page for clipping, stranded headings and readable links.
Keep PDF previews out of Git; Release regenerates its assets from the Word files.
Check relative Markdown links and preserve dated historical evidence when
consolidating engineering documentation.

The current Verify version gate also applies to documentation outside
`requirements/`. Update `versionName` and `versionCode` when changing the shipped
guides; this identifies the updated release assets without implying a runtime change
or publishing a release.
