# Java 25 / Spring Boot best practices

The standard reviewed code is measured against. Report a deviation only when it has a concrete
consequence (defect, risk, cost, or maintenance burden) in the reviewed code — not as a style preference.
Project conventions in the reviewed repository's `CLAUDE.md`/`AGENTS.md`/`README.md` take precedence.

## Java

- Use stable Java 25 features where they simplify the code: records for immutable data carriers, sealed
  types + pattern-matching `switch` for closed hierarchies, text blocks, `var` for obvious local types.
  Preview/incubator features (e.g. `StructuredTaskScope`) need explicit approval and `--enable-preview`.
- Immutability by default: `final` fields, defensive copies or `List.copyOf` for incoming collections,
  no exposed mutable internals. A record with a mutable component is not immutable.
- `equals`/`hashCode`/`compareTo` consistent; no mutable fields in hash keys.
- Null policy explicit (JSpecify `@Nullable`/`@NullMarked` or `Optional` return values); never
  `Optional` fields or parameters.
- Exceptions: no swallowed exceptions, no catching `Exception`/`Throwable` except at boundaries,
  preserve the cause, no exceptions for control flow. Close owned resources with try-with-resources.
- Concurrency: prefer immutable/confined state; concurrent collections do not make compound actions
  atomic; virtual threads for blocking I/O, not CPU work; bound access to scarce resources.
- Time via injected `Clock`/`InstantSource`; `BigDecimal` for money; explicit `Charset` and `Locale`.
- Logging through SLF4J with placeholders (`log.info("x={}", x)`), no secrets or personal data in logs,
  no log-and-rethrow duplication.

## Spring Boot

**Design and wiring**
- Constructor injection only (single constructor, no `@Autowired` needed); no field injection.
- Typed, validated configuration via `@ConfigurationProperties` + `@Validated` instead of scattered `@Value`.
- Keep controllers thin: HTTP mapping and validation only; business logic in services.
- Never expose JPA entities in the API; map to DTOs (records).

**Web layer**
- Validate input with `@Valid`/Bean Validation on request bodies and parameters.
- Centralised error handling with `@RestControllerAdvice` returning `ProblemDetail` (RFC 9457);
  no stack traces or internal messages in responses.
- Correct status codes; idempotent `PUT`/`DELETE`; pagination for unbounded collections.
- Outbound HTTP via `RestClient`/HTTP interface clients with explicit connect/read timeouts.

**Persistence and transactions**
- `@Transactional` on public service methods; it does not apply to self-invocation or private methods.
  `readOnly = true` for queries. No remote calls or long work inside a transaction.
- Watch for N+1 queries (use fetch joins / `@EntityGraph`), `LazyInitializationException` workarounds,
  and `spring.jpa.open-in-view` left enabled.
- Schema changes through Flyway/Liquibase migrations, never `ddl-auto=update` outside local dev.
- Parameterised queries only; no string-concatenated JPQL/SQL.

**Security and operations**
- No secrets in source or committed config; use environment/secret stores.
- Spring Security: deny by default, explicit `permitAll` only where intended, CSRF disabled only for
  stateless token APIs, method security for authorization rules.
- Actuator: expose only needed endpoints (`health`, `info`, `prometheus`); never `env`, `heapdump`,
  `configprops` publicly. Liveness/readiness probes enabled for Kubernetes.
- Graceful shutdown enabled; bounded thread pools, connection pools, queues, and caches.

**Testing**
- Test slices (`@WebMvcTest`, `@DataJpaTest`, `@JsonTest`) over `@SpringBootTest` where possible.
- `@MockitoBean`/`@MockitoSpyBean` instead of the deprecated `@MockBean`/`@SpyBean`.
- Real databases/brokers via Testcontainers (`@ServiceConnection`) rather than H2 substitutes when
  behaviour depends on the database.
- No `Thread.sleep` coordination; use Awaitility or controlled synchronisation.

## Build and dependencies

- Versions managed by the Spring Boot BOM/parent; explicit versions only for unmanaged artifacts
  or justified security overrides (with a comment naming the CVE).
- No snapshots, version ranges, or bundled JARs; correct scopes (`test`, `runtime`, `provided`).
- Compiler `release` matches the target Java version; Maven wrapper committed.
