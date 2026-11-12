# Java 25 / Spring Boot review checklist

Apply the checks relevant to the reviewed scope, but always assess performance and thread safety.
The best-practice standard itself lives in `.claude/rules/java-spring-boot.md`; this checklist defines
how to review against it.

## Scope and evidence

- For a diff review, inspect enough surrounding code, callers, and tests to establish impact. Separate
  defects introduced by the diff from pre-existing issues.
- Establish inputs, outputs, constraints, edge cases, ownership, mutation and null policies, ordering,
  overflow behaviour, and concurrency guarantees from the requirements, tests, and callers. Do not
  invent requirements.
- When unresolved uncertainty could change the conclusion (correctness, public API, threading
  guarantees, compatibility), record it under open questions and continue independent investigation.
- Trace each suspected issue through an actual call path. Support it with a concrete input, a legal
  thread interleaving, a violated contract, a complexity argument, a test, or a runtime observation.
- Separate confirmed defects from open questions and measurement hypotheses. Do not report
  speculation as a finding or style preferences as defects. Do not report what the project's
  formatter/linter already enforces.
- Respect the public API, wire formats, configured source roots, and required Java and build toolchain.

## Correctness and Java 25

- Check the logic against stated requirements and examples, including empty and singleton inputs,
  duplicates, boundaries, malformed input, integer overflow, and termination.
- Check equality and hashing, comparator contracts, mutable collection keys, iteration-order
  assumptions, generic type safety, exception behaviour, and resource ownership.
- Verify failure paths leave valid state, close owned resources, shut down owned executors, and
  propagate asynchronous failures.
- Check Unicode, charset, locale, timezone, and floating-point assumptions where the contract makes
  them relevant.
- Verify compiler release, runtime, test runner, plugins, and libraries are compatible with Java 25.
  Prefer stable Java 25 features that simplify the actual code; do not require modernisation for style
  alone.
- `StructuredTaskScope` is preview in Java 25: flag unapproved preview/incubator use and missing flags.
  `ScopedValue` is final in Java 25; suggest it only for a demonstrated context-sharing need.
- Consult official documentation and current primary sources when an API guarantee, feature status,
  performance claim, dependency version, or security fact is uncertain. Report unavailable
  verification rather than guessing.

## Spring Boot

- Bean wiring: constructor injection, scopes, circular dependencies, conditional beans, and
  configuration binding/validation.
- Transactions: boundaries on public methods, self-invocation bypassing proxies, propagation and
  rollback rules (checked exceptions do not roll back by default), remote calls inside transactions.
- Persistence: N+1 and unbounded queries, lazy loading outside a session, open-in-view, missing indexes
  implied by new queries, migrations matching entity changes, optimistic locking for concurrent updates.
- Web: input validation, error mapping, status codes, entity leakage, pagination, timeouts on outbound
  clients, retry amplification.
- Security: authentication/authorization rules for new endpoints, CSRF, CORS, actuator exposure,
  secrets in config, logging of sensitive data.
- Operations: health/readiness semantics, graceful shutdown, pool and cache bounds, observability for
  new failure modes.

## Performance

- State time and auxiliary-space complexity for the main operations, defining variables and
  distinguishing worst-case from amortised behaviour where needed.
- Check repeated scans, sorting, nested work, copying, expensive loop bodies, unsuitable collections,
  recursion depth, boxing, intermediate objects, and retained memory against realistic input sizes.
- Where applicable, assess throughput, tail latency, contention, lock scope, blocking I/O, executor and
  connection pool capacity, bounded queues, backpressure, timeouts, retry amplification, cache bounds,
  TTL cleanup, and eviction.
- Relate each optimisation to a requirement or evidence. Distinguish static complexity analysis from
  measured performance.
- For uncertain hotspots, propose a specific measurement: JFR for CPU, allocation, GC, and contention;
  JMH for justified microbenchmarks with representative inputs, warmup, forks, result consumption,
  hardware/JDK details, and comparable baselines.

