# Interview Log

## Deloitte India

### Q1. Filter all log entries where latency > 500 ms
### Q2. Map of svcName -> average latency

Answer (Java streams):

```java
record LogEntry(String serviceName, long latencyMs, Instant timestamp) {}

private static final long SLOW_THRESHOLD_MS = 500;

List<LogEntry> slowEntries = logEntries.stream()
        .filter(entry -> entry.latencyMs() > SLOW_THRESHOLD_MS)
        .toList();

Map<String, Double> avgLatencyByService = logEntries.stream()
        .collect(Collectors.groupingBy(
                LogEntry::serviceName,
                Collectors.averagingLong(LogEntry::latencyMs)));
```

Talking points: toList() (Java 16+) is unmodifiable, use collect(Collectors.toList()) if caller mutates; averagingLong returns Double; groupingBy gives HashMap (pass TreeMap::new for sorted keys); raw log lines -> map(this::parse) first; huge file -> Files.lines() streams lazily, don't load all into memory; likely follow-ups: avg of slow calls only (filter then groupingBy), top-N slowest services (sort entrySet by value desc, limit N), p95/p99 instead of avg (avg hides tail latency).

## Worldline

### Q1. Heap vs Stack memory
Stack: per-thread, holds frames (local primitives, references, return addresses), LIFO, auto-freed on method return, fast, small (-Xss), overflow -> StackOverflowError. Heap: shared by all threads, holds objects/arrays, managed by GC (young/old gen), larger (-Xms/-Xmx), exhaustion -> OutOfMemoryError. Local reference lives on stack, object it points to lives on heap (escape analysis may stack-allocate/scalar-replace). Heap data needs synchronization; stack data is thread-confined.

### Q2. HashMap vs Hashtable vs ConcurrentHashMap
HashMap: not thread-safe, one null key + null values, fail-fast iterator, fastest single-threaded. Hashtable: legacy, every method synchronized (whole-table lock), no nulls, poor concurrency. ConcurrentHashMap: thread-safe, Java 8+ uses CAS for empty bins + synchronized on bin head only, lock-free reads, no nulls (ambiguity of get()==null), weakly-consistent iterators (no ConcurrentModificationException), atomic ops like computeIfAbsent/merge. Java 8: bins treeify to red-black tree at 8 entries (capacity >= 64). Use CHM for shared maps; Hashtable never in new code.

### Q3. Comparable vs Comparator
Comparable: java.lang, compareTo(T), defines the class's natural ordering, implemented by the class itself (String, Integer). Comparator: java.util, compare(T,T), external/multiple orderings, no change to the class, composable: Comparator.comparing(Employee::salary).reversed().thenComparing(Employee::name). Use Comparable for one obvious natural order; Comparator for alternate orders or classes you don't own. Keep compareTo consistent with equals (TreeSet/TreeMap rely on it). Avoid `a - b` comparators (overflow) -> use Integer.compare / Comparator.comparingInt.

### Q4. Java 21 new features (LTS, Sep 2023)
Virtual threads (JEP 444, final) - cheap JVM-managed threads for blocking I/O, Executors.newVirtualThreadPerTaskExecutor(). Record patterns (JEP 440) - deconstruct records in instanceof/switch. Pattern matching for switch (JEP 441) - type patterns, guards with `when`, exhaustiveness with sealed types. Sequenced collections (JEP 431) - getFirst/getLast/reversed on List, Deque, LinkedHashSet/Map. Generational ZGC (JEP 439). Key encapsulation API (JEP 452). Previews: string templates, unnamed patterns/variables (_), unnamed classes + instance main, structured concurrency, scoped values.

