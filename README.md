# ZmalaApp

This repository contains the Zmala Android app.

- [Technical guide](docs/android.md)
- [Development and requirements](docs/general.md)
- [Migration provenance](docs/migration/provenance.json)
- [Downloads](https://github.com/toastfr35/ZmalaApp/releases)

## Related repositories

- [ZmalaServer](https://github.com/toastfr35/ZmalaServer)
- [ZmalaTerminal](https://github.com/toastfr35/ZmalaTerminal)
- [Zmala](https://github.com/toastfr35/Zmala)
- [Original ZmalaCall repository and historical downloads](https://github.com/toastfr35/ZmalaCall)

## Release

The release asset is ZmalaPhone.apk. Preserve application ID org.zmala.phone and its signing identity for in-place upgrades.

After Verify passes on the merged version, dispatch **Release** on main. Terminal and App require the existing signing credentials in the release environment.
