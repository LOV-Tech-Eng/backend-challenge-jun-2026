---
description: Pre-delivery audit — re-run agents with a strict scoring mindset and score every AC PASS/PARTIAL/FAIL with evidence
argument-hint: [optional area to focus the audit]
---

We are in **Audit Mode**. Strict scoring mindset: nothing is "good enough" without evidence.
Focus (optional): $ARGUMENTS

Steps:

1. **Deep read first** — read every source file under `src/` and the `docs/` so agent prompts are
   grounded in the actual implementation, not assumptions.
2. Launch the audit agents **in parallel**:
   - `requirements-analyst` → gap between spec and implementation; MISSING / WEAK / SKIP buckets
   - `qa-resilience` → formula/logic consistency, untested branches, dead code, missing validation
   - `security-compliance` → leaked secrets, unmasked card data, unvalidated inputs, error leaks
   (add `api-designer` / `systems-architect` if the audit touches contract or architecture.)
3. Produce an **AC-by-AC scoring table**: each criterion PASS / PARTIAL / FAIL with file:line evidence.
   A working feature is still PARTIAL if it doesn't fully satisfy the criterion.
4. Categorize all findings into three phases, ordered by score impact:
   - **Phase 1 — Blockers** (hard deliverable fails: build broken, README missing, correctness bugs)
   - **Phase 2 — High-impact** (moves an AC from PARTIAL to PASS)
   - **Phase 3 — Polish**
5. Write the scoring + findings into the Review section of `docs/HANDOFF.md`. Then STOP and show me the phased fix list.
   Do not start fixing until I confirm the order.
6. **After the fixes are applied:** confirm the test suite is green (`./mvnw verify`) and mark the affected
   ACs PASS in `docs/HANDOFF.md`. Do **NOT** re-run this full audit (re-launching the agents) unless the
   fixes were a large or risky refactor — the green suite is the verification, not another agent pass.
