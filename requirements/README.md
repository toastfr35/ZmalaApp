# ZmalaApp requirements

The Doorstop baseline under `android/` covers the Android runtime.
`ownership.json` identifies the owner of each requirement. Read the relevant
HLR, LLR and verification plan before changing behavior.

## External interfaces

- [App and Server interface](interfaces/app-server.md) covers enrollment,
  authenticated HTTP, callbacks, push, SIP/TURN, media and compatibility
  verification. ZmalaServer carries an identical copy.

When changing an interface, update both copies in coordination. Test with
disposable local peers, and record the exact app and server versions when
qualifying live interoperability. Run `make requirements` to verify the local
Doorstop tree.
