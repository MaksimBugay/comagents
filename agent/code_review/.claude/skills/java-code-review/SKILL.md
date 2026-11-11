---
name: java-code-review
description: Review Java 25 / Spring Boot code, diffs, branches, or commit ranges for actionable correctness, security, performance, concurrency, design, dependency, and test issues, and produce a severity-ordered report. Use when a code review is requested; not for implementation work.
argument-hint: "[path... | <base>..<head> | branch]"
allowed-tools: Read Grep Glob Bash(git diff *) Bash(git log *) Bash(git show *) Bash(git status *) Bash(git merge-base *) Bash(git rev-parse *) Bash(git ls-files *) Bash(git blame *) Bash(./mvnw *) Bash(mvn *) Bash(./gradlew *)
---

# Java code review

Act as a senior Java 25 / Spring Boot reviewer. Produce a rigorous, defect-first review whose every
finding the author can verify and act on without further questions.

Review target: $ARGUMENTS

## 1. Determine the scope

Resolve the target in this order and state the resolved scope at the top of the report:

1. Explicit paths → review those files (and the code they depend on as needed).
2. A commit range (`a..b`) or branch → `git diff <merge-base>..<head>` of that range.
3. Nothing given → the current branch's changes against its base:
   `git diff $(git merge-base HEAD origin/main 2>/dev/null || git merge-base HEAD main)` plus uncommitted
   changes (`git status`). If there are no changes either, report `review incomplete` and ask for a target
   rather than reviewing the whole repository.

Skip generated and vendored code (`target/`, `build/`, generated sources) unless explicitly targeted.

## 2. Prepare

1. Read the reviewed project's `CLAUDE.md`/`AGENTS.md` and `README.md`; their conventions are
   authoritative over this skill.
2. Read [the review checklist](references/review-checklist.md) completely.
3. Apply the standard in `.claude/rules/java-spring-boot.md`.
4. For each changed unit, read the implementation, its callers, its tests, and the build file
   (`pom.xml`/`build.gradle*`) before reaching conclusions. For a diff, read enough surrounding code to
   establish impact; the diff alone is not evidence.
5. Stay read-only (`.claude/rules/read-only.md`).

## 3. Verify

Run the narrowest meaningful checks and record each command with its result:

- focused tests for the changed classes (`./mvnw -B -q test -Dtest=<TestClass>`), the full suite
  (`./mvnw -B verify`) for shared code, build, or dependency changes when feasible;
- `./mvnw -B -q dependency:tree` when dependencies changed.

If a command cannot run (missing toolchain, network, resources), say so; never report it as passed.

## 4. Report

Assign exactly one severity to every finding:

- `P0 Critical`: an unconditional failure with catastrophic impact (data loss, security breach, outage).
- `P1 High`: a high-impact defect requiring prompt attention.
- `P2 Medium`: a substantive defect under a stated scenario.
- `P3 Minor`: a limited-impact actionable issue, including a cosmetic issue worth changing.

List findings in strict descending severity. Consolidate findings with the same root cause under the
highest demonstrated severity. Do not invent findings to fill a level or impose a quota. In diff reviews,
mark pre-existing issues as `(pre-existing)`.

Use this structure:

```markdown
## Code review: <scope>

**Conclusion:** changes required | no actionable findings within reviewed scope | review incomplete — <one-line reason>
**Findings:** P0: n · P1: n · P2: n · P3: n

### Findings

#### P1 High — <title>
- **Location:** `path/to/File.java:42-57` (`method()`)
- **Evidence and impact:** <trigger input or interleaving, actual vs required behaviour, who is affected>
- **Remedy:** <smallest correct fix, reusing existing code where possible; short snippet if it helps>
- **Validation:** <focused test or measurement that would prove the fix>

### Coverage
| Area | Status | Notes |
|------|--------|-------|
| ... | checked / issue found / not applicable / unverified | ... |

### Verification
- `<command>` → <result>

### Open questions
- <only questions that materially limit the conclusion>
```

The coverage areas, the mandatory performance and threading statements, and the rules for
optional improvements are defined in the checklist. If there are no findings, say so explicitly and
still report coverage and verification.
