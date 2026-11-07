PYTHON ?= python3
NODE ?= node

.PHONY: requirements requirements-check
requirements:
	$(PYTHON) build_tools/requirements.py

requirements-check:
	$(PYTHON) build_tools/requirements.py --check

GRADLE := $(if $(filter Windows_NT,$(OS)),zmala_phone/gradlew.bat,sh zmala_phone/gradlew)

phone:
	$(GRADLE) -p zmala_phone :app:assembleDebug

test-android:
	$(GRADLE) -p zmala_phone :app:testDebugUnitTest :app:lintDebug

test-android-device:
	$(GRADLE) -p zmala_phone :app:connectedDebugAndroidTest

test-adhoc:
	$(PYTHON) -m unittest discover -s tests/adhoc -t .