### Q5. Functional interfaces introduced in which version
Java 8 (2014), alongside lambdas, method references, Streams, java.util.function (Function, Predicate, Consumer, Supplier, BiFunction, UnaryOperator...). @FunctionalInterface annotation = exactly one abstract method (default/static methods and Object's methods allowed). Single-method interfaces like Runnable/Comparator existed earlier but became lambda-usable in 8.

### Q6. Intermediate vs terminal stream operations
Intermediate: return a Stream, lazy, nothing runs until a terminal op - filter, map, flatMap, distinct, sorted, peek, limit, skip. Stateless (filter, map) vs stateful (sorted, distinct - must see all elements). Terminal: trigger the pipeline, return a result/side effect, consume the stream (reuse -> IllegalStateException) - collect, forEach, reduce, count, min/max, anyMatch/allMatch/noneMatch, findFirst/findAny, toList. Short-circuiting: limit, findFirst, anyMatch - can finish on infinite streams. Laziness enables loop fusion (element passes through whole pipeline one at a time).

### Q7. Stream problem: highest-salary employee per department

```java
record Employee(String name, String department, double salary) {}

Map<String, Optional<Employee>> topEarnerByDepartment = employees.stream()
        .collect(Collectors.groupingBy(
                Employee::department,
                Collectors.maxBy(Comparator.comparingDouble(Employee::salary))));

// Without Optional in the result:
Map<String, Employee> topEarnerByDepartment = employees.stream()
        .collect(Collectors.toMap(
                Employee::department,
                Function.identity(),
                BinaryOperator.maxBy(Comparator.comparingDouble(Employee::salary))));

// Variant: department with the highest total salary
Optional<String> highestPayingDepartment = employees.stream()
        .collect(Collectors.groupingBy(
                Employee::department,
                Collectors.summingDouble(Employee::salary)))
        .entrySet().stream()
        .max(Map.Entry.comparingByValue())
        .map(Map.Entry::getKey);
```

Talking points: ties - maxBy keeps one arbitrarily; to keep all top earners group to list then filter by max; money -> BigDecimal in real code; clarify with interviewer which variant they mean.

### Q8. Orchestration vs Choreography (microservices / Saga)
Orchestration: central orchestrator (e.g. Camunda, Temporal, a saga service) commands each step and triggers compensations - clear flow visibility, easy to debug/monitor, single place for business logic; cons: coupling to orchestrator, potential bottleneck/SPOF. Choreography: services react to events on a broker (Kafka), no central brain - loose coupling, independent scaling; cons: flow is implicit and hard to trace, cyclic dependencies, harder compensation. Use orchestration for complex, many-step, ordered workflows with compensations (payments, order fulfilment); choreography for simple flows with few participants or fan-out notifications. Hybrids common. Mention idempotency, outbox pattern, correlation IDs/distributed tracing either way.

### Q9. Encapsulation vs Abstraction
Abstraction: hiding complexity, exposing only what an object does (design level) - via interfaces/abstract classes; answers "what". Encapsulation: bundling data + behaviour and restricting direct access to state (implementation level) - private fields, controlled getters/setters, invariants enforced in methods; answers "how it's protected". Example: List interface = abstraction; ArrayList keeping elementData private and resizing internally = encapsulation. Abstraction reduces what callers must know; encapsulation protects internal state from misuse.

### Q10. Interface vs Abstract class - when to use what
Interface: defines a capability/contract, multiple inheritance of type, no instance state (only constants), default/static (Java 8) and private (Java 9) methods, sealed interfaces (17). Abstract class: shared state + constructors, any access modifiers, partial implementation, single inheritance; template-method pattern. Use interface for "can-do" roles across unrelated classes and as API boundary (default choice, easier to mock/evolve); abstract class for closely related "is-a" family sharing fields/code. Common combo: interface + abstract skeletal implementation (List + AbstractList).

### Q11. Rate Limiter design
Clarify: per user/IP/API key, limits, distributed?, hard vs soft, response (HTTP 429 + Retry-After, X-RateLimit-* headers). Algorithms: Token bucket (allows bursts, refill rate r, capacity b - most common), Leaky bucket (smooth constant outflow, queue), Fixed window counter (simple, boundary burst 2x problem), Sliding window log (accurate, memory heavy), Sliding window counter (weighted prev+current window, good tradeoff). Placement: API gateway/middleware (Spring Cloud Gateway, Nginx, Envoy) vs in-service (Resilience4j, Bucket4j). Distributed: central Redis with atomic Lua script (INCR + EXPIRE, or token bucket state), sharded by key; local cache + periodic sync for latency; consider race conditions, Redis failure -> fail-open vs fail-closed, clock skew. Rules config store, monitoring of throttled requests, multiple tiers (per-user + global).

### Q12. Checked vs unchecked exceptions
Checked: subclasses of Exception (not RuntimeException), compiler forces catch or throws - recoverable conditions outside program control (IOException, SQLException). Unchecked: RuntimeException subclasses - programming errors/invalid state (NullPointerException, IllegalArgumentException, IllegalStateException), not enforced. Error (OutOfMemoryError, StackOverflowError) also unchecked, don't catch. Modern practice (Spring etc.) favours unchecked for business exceptions, wrap checked ones with cause preserved; checked exceptions don't compose with lambdas/streams. Use try-with-resources for AutoCloseable, never swallow exceptions, custom exceptions with meaningful names.

### Q13. Design a public-facing payment API, fault tolerant and highly available
Clarify: scope (authorize/capture/refund/status), scale (TPS, peak), latency SLO, consistency needs, regions, compliance (PCI-DSS, RBI/PSD2, tokenization). Core API: POST /payments (with mandatory Idempotency-Key header), GET /payments/{id}, POST /payments/{id}/refunds, webhooks for async status. Flow: client -> CDN/WAF -> API gateway (authn via OAuth2/API key + mTLS for partners, rate limiting, request validation, TLS termination) -> stateless payment service (horizontally scaled, multi-AZ, behind load balancer) -> PSP/bank/acquirer adapters. Idempotency: store key + request hash + response in DB (unique constraint); retries return the original result, so client/network retries never double-charge. Payment state machine: CREATED -> PENDING -> AUTHORIZED -> CAPTURED / FAILED / REFUNDED, persisted with optimistic locking; every transition is an immutable ledger/audit entry (double-entry). Data: primary relational DB (ACID) with synchronous replica in another AZ and async cross-region replica, sharded by merchant/customer id when needed; Redis for idempotency/rate-limit hot path (not the source of truth). Async/reliability: transactional outbox -> Kafka for events (notifications, reconciliation, webhooks), consumers idempotent, retries with exponential backoff + jitter, dead-letter queue. Downstream failures: timeouts, circuit breaker + bulkhead (Resilience4j), fallback to secondary PSP/route; on unknown outcome (timeout from bank) never assume failure -> mark PENDING and resolve via status query/webhook/reconciliation job. Saga with compensations (void/refund) for multi-step flows. HA: active-active multi-region or active-passive with health-check failover (DNS/global LB), no single point of failure, graceful degradation, RPO ~0 / RTO minutes, regular chaos/DR drills. Security: PCI scope minimisation via tokenization (never store raw PAN/CVV), encryption at rest/in transit, HMAC-signed webhooks, secrets in vault, replay protection (timestamp + nonce). Observability: correlation IDs, distributed tracing, metrics (success rate, p99 latency, error by PSP), alerting, daily reconciliation against PSP settlement files. Trade-offs to mention: consistency over availability for money movement (CP for ledger writes), at-least-once delivery + idempotency = effectively exactly-once, sync vs async confirmation.

## SQL (company not specified)

### Q1. Goals and teams: return goals scored in under 30 seconds
Tables: teams(team_id, team_name), goals(goal_id, match_id, team_id, player_name, goal_time_seconds). Task: team name, player name, goal time for every goal under 30 seconds, fastest first.

```sql
SELECT t.team_name,
       g.player_name,
       g.goal_time_seconds
FROM goals g
INNER JOIN teams t ON g.team_id = t.team_id
WHERE g.goal_time_seconds < 30
ORDER BY g.goal_time_seconds ASC;
```

Talking points: strictly under 30 so a goal at exactly 30 is excluded; likely follow-ups: count per team (GROUP BY t.team_name + COUNT(*)); include teams with zero fast goals -> LEFT JOIN from teams with the time filter in the ON clause (in WHERE it turns the left join into an inner join); if time is stored as goal_minute + goal_second use goal_minute = 0 AND goal_second < 30; harder version: fastest goal per team with ROW_NUMBER() OVER (PARTITION BY team_id ORDER BY goal_time_seconds).

## Wissen Technology (Hyderabad, Java developer, Round 1)

### Q1. Java output-type questions: subclass (inheritance) rules and HashMap internals
Subclass rules: static blocks run once at class load, parent before child; object creation order is parent instance initialisers then parent constructor, then child instance initialisers then child constructor (implicit super() first); instance methods are resolved by runtime type (overriding) but fields and static methods by reference type (hiding); overloading is resolved at compile time by reference type; a parent constructor calling an overridden method runs the child's version before child fields are initialised (sees null/0); overriding rules: same signature, covariant return, access cannot be reduced, no broader checked exceptions, private/static/final cannot be overridden. HashMap: hash spread (h ^ h>>>16), index = (capacity-1) & hash, equals used on put and get, list becomes red-black tree at 8 nodes if capacity >= 64 (else resize), resize doubles at 0.75 load (16 -> 12 entries) and entries stay or move by oldCapacity, capacity is a power of two for the bitmask index.

### Q2. DSA (easy): find missing positives in an array

```java
public static int findSmallestMissingPositive(int[] numbers) {
    Set<Integer> presentNumbers = new HashSet<>();
    for (int number : numbers) {
        if (number > 0) {
            presentNumbers.add(number);
        }
    }
    int candidate = 1;
    while (presentNumbers.contains(candidate)) {
        candidate++;
    }
    return candidate;
}

public static List<Integer> findAllMissingPositives(int[] numbers) {
    Set<Integer> presentNumbers = new HashSet<>();
    int largest = 0;
    for (int number : numbers) {
        if (number > 0) {
            presentNumbers.add(number);
            largest = Math.max(largest, number);
        }
    }
    List<Integer> missingNumbers = new ArrayList<>();
    for (int candidate = 1; candidate < largest; candidate++) {
        if (!presentNumbers.contains(candidate)) {
            missingNumbers.add(candidate);
        }
    }
    return missingNumbers;
}
```

Talking points: clarify which variant (smallest missing vs all missing up to max); edge cases: empty array, all negatives or zeros, duplicates, unsorted; O(n) time, O(n) space; O(1) space follow-up: place each value v in 1..n at index v-1 by swapping, then scan for the first mismatch (mention only if asked).

## Wissen Technology (Hyderabad, Java developer, Round 3)

### DSA

### Q1. Output the median from a running stream of numbers ([LeetCode 295: Find Median from Data Stream](https://leetcode.com/problems/find-median-from-data-stream/))

Two heaps: a max-heap for the lower half, a min-heap for the upper half; keep sizes equal or the lower half one larger.

```java
class MedianFinder {
    private final PriorityQueue<Integer> low = new PriorityQueue<>(Comparator.reverseOrder()); // max-heap
    private final PriorityQueue<Integer> high = new PriorityQueue<>();                         // min-heap

    public void addNum(int num) {
        low.offer(num);
        high.offer(low.poll());            // move the largest of the lower half up
        if (high.size() > low.size()) {
            low.offer(high.poll());        // rebalance
        }
    }

    public double findMedian() {
        return low.size() > high.size()
                ? low.peek()
                : (low.peek() + (double) high.peek()) / 2;
    }
}
```

`addNum` O(log n), `findMedian` O(1), space O(n).

### SQL

### Q2. Table (sale_date, sales): find rows where sales are higher than the previous date (similar to [LeetCode 197: Rising Temperature](https://leetcode.com/problems/rising-temperature/))

```sql
SELECT sale_date, sales
FROM (
    SELECT sale_date, sales,
           LAG(sales) OVER (ORDER BY sale_date) AS prev_sales
    FROM sales
) t
WHERE sales > prev_sales;
```

`LAG` compares with the previous row (the previous recorded date). If it must be exactly the previous calendar day, self-join: `JOIN sales p ON p.sale_date = s.sale_date - INTERVAL '1' DAY WHERE s.sales > p.sales`.

### LLD

### Q3. Rate limiter algorithms; design and code one (coded: sliding window log; also asked at Worldline Q11)

- Token bucket: refill tokens at rate r up to capacity b; each request takes a token, reject if none left.
- Leaky bucket: requests enter a fixed-size queue that drains at a constant rate; reject when the queue is full.
- Fixed window counter: count requests per window (key = client + window start); reject when count > limit; reset each window.
- Sliding window log: keep a timestamp per request; drop timestamps older than now - window; allow if remaining count < limit, then add now.
- Sliding window counter: estimate = current count + previous count x (overlap fraction of previous window); allow if estimate < limit.

## Charles Schwab (Java Lead)

### Java

### Q1. Find all elements that have duplicates in [1, 2, 3, 2, 4, 5, 3] using Java streams

```java
List<Integer> duplicates = List.of(1, 2, 3, 2, 4, 5, 3).stream()
        .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
        .entrySet().stream()
        .filter(e -> e.getValue() > 1)
        .map(Map.Entry::getKey)
        .toList();   // [2, 3]
```

### Q2. String vs StringBuilder vs StringBuffer

String: immutable, thread-safe because it can't change, literals pooled; every "modification" creates a new object. StringBuilder: mutable, not synchronized, fastest; use for building strings in a loop. StringBuffer: mutable, every method synchronized, slower; legacy, rarely needed (prefer StringBuilder, or confine it to one thread). `+` in a single expression is optimized by the compiler (`invokedynamic` since Java 9), but `+=` in a loop creates a new String each iteration, so use StringBuilder there.

### Q3. Java 8, Java 11 and Java 21 features (Java 21 also asked at Worldline)

**Java 8 (LTS, 2014)**
- Lambda expressions and functional interfaces (`java.util.function`: Function, Predicate, Supplier, Consumer); `@FunctionalInterface`
- Method references (`String::length`)
- Streams API (`filter`, `map`, `collect`, `Collectors.groupingBy`, parallel streams)
- `Optional`
- Default and static methods in interfaces
- New Date/Time API `java.time` (`LocalDate`, `LocalDateTime`, `Instant`, `Duration`)
- `CompletableFuture`
- Collection/Map additions: `forEach`, `removeIf`, `computeIfAbsent`, `merge`, `getOrDefault`
- `String.join`, `StringJoiner`, `Base64`

**Java 11 (LTS, 2018), including 9 and 10**
- `var` for local variables (10), also in lambda parameters (11)
- Immutable collection factories `List.of`, `Set.of`, `Map.of` (9), `List.copyOf` (10)
- Module system / JPMS (9), JShell (9)
- Private methods in interfaces (9)
- Stream additions `takeWhile`, `dropWhile`, `ofNullable` (9); `Collectors.toUnmodifiableList` (10)
- `Optional.orElseThrow()` (10), `Optional.isEmpty()` (11)
- String methods `isBlank`, `strip`, `lines`, `repeat` (11)
- `Files.readString` / `writeString` (11)
- Standard HTTP Client `java.net.http.HttpClient` (11)
- Run a single source file directly: `java Hello.java` (11)
- `Predicate.not` (11)

**Java 21 (LTS, 2023), including 12 to 20**
- Switch expressions with `->` and `yield` (14)
- Text blocks `"""` (15)
- Records (16)
- Pattern matching for `instanceof` (16)
- `Stream.toList()` (16)
- Sealed classes and interfaces (17)
- Virtual threads (21)
- Pattern matching for `switch` and record patterns (21)
- Sequenced collections: `getFirst`, `getLast`, `reversed` (21)

### Q4. Second-highest salaried employee using Java streams

```java
Optional<Employee> second = employees.stream()
        .sorted(Comparator.comparingDouble(Employee::salary).reversed())
        .skip(1)
        .findFirst();
```

If two employees share the top salary, the second-highest *salary* is asked for: take distinct salaries first.

```java
Optional<Double> secondSalary = employees.stream()
        .map(Employee::salary)
        .distinct()
        .sorted(Comparator.reverseOrder())
        .skip(1)
        .findFirst();
```

### Q5. Singleton implementation and how to break it

Bill Pugh (lazy, thread-safe, no locking; the holder class loads on first `getInstance()`):

```java
public final class Config {
    private Config() {}
    private static class Holder { static final Config INSTANCE = new Config(); }
    public static Config getInstance() { return Holder.INSTANCE; }
}
```

Ways to break it, and the fix:
- Serialization: deserializing creates a new object. Fix: `private Object readResolve() { return getInstance(); }`
- Reflection: `constructor.setAccessible(true)` then `newInstance()`. Fix: throw from the constructor if the instance already exists.
- Cloning: if it implements `Cloneable`. Fix: don't implement it, or throw `CloneNotSupportedException` from `clone()`.
- Multiple class loaders: each loads its own copy (one instance per class loader).

Best protection: enum singleton `enum Config { INSTANCE; }`. The JVM guarantees one instance and blocks reflection and serialization attacks.

### Microservices

### Q6. API A calls a slow third-party payment API B. The user is waiting. What does the user see?

Don't keep the user waiting on B. API A saves the payment as PROCESSING with a reference ID, calls B asynchronously, and immediately returns a status link (e.g. `/payments/{referenceId}`) with "Payment is being processed, don't refresh or pay again".

When B finishes, A learns the result through B's webhook (B calls A's callback URL) or by A polling B's status API. A then updates the payment record to SUCCESS or FAILED, so the status page/link shows the final result; optionally send an email/SMS.

### Spring Boot

### Q7. Micronaut vs Spring Boot

- Dependency injection: Micronaut resolves DI and AOP at compile time (annotation processors); Spring Boot does it at runtime (classpath scanning, reflection, proxies).
- Startup and memory: Micronaut starts faster and uses less memory; Spring Boot is slower to start and heavier.
- Native image: Micronaut works with GraalVM native out of the box; Spring Boot 3 supports it through AOT processing, with more configuration.
- Ecosystem: Spring Boot is far larger and more mature (Spring Data, Security, Cloud), with more developers and docs; Micronaut's is smaller.
- When to use: Micronaut for serverless and small microservices where cold start and memory matter; Spring Boot as the default for enterprise applications.

### Q8. Challenges faced during migration and how you mitigated them
