---
description: Update the HANDOFF continuity doc to reflect the current state of the code
---

Update the project's handoff doc so a fresh session — or a different account/AI — can pick up with
zero context loss.

1. Read the current `src/` and `docs/00-context.md` + `docs/HANDOFF.md`.
2. Update `docs/HANDOFF.md`:
   - **Current state**: what's built and working, newest first.
   - **Next steps**: the very next slice to build, in order.
   - **Architecture & key decisions**: append any new fork as `decision · why` (ADR-lite).
3. Report a one-paragraph summary of what changed. Do not touch production code.
