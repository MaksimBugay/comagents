---
paths:
  - "**/*.java"
---

# Java style

- Target Java 25: prefer `record`, `var` for obvious local types, switch expressions, and text blocks.
- Use constructor injection; never field injection in production code.
- No wildcard imports. No unused imports.
- Public API types and methods get a short Javadoc only when the name does not already say it all.
- Do not catch `Exception`/`Throwable` unless rethrowing or at a process boundary.