## Thread safety

- Classify the component as stateless or immutable, thread-confined, externally synchronised,
  internally thread-safe, or unsafe for intended concurrent use. Spring singletons are shared across
  request threads: any mutable field in a singleton bean is suspect.
- Inventory shared mutable state, aliases, static fields, callbacks, and mutable objects exposed
  through constructors or accessors. A `final` reference, record, or unmodifiable wrapper is not deep
  immutability.
- Check safe publication, happens-before relationships, constructor escape, lazy initialisation, stale
  reads, visibility, atomicity, and multi-field invariants.
- Check compound actions and their atomic boundary (check-then-act, read-modify-write, capacity checks,
  eviction, TTL refresh). A concurrent collection does not make a compound operation atomic.
- Check lock ordering, release in `finally`, condition loops, deadlock, starvation, livelock, and calls
  to blocking or unknown code while holding locks.
- Check admission bounds, rejection, shutdown, deadlines, cancellation, interruption, cleanup, executor
  choice, blocking in the common pool, nested waits, and lost asynchronous exceptions (`@Async`,
  `CompletableFuture`).
- Virtual threads suit blocking work but do not speed up CPU work or remove synchronisation needs.
  Bound access to scarce resources rather than pooling virtual threads. In Java 25, ordinary
  `synchronized` blocks do not pin virtual threads; require evidence before reporting pinning.
- For every race finding, show a legal sequence of operations and the invariant it breaks. Prefer
  controlled concurrency tests; passing stress tests alone do not prove thread safety.

## Design, reuse, and dependencies

- Assess the SOLID principles proportionately. Accept a pure function or small concrete class as a
  complete design. Do not demand speculative interfaces, strategies, or layers.
- Search for equivalent behaviour before recommending new code or dependencies. Prefer existing
  project code, then the JDK/Spring, then existing dependencies.
- Check repeated business rules, validation, algorithms, and configuration. Share code only where
  callers have the same contract.
- Verify dependencies are necessary, correctly scoped, and exactly versioned or BOM-managed. Flag
  bundled JARs, version ranges, snapshots, and prereleases.
- For dependency changes, verify the latest compatible stable release on Maven Central, read the
  declared and BOM-managed versions in the build file, and check vulnerabilities in **more than one** source — the publisher's GitHub
  security advisories plus OSV or NVD, since each can lag the others. State the check date, the fixed
  version, and any verification that was unavailable.
- Inspect real trust boundaries for injection (SQL/JPQL, command, log, SpEL), path traversal, SSRF,
  unsafe deserialisation, weak randomness, secret exposure, and unbounded resource consumption. Avoid a
  generic security checklist when the code has no relevant boundary.

## Tests

- Check that tests cover the contract, boundaries, failure paths, and regressions for the change.
- Check the test type fits: unit tests for logic, slices for a single layer, `@SpringBootTest` only for
  wiring, Testcontainers where database behaviour matters.
- For concurrency, assess controlled interleavings, visible worker failures, bounded waits, cleanup,
  and invariants; sleep-based coordination is weak evidence.
- Review tests by reading them; never run them. Report material gaps in what they cover.

## Report content

Each finding contains severity and title, location (`path:line-range` plus symbol), evidence and impact,
minimal remedy, and validation (see `SKILL.md` for the format).

The coverage table must list each of these areas as `checked`, `issue found`, `not applicable` (with a
reason), or `unverified`:

- requirements and correctness
- Java 25 and build compatibility
- Spring Boot practices
- SOLID principles and minimality
- reuse and duplication
- dependency freshness and security
- performance
- thread safety
- tests

Always state the main operations' performance assessment and the component's threading contract in
the coverage notes, even when neither produces a finding.

Optional improvements go after the required fixes, each with its own severity (usually `P3 Minor`),
keeping the descending order.

Conclude with exactly one of `changes required`, `no actionable findings within reviewed scope`, or
`review incomplete`, plus a one-line reason.
