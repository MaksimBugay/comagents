---
name: write-unit-test
description: Write or extend a JUnit 5 unit test for a given Java class and run it. Use when asked to add tests, cover a class, or reproduce a bug with a test.
argument-hint: "[path/to/Class.java]"
allowed-tools: Read Grep Glob Edit Write Bash(./mvnw *) Bash(mvn *)
---

# Write a unit test

Target class: $ARGUMENTS

1. Read the target class and find its existing test (`src/test/java/<same package>/<Class>Test.java`).
2. List the behaviours worth testing: the happy path, edge cases (null/empty/boundary), and error paths.
3. Write or extend the test following the conventions in [examples.md](examples.md):
   - one behaviour per test method, named `<action><Expectation>` (e.g. `parseRejectsEmptyInput`)
   - arrange / act / assert, no logic in tests
   - plain JUnit 5 assertions; `@MicronautTest` only when the class needs the application context
4. Run only that test: `./mvnw -B -q test -Dtest=<Class>Test`.
5. If it fails, decide whether the test or the code is wrong. Fix the test; report (do not silently fix) suspected bugs in the code.
6. Reply with the test file path, the behaviours covered, and the test result.
