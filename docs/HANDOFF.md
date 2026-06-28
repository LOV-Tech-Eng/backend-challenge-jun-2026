# HANDOFF — single continuity doc

> Purpose: if I run out of tokens or switch account/AI mid-challenge, whoever continues reads
> THIS file + `00-context.md` and can pick up with zero context loss. Keep it short and current.

## Current state
- _What is built and working right now. Newest first._

## Next steps
- _The very next slice to build, in order._

## Architecture & key decisions
- Stack: _tbd_ · Routing engine: _pure function, table-driven tests_
- Decisions (ADR-lite — decision · why):
  - No healthy processor → **503** · downstream down, not a client error
  - Money as integer minor units + ISO-4217
  - _add forks here as they happen_

## Review / audit findings
- _AC-by-AC scoring (PASS/PARTIAL/FAIL) + blockers, filled during `/audit`._

## How to run & deliver
- Build/run: _tbd_
- Test: _tbd_
- Deploy/submit: _tbd_
