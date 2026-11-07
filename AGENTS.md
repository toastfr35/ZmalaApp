# ZmalaApp repository instructions

Use a GitHub issue and a `codex/` feature branch for every change. Open a pull
request to `main`. Wait for Verify and resolve failures before merging through
GitHub. Keep release inputs confined to the release workflow.

This repository owns the Android app. Keep code, normative Doorstop items,
standalone `@llr` comments, LLT/HLT plans and documentation synchronized.
Keep runtime requirements in `requirements/`. Keep IDs stable and reserve
numbers across HLR and LLR within each theme. Consult `requirements/ownership.json`
before editing a shared requirement. Coordinate imported copies with the owner.

Run `make requirements` after changes to requirements, code anchors, or test
plans. Do not commit `requirements/output/` or clear suspect links without
review. Product tests go in the top-level `tests/` tree. Keep packaging,
installer and tooling tests in `tests/adhoc/`. Record hardware skips explicitly.

Read `requirements/README.md` before changing an external interface. Preserve
peer compatibility with local fixtures and coordinate deliberate wire changes.

Increase Android versionName and versionCode in PRs that affect runtime, build,
or release. Changes limited to requirements, repository instructions, and
Verify configuration may keep the existing version.
