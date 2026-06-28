---
description: Run all specialist agents in parallel to produce a merged implementation plan with an AC table (Plan Mode)
argument-hint: [optional focus area, e.g. "routing engine only"]
---

We are in **Plan Mode**. Do not write production code in this command.

Goal: produce ONE merged, approved-ready implementation plan for the Yuno fintech / payments backend challenge.
Focus (optional): $ARGUMENTS

Steps:

1. Read `docs/00-context.md`. If it's empty, STOP and tell me the enunciado hasn't been loaded yet.
2. Launch these specialist agents **in parallel** (single message, multiple Agent calls):
   - `requirements-analyst` → finalized AC table + ambiguities + assumptions
   - `api-designer` → REST contract, idempotency, error model
   - `systems-architect` → module boundaries, routing engine design, trade-offs, skeletons
   - `qa-resilience` → test strategy, edge-case matrix, resilience spec, risk matrix
   - `security-compliance` → sensitive-data map, validation & secrets guardrails
3. Merge their outputs into a single plan with:
   - the consolidated **AC table** (MUST/SHOULD/NICE)
   - the chosen architecture + routing strategy (one recommendation, alternatives noted)
   - a **time-boxed task breakdown**: each task with a time estimate that fits the budget, ordered so the
     **pass/fail-critical tasks come first** (what the evaluation hinges on), foundation before features
   - an incremental build sequence (auto-flow; stop only on exceptions — an unresolvable ambiguity, a
     failing AC, or a decision only the user can make)
   - the open questions that genuinely need my decision (keep this short)
4. Write the AC table + assumptions into `docs/00-context.md`, and the chosen architecture + key
   decisions into `docs/HANDOFF.md`.
5. Present the plan and the non-negotiable rules for the build. Wait for my approval before any code.
