# ZmalaApp requirements

The Doorstop baseline under `android/` covers the Android runtime.
`ownership.json` identifies the owner of each requirement. Read the relevant
HLR, LLR and verification plan before changing behavior.

The [2026-11-09 implementation review](reviews/2026-11-09.md) records the reviewed
baseline, corrected drift and outstanding verification/implementation gaps. It is
a review record, not an additional normative specification or a hardware qualification.

## External interfaces

- [App and Server interface](interfaces/app-server.md) covers enrollment,
  authenticated HTTP, callbacks, push, SIP/TURN, media and compatibility
  verification. ZmalaServer carries an identical copy.

When changing an interface, update both copies in coordination. Test with
disposable local peers, and record the exact app and server versions when
qualifying live interoperability. Run `make requirements` to verify the local
Doorstop tree.
