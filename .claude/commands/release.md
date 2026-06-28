---
description: Final delivery pass — release-engineer: Railway deploy + curl test evidence + Swagger link + 3 URLs into the README. Time-permitting.
argument-hint: [optional, e.g. "evidence only, no deploy"]
---

Final delivery pass (time-permitting), after build + audit. Scope (optional): $ARGUMENTS

1. Launch the `release-engineer` agent.
2. It runs the local curl test evidence (Release Plan), deploys to Railway (live URL — outward-facing,
   only on explicit go), links Swagger, and writes the **3 URLs (repo · Railway · Swagger)** + the
   evidence into the README.
3. If a curl reveals a real defect (e.g. a 404 returning `INTERNAL_ERROR`), STOP and fix before re-running
   that case — live tests catch what unit tests miss.
4. If there's no time to run it, leave the explicit "not run, would have…" note in the README.
5. Report N cases · P passed · F failed + the 3 URLs.
