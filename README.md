# ZmalaApp

This repository owns Zmala Phone, the Android app for calls with permitted family
contacts on phones and TV terminals. Installation requires a private App setup QR
from the person managing the Zmala server.

- [Technical guide](docs/android.md)
- [Development and requirements](docs/general.md)
- [Installation guide source](docs/phone_installation.docx)
- [Everyday use guide source](docs/phone_guide.docx)
- [Dated verification records and limits](docs/qualification.md)
- [Downloads](https://github.com/toastfr35/ZmalaApp/releases)

## Related repositories

- [ZmalaServer](https://github.com/toastfr35/ZmalaServer)
- [ZmalaTerminal](https://github.com/toastfr35/ZmalaTerminal)
- [Zmala](https://github.com/toastfr35/Zmala)
- [Original ZmalaCall repository and historical downloads](https://github.com/toastfr35/ZmalaCall)

## Release

Releases include `ZmalaPhone.apk`, `SHA256SUMS.txt`, `SIGNATURE.txt`,
`ZmalaPhone-Installation.pdf` and `ZmalaPhone-Guide.pdf`. The editable Word sources
are in `docs/`; `build_tools/release/guides.py` generates the two PDFs.

Preserve application ID `org.zmala.phone` and the existing signing identity for
in-place upgrades. After verification of the release commit, dispatch **Release**
on `main`; its protected environment supplies signing and Firebase inputs. A
merged change or debug build is not a published release. See the
[release procedure](docs/android.md#signed-release-builds).
