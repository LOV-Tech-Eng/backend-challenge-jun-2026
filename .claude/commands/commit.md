---
description: Stage the relevant changes and create one clean Conventional Commit (English, atomic). Does not push.
argument-hint: [optional scope/intent hint]
---

Create a professional, standards-compliant commit. Hint (optional): $ARGUMENTS

1. Run `git status` and `git diff` (staged + unstaged) to see exactly what changed.
2. Group into ONE atomic logical change. If the working tree spans several unrelated changes, STOP and
   propose splitting them into multiple commits before committing anything.
3. Write a **Conventional Commit** — `type(scope): subject`: imperative, lowercase, ≤ ~50 chars, **English**,
   no trailing period. Add a body explaining the **why** when the change isn't trivial.
4. End the commit message with the trailer `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`.
5. Do not commit secrets or build output. Do not push (the user asks for that separately).
6. Show me the final message and the commit result.
