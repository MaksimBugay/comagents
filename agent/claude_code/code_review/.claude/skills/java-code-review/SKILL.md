---
name: java-code-review
description: Review Java 25 / Spring Boot code — a whole project, selected files or folders, or a patch file — for actionable correctness, security, performance, concurrency, design, dependency, and test issues, and produce a severity-ordered report. Works without git. Use when a code review is requested; not for implementation work.
argument-hint: "[path... | file.patch]"
allowed-tools: Read Grep Glob Bash(./mvnw *) Bash(mvn *) Bash(./gradlew *)
---

# Java code review

Act as a senior Java 25 / Spring Boot reviewer. Produce a rigorous, defect-first review whose every
finding the author can verify and act on without further questions.

Review target: $ARGUMENTS

## 1. Determine the scope

The project may be a plain snapshot of the sources without version control. Do not use git, and do not
rely on history, branches, or a diff being available.

Resolve the target in this order and state the resolved scope at the top of the report:

1. Explicit files or folders → review those (and the code they depend on as needed).
2. A patch file (`*.patch`, `*.diff`) → review the changes it describes, reading the current files for
   context. The patch is the diff for the checklist's diff-review rules.
3. Nothing given → the whole project in the working directory. Start from the build file and the entry
   points (`@SpringBootApplication`, controllers, scheduled and async jobs, message listeners), then follow
   the code they reach. If the project is too large to read completely, review security-sensitive code,
   shared infrastructure, and concurrent code first, and list everything not reviewed under Coverage as
   `unverified`.

Skip generated and vendored code (`target/`, `build/`, generated sources) and this agent's own
configuration (`CLAUDE.md`, `.claude/`) unless explicitly targeted.

## 2. Prepare

1. Read the reviewed project's `AGENTS.md` and `README.md` if present; their conventions are
   authoritative over this skill. `CLAUDE.md` and `.claude/` in the project root are this agent's own
   configuration, not the project's.
2. Read [the review checklist](references/review-checklist.md) completely.
3. Apply the standard in `.claude/rules/java-spring-boot.md`.
4. For each reviewed unit, read the implementation, its callers, its tests, and the build file
   (`pom.xml`/`build.gradle*`) before reaching conclusions. For a patch, read enough surrounding code to
   establish impact; the patch alone is not evidence.
5. Stay read-only (`.claude/rules/read-only.md`).

## 3. Verify

Run the narrowest meaningful checks and record each command with its result:

- focused tests for the reviewed classes (`./mvnw -B -q test -Dtest=<TestClass>`); the full suite
  (`./mvnw -B verify`) for a whole-project review or for shared code, build, or dependency changes when
  feasible;
- `./mvnw -B -q dependency:tree` for a whole-project review or when dependencies changed.

If a command cannot run (missing toolchain, network, resources), say so; never report it as passed.

## 4. Report

Assign exactly one severity to every finding:

- `P0 Critical`: an unconditional failure with catastrophic impact (data loss, security breach, outage).
- `P1 High`: a high-impact defect requiring prompt attention.
- `P2 Medium`: a substantive defect under a stated scenario.
- `P3 Minor`: a limited-impact actionable issue, including a cosmetic issue worth changing.

List findings in strict descending severity. Consolidate findings with the same root cause under the
highest demonstrated severity. Do not invent findings to fill a level or impose a quota. In patch
reviews, mark pre-existing issues as `(pre-existing)`.

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
