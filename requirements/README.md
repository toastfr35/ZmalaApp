# ZmalaApp requirements

The Doorstop baseline under `android/` covers the Android runtime.
`ownership.json` identifies the owner of each requirement. Read the relevant
HLR, LLR and verification plan before changing behavior.

The [2026-11-09 implementation review](reviews/2026-11-09.md) records the reviewed
baseline, corrected drift and outstanding verification/implementation gaps. It is
a review record, not an additional normative specification or a hardware qualification.

## Maintaining testable requirements

- Keep IDs and ownership stable. Refine an existing obligation in its owning item;
  allocate a new ID for a genuinely separate obligation rather than renumbering the baseline.
- State the trigger, observable result and failure/cancellation behavior. Give units and
  explicit limits for words such as bounded, periodic or idle; distinguish a default from
  accessibility or platform overrides.
- Keep high-level user outcomes separate from low-level mechanism and limits. Use paragraphs
  for distinct acceptance criteria within an existing item so each can be checked independently.
- Specify boundary cases and observable assertions in `verification`, including denied access,
  stale results and lifecycle transitions where applicable. Identify the LLT/HLT and environment
  that can establish the result; a file reference alone is not clause-level coverage.
- Record executed checks and skips separately from plans. Native layout tests establish rendered
  geometry; fixtures do not qualify physical speech/video, FCM transport or release signing.
- Preserve intended behavior when implementation falls short. Track and fix the discrepancy
  instead of relaxing the requirement to make the current implementation appear compliant.

## External interfaces

- [App and Server interface](interfaces/app-server.md) covers enrollment,
  authenticated HTTP, callbacks, push, SIP/TURN, media and compatibility
  verification. ZmalaServer carries an identical copy.

When changing an interface, update both copies in coordination. Test with
disposable local peers, and record the exact app and server versions when
qualifying live interoperability. Run `make requirements` to verify the local
Doorstop tree.
