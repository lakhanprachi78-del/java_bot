# LOS Chatbot Backend — Python → Java Migration Architecture

**Status:** Planning document, written before any Java code exists. Use this as the build order.
**Source analyzed:** `backend_application_1.zip` (FastAPI/Python), 13 source files, fully read.
**Frontend:** `frontend_1.zip` upload was corrupted (truncated — no end-of-central-directory record, nothing could be extracted). Not needed for this document since the frontend only talks to the backend over the HTTP contract documented in §8 below — but re-upload it if you want it cross-checked against that contract once you're building the Angular-facing pieces.

---

## 1. What this service actually is

A FastAPI app that answers loan-application status questions two ways:

1. **`/chat`** — free text, goes through an LLM (OpenRouter) that can call a fixed set of read-only "tools" against the database, with a Python-side authorization layer the LLM can never bypass.
2. **`/chat/direct`** — structured menu input from the frontend (status dropdown, application ID field, etc.) that skips the LLM entirely and calls the data layer directly, producing byte-identical reply formatting.

If a question isn't about application data, it's silently handed off (HTTP call) to a separate "SkaleUp RAG" backend and that answer is returned instead.

Everything is intentionally layered so that **authorization and SQL safety live in exactly one place** (the repository layer), and the LLM is structurally incapable of bypassing them — this property must be preserved 1:1 in Java. It's the single most important thing not to regress on during migration.

---

## 2. Target stack

| Concern | Python (current) | Java (target) |
|---|---|---|
| Language / runtime | Python 3.11/3.14 | **Java 21** (LTS) |
| Web framework | FastAPI + Uvicorn | **Spring Boot 3.3.x** (`spring-boot-starter-web`, embedded Tomcat) |
| Request/response validation | Pydantic `BaseModel` | **Jakarta Bean Validation** (`jakarta.validation`) on Java `record` DTOs |
| ORM | SQLAlchemy 2.0 (declarative + Query API) | **Spring Data JPA / Hibernate 6** |
| Main DB driver | `psycopg2-binary` (Postgres) | **`org.postgresql:postgresql`** (JDBC) |
| Log DB driver | stdlib `sqlite3` (raw SQL, 2 separate `.db` files) | **`org.xerial:sqlite-jdbc`** + `JdbcTemplate` (2 separate `DataSource`s — see §7) |
| Outbound HTTP (OpenRouter, RAG handoff) | `requests` | **Spring `RestClient`** (sync, matches current sync-blocking style) |
| Config / env | `python-dotenv` + `os.getenv` in `config.py` | **`application.yml`** + `@ConfigurationProperties` |
| JSON | stdlib `json` / Pydantic | **Jackson** (`ObjectMapper`, ships with Spring Boot) |
| Build tool | `pip` / `requirements.txt` | **Maven** (`pom.xml`) — pick Gradle instead only if your team already standardizes on it |
| DTO style | `@dataclass`, `dict` | **Java `record`** wherever the Python used a `dataclass` or a bare `dict` shape |

**Why Spring Boot specifically:** it's the closest architectural match to what this codebase already does — dependency-injected services, declarative validation, a thin controller layer, an ORM-backed repository layer — so the migration is close to 1:1 module-for-module rather than a redesign.

---

## 3. Target package structure

```
com.ugrocapital.losbot
├── LosBotApplication.java                 # main() — replaces main.py's app bootstrap
│
├── config/
│   ├── AppProperties.java                 # replaces config.py (typed @ConfigurationProperties)
│   ├── PrimaryDataSourceConfig.java        # Postgres — application/applicant tables
│   ├── QueryLogDataSourceConfig.java       # SQLite — los_query_log.db
│   ├── FeedbackLogDataSourceConfig.java    # SQLite — los_feedback_log.db
│   ├── CorsConfig.java                     # replaces main.py's CORSMiddleware block
│   └── RestClientConfig.java               # shared RestClient bean(s) for OpenRouter + RAG handoff
│
├── auth/
│   ├── AuthContext.java                    # record(username, role) — replaces auth.py's @dataclass
│   ├── AuthService.java                    # replaces auth.py's HARDCODED_SESSIONS + get_auth_context
│   ├── UnknownSessionException.java
│   └── GreetingService.java                # replaces build_greeting / get_time_based_greeting
│
├── entity/
│   ├── LoanApplication.java                # JPA entity ← models.py LoanApplication
│   ├── ApplicationApplicant.java           # JPA entity ← models.py ApplicationApplicant
│   └── ApplicationApplicantEmail.java      # JPA entity ← models.py ApplicationApplicantEmail
│
├── repository/
│   ├── LoanApplicationJpaRepository.java   # Spring Data interface, base CRUD only
│   ├── ApplicationQueryRepository.java     # Criteria-API queries ← repository.py's search_* functions
│   └── ApplicationSerializer.java          # ← repository.py's _serialize() + _redact_sensitive()
│
├── tools/
│   ├── ToolSchemas.java                    # ← tools.py TOOL_SCHEMAS + STATUS_CODES (as Java constants)
│   ├── ToolDispatcher.java                 # ← tools.py dispatch()
│   └── ToolException.java                  # ← tools.py ToolError
│
├── chat/
│   ├── ChatEngineService.java              # ← chat_engine.py run_chat_turn() + SYSTEM_PROMPT
│   ├── OutOfScopeException.java
│   ├── SmallTalkDetector.java              # ← chat_engine.py _is_pure_smalltalk / _SMALLTALK_RE
│   ├── SearchFilterDescriber.java          # ← _describe_search_filters / _narrow_hint
│   └── TokenAccumulator.java               # ← token_tracker.py
│
├── llm/
│   ├── LlmClient.java                      # ← llm_client.py call_llm()
│   ├── ChatMessage.java                    # role/content/tool_calls/name/tool_call_id model
│   └── LlmException.java
│
├── directquery/
│   ├── DirectQueryService.java             # ← direct_queries.py run_direct_query()
│   ├── DirectQueryException.java
│   └── RelativeDatePhraseResolver.java     # ← resolve_date_phrase()
│
├── format/
│   └── ReplyFormatter.java                 # ← formatting.py (format_search_reply etc.)
│
├── log/
│   ├── QueryLogService.java                # ← query_log.py
│   └── FeedbackLogService.java             # ← feedback_log.PY
│
├── controller/
│   ├── ChatController.java                 # POST /chat, POST /chat/direct, POST /chat/reset
│   ├── SessionController.java              # GET /whoami, GET /greet
│   ├── FeedbackController.java             # POST /api/feedback, POST /api/feedback/detail
│   └── HealthController.java               # GET /health
│
├── dto/
│   ├── ChatRequest.java / ChatResponse.java
│   ├── DirectChatRequest.java
│   ├── FeedbackRequest.java / FeedbackResponse.java
│   ├── FeedbackDetailRequest.java / FeedbackDetailResponse.java
│   └── WhoAmIResponse.java
│
├── exception/
│   └── GlobalExceptionHandler.java         # @ControllerAdvice — replaces main.py's try/except → HTTPException
│
└── dev/
    └── SeedDataRunner.java                 # ← seed_data.py, gated behind a "local"/"dev" Spring profile
```

This mirrors the existing Python README's "quick orientation" table almost file-for-file — that mapping is intentional so your team's mental model of "where do I go to change X" transfers directly.

---

## 4. Module-by-module migration notes

### 4.1 `config/AppProperties.java` ← `config.py`
Everything environment-specific stays in one place, same principle as the Python version's comment ("changing `.env`, not application code").

```yaml
# application.yml
los:
  database:
    url: postgresql://username:password@host:port/databasename
  llm:
    api-key: ${OPENROUTER_API_KEY:}
    url: https://generativelanguage.googleapis.com/v1beta/openai/chat/completions
    model: ${LLM_MODEL:openai/gpt-4o-mini}
  app:
    max-rows-returned: ${MAX_ROWS_RETURNED:25}
  handoff:
    rag-chat-url: ${RAG_CHAT_URL:http://127.0.0.1:8000/api/chat}
  support:
    email: ${SUPPORT_EMAIL:skaleupprodsupport@ugrocapital.com}
```
Bind with `@ConfigurationProperties(prefix = "los")`. Secrets (`OPENROUTER_API_KEY`, DB password) come from env vars / a secrets manager in every profile except local dev — don't commit them, same discipline the `.env` file implies today.

### 4.2 `entity/` ← `models.py`
Straight JPA translation of the three tables — `application`, `application_applicant`, `application_applicant_email`, all in the `dmcredit` schema. Preserve exactly:
- `@Table(name = "application", schema = "dmcredit")` etc.
- The FK relationship `application_applicant.applicationkey → application.applicationkey` and `application_applicant_email.appapplicantkey → application_applicant.appapplicantkey`.
- `@OneToMany(fetch = FetchType.EAGER)` (or a JPQL `JOIN FETCH`) on `applicants`/`emails` to match `lazy="joined"` — the Python code relies on these being loaded together, not lazily, because `_serialize()` reads nested applicant/email data after the session pattern closes.
- **Do not let Hibernate auto-DDL touch this in production** (`ddl-auto: validate`, never `update`/`create`) — same warning as the Python README ("point this at the real table rather than letting SQLAlchemy create a new one").

### 4.3 `repository/` ← `repository.py` — **the security-critical file, read this twice**
This is where five non-negotiable rules live in the Python version, and they must transfer exactly:

1. **No string-concatenated SQL** — use JPA Criteria API or `@Query` with bound parameters only. Never `String.format` a query.
2. **Every method takes typed args**, never a raw query string from the caller.
3. **Every method enforces the row cap** (`min(limit, maxRowsReturned)`), applied server-side, never trusted from the LLM/caller.
4. **Every method takes an `AuthContext` and applies ownership scoping before running the query** — this is the Java equivalent of `_scope_to_owner()`. Concretely: build every `CriteriaQuery` so that unless `auth.role().equals("admin")`, a `WHERE lower(trim(createdby)) = :u OR lower(trim(lstupdatedby)) = :u` predicate is *always* present. There is no code path in this file that skips it.
5. **Not-found and not-authorized return the same thing** (`Optional.empty()` / empty list) — never a 403, never a different message shape — so the API never confirms a record exists to someone who can't see it.

Also carry over, unchanged:
- **Column scoping** (`_column_scoped`): don't `SELECT *` — fetch only the ~8 application columns and ~3 applicant/email columns actually used, via JPQL projections or `@EntityGraph`, not the ~30 unused columns on the real table.
- **`_redact_sensitive()`**: three `Pattern`s (PAN format `[A-Z]{5}[0-9]{4}[A-Z]`, password/OTP/PIN keyword, 9+ digit runs) applied to `remarks` before it's returned to *any* caller — LLM path or direct path. Also the 500-char truncation with a `"... [truncated]"` suffix. Put this on the shared serializer, not duplicated per query method, exactly as Python does.
- **`_serialize()`**: application → applicant/co-applicant name grouping by `applicanttype`, applicant email joined only for `APPLICANT` type, date formatting `dd MMM yyyy, HH:mm:ss` (Java `DateTimeFormatter` equivalent of Python's `%d %b %Y, %H:%M:%S`).

Method-for-method mapping (same signatures, same defaults: `DEFAULT_RESULT_LIMIT = 5`):

| Python function | Java method |
|---|---|
| `get_application_by_id` | `Optional<ApplicationDto> getApplicationById(String id, AuthContext auth)` |
| `search_by_applicant_name` | `SearchResult searchByApplicantName(String name, AuthContext auth, int limit)` |
| `search_by_applicant_email` | `SearchResult searchByApplicantEmail(String email, AuthContext auth, int limit)` |
| `search_by_created_by` | `SearchResult searchByCreatedBy(String name, AuthContext auth, int offset, int limit)` |
| `search_by_date` | `SearchResult searchByDate(LocalDate date, AuthContext auth, int offset, int limit)` |
| `search_by_date_range` | `SearchResult searchByDateRange(LocalDate start, LocalDate end, AuthContext auth, int offset, int limit)` |
| `search_by_status` | `SearchResult searchByStatus(String statusCode, AuthContext auth, int offset, int limit)` |
| `combined_search` | `SearchResult combinedSearch(CombinedSearchParams params, AuthContext auth)` |

`SearchResult` is a `record(List<ApplicationDto> results, long totalMatches, int returned, boolean hasMore)` — direct match for the Python dict shape `{"results", "total_matches", "returned", "has_more"}`.

> ⚠️ Note the applicant-name/email search joins `ApplicationApplicant` filtered to `applicanttype IN ('APPLICANT','COAPPLICANT','GUARANTOR')` (`ANY_APPLICANT_TYPE`), and `combined_search` must join `ApplicationApplicant` **at most once** even when both `applicant_name` and `applicant_email` filters are supplied together — the Python code has an explicit guard for this (`if not applicant_name: q = q.join(...)`). Replicate that guard or you'll get a duplicate-join query error / duplicated rows in Java too.

### 4.4 `tools/` ← `tools.py`
This is the entire interface the LLM has to your data — port the tool schema list as-is (7 real tools + the `not_in_scope` pseudo-tool), and the `STATUS_CODES` list *exactly* (order and spelling matter — it's shown verbatim to the LLM and must match real DB values):

```java
public static final List<String> STATUS_CODES = List.of(
    "pre-login", "pre-login review", "pre login discrepant",
    "sales discrepant", "credit assessment", "pre-disbursement",
    "approved", "rejected", "sent to lms"
);
```

`ToolDispatcher.dispatch(String toolName, Map<String,Object> arguments, AuthContext auth)` — **`AuthContext` is a Java method parameter injected by the caller, never a field the LLM can populate via `arguments`.** This is the single most important security property in the whole system: if you ever add `username` as a tool-schema property "for convenience," you've reopened the exact prompt-injection privilege-escalation hole the Python comments call out explicitly. Keep it structurally impossible, not just documented against.

### 4.5 `chat/ChatEngineService.java` ← `chat_engine.py` (the biggest file — plan real time for this one)
Port in this order:

1. **`SYSTEM_PROMPT`** — copy verbatim as a Java text block (`"""..."""`), keeping the exact two-part structure: a large **static** section (cacheable prefix — don't parameterize anything in it) + a small **`## SESSION CONTEXT`** section templated per-request with `username`, `role`, `today`, `supportEmail`. If you later wire up prompt caching on whichever LLM provider you use in Java, this split is what makes that possible — don't collapse it into one template.
2. **`_is_pure_smalltalk`** → `SmallTalkDetector`, same anchored regex, same purpose (skip sending ~2,000 tokens of tool schemas on a bare "thanks"/"hi").
3. **The tool-calling loop** (`for _ in range(MAX_TOOL_ROUNDS)`): call LLM → if no `tool_calls`, return text → if it's `not_in_scope`, throw `OutOfScopeException(topic)` → if it's exactly one search/lookup tool call, use the **fast path** (skip the second LLM round-trip, format the reply deterministically via `ReplyFormatter` — this is a real cost optimization, keep it) → otherwise dispatch every tool call, append `tool` result messages, loop again. Cap at `MAX_TOOL_ROUNDS = 4`, same fallback message on exhaustion.
4. **`MAX_HISTORY_MESSAGES = 20`** trimming logic, including the "walk forward to the next user message" fix so you never truncate mid tool-call exchange.
5. **`_persist()`** — every turn collapses to exactly one user + one assistant message in what's stored/resent later (raw tool JSON is never persisted) — keep this, it's what keeps prompt size bounded over a long session.
6. **`SEARCH_TOOLS` / `LOOKUP_TOOLS`** constant sets, and `_describe_search_filters` / `_narrow_hint` → `SearchFilterDescriber`, used both for the fast-path formatter and to build the "add a date/status/ID to narrow it down" hint while never re-suggesting a filter kind already used.

Conversation history storage: Python uses an in-memory `dict[str, list[dict]]` (`CONVERSATIONS`) and the file explicitly flags this as dev-only ("won't survive a restart or work across multiple workers"). **Do the same explicitly** in Java for parity during migration — a `ConcurrentHashMap<String, List<ChatMessage>>` bean — but flag it exactly as loudly for the production hardening pass: swap for Redis (`spring-data-redis`) or a DB-backed session table before this runs on more than one instance.

### 4.6 `directquery/DirectQueryService.java` ← `direct_queries.py`
Straight port. Keep the field-name contract identical to what the frontend already sends (`application_id`, `status`, `applicant_name`, `applicant_email`, `date_time`), the same regex for a valid application ID (`^[A-Za-z0-9-]{5,30}$`), and the same relative-date phrase table (`today`, `yesterday`, `tomorrow`, `this week`, `last week`, `this month`, `last month`, `this year`, `last year`, plus 3 literal date formats). This whole module exists to avoid an LLM round-trip when the frontend's menu already picked the exact field+value — preserve that shortcut; it never touches `ToolDispatcher`/`ChatEngineService`, only the repository + formatter.

### 4.7 `format/ReplyFormatter.java` ← `formatting.py`
Pure, deterministic string building — no LLM involved. Copy the exact field order, the exact omit-if-missing behavior (never show a blank/None field), status upper-casing, and the exact count-line wording ("There are N applications... — showing the 5 most recent.") since this is the *single source of truth* for reply shape shared between the direct-query path and the LLM fast-path — a change here changes both.

### 4.8 `log/QueryLogService.java` + `FeedbackLogService.java` ← `query_log.py` + `feedback_log.PY`
See §7 below for *why* these get their own `DataSource`s. Logic to preserve:
- `query_log`: every query is logged as its own row, **no dedup at log time**; dedup only happens at feedback time, only against a row with the *exact same normalized question text* that already has feedback — port `_normalize()` (lowercase, collapsed whitespace) exactly.
- `feedback_log`: append-only, one row per submission, `tags` stored as a JSON array string (use Jackson to serialize, same as Python's `json.dumps`), only written when at least one tag or a non-empty comment exists (enforced in the controller, same as `main.py` does today).
- Both: **never throw** on a logging failure — catch and log server-side only, exactly like the Python `try/except Exception` wrappers, because a logging hiccup must never break the actual chat response to the user.

### 4.9 `llm/LlmClient.java` ← `llm_client.py`
Thin wrapper, `RestClient` instead of `requests`. Same payload shape (`model`, `messages`, `temperature: 0`, optional `tools` + `tool_choice: "auto"`), same header (`Authorization: Bearer <key>`), same 30s timeout, same "raise if `OPENROUTER_API_KEY` unset" guard, same "raise if `choices` missing/empty" guard. Return the raw assistant message plus `usage` so `TokenAccumulator` can sum it — don't reshape it into a Java-idiomatic object at this layer; keep it structurally close to the OpenAI-compatible wire format since `ChatEngineService` needs to re-append it into `messages` verbatim for the next round.

### 4.10 `auth/` ← `auth.py`
**This stays the one file that changes when real session management gets wired up** — same principle as the Python doc. For now, port the hardcoded `Map<String, SessionEntry>` (session ID → username/role) 1:1, including the display-name table and the two greeting helper methods. Throw `UnknownSessionException` on a miss — **never fall back to a default user**, that would be an authorization bypass, same warning as the Python docstring.

### 4.11 `controller/` + `dto/` ← `main.py`
Straight FastAPI-route → Spring `@RestController`-method translation. Endpoint-by-endpoint detail is in §8 — the contract must not change shape, since Angular is coded against it today. Key behaviors to preserve:
- `/chat`: 400 on blank message, 401 on unknown session, generic 500 message on any other exception (**never leak stack traces / DB details to the client** — log server-side, return a fixed friendly string, exactly like Python's `print("ERROR:", ...)` + generic `HTTPException(500, ...)`).
- The `OutOfScopeException` → RAG-backend-handoff logic in `/chat`, including the `is_handoff` loop-prevention flag, and using the *clean isolated topic* from the tool call (not the raw user message, which may have search context glued on) when forwarding.
- `/chat/direct`: on a validation-style error (`DirectQueryException`), return it as a normal 200 reply (not a 500) — the frontend doesn't need to know this path skipped the LLM.
- `/api/feedback/detail`: 400 if neither `tags` nor `comment` is present — server-side hard rule, not just a frontend nicety.

---

## 5. DTO translation pattern (Pydantic → Java `record`)

Every Pydantic `BaseModel` becomes a Java `record` with Bean Validation annotations. Example:

```java
// ChatRequest.java  ← main.py ChatRequest
public record ChatRequest(
    @NotBlank String sessionId,
    @NotBlank String message,
    boolean isHandoff   // defaults to false via a compact constructor or @JsonProperty default
) {}
```
```java
// FeedbackDetailRequest.java ← main.py FeedbackDetailRequest
public record FeedbackDetailRequest(
    Integer queryId,           // nullable — same as Optional[int] = None
    String queryText,
    String answerText,
    boolean positive,
    @NotNull List<String> tags,
    @Size(max = 2000) String comment
) {}
```
Use `@JsonProperty("session_id")` (or a global `PropertyNamingStrategies.SNAKE_CASE` Jackson config) if the frontend's JSON keys are `snake_case` — check this once the frontend zip is re-uploaded and readable; don't guess field casing.

---

## 6. Preserving the two `NOTE` warnings from the Python docstrings

Both are still true in Java and worth keeping visible in code comments, not just this doc:

1. **Conversation history is in-memory** (`ConcurrentHashMap`) — fine for one instance/local testing, breaks the moment you run >1 pod/worker or restart. Swap for Redis or a DB table before any real multi-instance deployment.
2. **Session → identity resolution is fully hardcoded** in `AuthService` — when real auth (JWT/SSO) is wired up, `AuthService` is the *only* class that should change; every other class already only depends on `AuthContext` and doesn't know how it was produced. Don't let real-auth logic leak into controllers or the chat engine.

---

## 7. Data layer: three databases, three `DataSource`s

The Python app talks to **three separate databases** and this must carry over as three separate Spring `DataSource`/`JdbcTemplate` (or `EntityManagerFactory`) beans — don't try to force them into one:

| DB | Engine | Used by | Java wiring |
|---|---|---|---|
| Main LOS data (`application`, `application_applicant`, `application_applicant_email`) | PostgreSQL | `repository/` (JPA) | Primary `@Primary DataSource` + Hibernate `EntityManagerFactory` |
| `los_query_log.db` | SQLite | `log/QueryLogService` | Secondary `DataSource` (`sqlite-jdbc`) + plain `JdbcTemplate`, **not** JPA — mirrors the Python file's raw-SQL, no-ORM style intentionally, since it's a simple append/counter table |
| `los_feedback_log.db` | SQLite | `log/FeedbackLogService` | Third `DataSource` + `JdbcTemplate`, same reasoning |

Spring Boot needs explicit multi-`DataSource` configuration (one `@Bean DataSource`, one `@Bean JdbcTemplate` per log DB, qualified with `@Qualifier`) since auto-configuration only wires one `DataSource` for you. Keep the SQLite files as simple local files for now, exactly as Python does — this is a reasonable thing to flag for a later hardening pass (e.g. moving both logs into Postgres tables instead of SQLite files) but is **out of scope for a straight migration**; match behavior first, improve architecture second.

Table DDL to port into each SQLite log DB's schema (create-if-not-exists on startup, same as Python's `init_db()` called at module-import time — in Java, run this from each service's `@PostConstruct` or a Flyway/plain-SQL migration):

```sql
-- los_query_log.db
CREATE TABLE IF NOT EXISTS query_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    query TEXT NOT NULL,
    normalized_query TEXT NOT NULL,
    answer TEXT,
    source TEXT NOT NULL DEFAULT 'chat',
    thumbs_up INTEGER NOT NULL DEFAULT 0,
    thumbs_down INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL,
    last_seen TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_los_query_log_normalized ON query_log (normalized_query);
CREATE INDEX IF NOT EXISTS idx_los_query_log_created_at ON query_log (created_at);

-- los_feedback_log.db
CREATE TABLE IF NOT EXISTS feedback_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    query_id INTEGER,
    query_text TEXT,
    answer_text TEXT,
    positive INTEGER NOT NULL DEFAULT 0,
    tags TEXT NOT NULL DEFAULT '[]',
    comment TEXT,
    created_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_los_feedback_log_query_id ON feedback_log (query_id);
CREATE INDEX IF NOT EXISTS idx_los_feedback_log_created_at ON feedback_log (created_at);
```

---

## 8. HTTP contract Angular is coded against — must not change shape

This is the frozen contract. Port every endpoint with the same path, method, request/response field names, and status codes. (Re-verify field casing against the actual Angular services once `frontend_1.zip` is re-uploaded successfully.)

| Method | Path | Request body | Response body | Notes |
|---|---|---|---|---|
| `POST` | `/chat` | `{session_id, message, is_handoff?}` | `{reply, session_id, has_more?, query_id?}` | 400 if `message` blank; 401 if unknown session |
| `POST` | `/chat/direct` | `{session_id, field, value, offset?}` | `{reply, session_id, has_more?, query_id?}` | `field` ∈ `application_id\|status\|applicant_name\|applicant_email\|date_time` |
| `GET` | `/whoami?session_id=` | — | `{username, role, display_name}` | 401 if unknown session |
| `POST` | `/chat/reset?session_id=` | — | `{status: "reset"}` | |
| `GET` | `/greet?session_id=` | — | `{greeting}` | |
| `GET` | `/health` | — | `{status: "ok"}` | |
| `POST` | `/api/feedback` | `{query_id, positive}` | `{success}` | |
| `POST` | `/api/feedback/detail` | `{query_id?, query_text?, answer_text?, positive, tags[], comment?}` | `{success, id?}` | 400 if no tags and no comment |

CORS: currently wide open (`allow_origins=["*"]`, all methods/headers) — the Python comment flags this as "for testing (later restrict)". Port it as-is for now with the same comment, but this is a good candidate to tighten (specific Angular origin) as part of the Java rollout rather than carrying the `*` forward into production.

---

## 9. Recommended build order

Follow this sequence — each step is independently testable before moving to the next, same philosophy as the Python file split:

1. **Skeleton**: Spring Boot project, `pom.xml`, `application.yml`, package structure, `/health` endpoint. Confirm it boots.
2. **`entity/` + `repository/`**: JPA entities + Criteria-based query methods, unit-tested directly against a test Postgres/H2 instance — including the ownership-scoping and column-scoping and redaction logic. This is the highest-risk file; get it right and fully tested before anything else depends on it.
3. **`auth/`**: hardcoded session map, `AuthContext`, greeting helpers.
4. **`log/`**: both SQLite-backed log services, with their own `DataSource`s, tested standalone (insert, dedup-on-feedback, fetch).
5. **`format/ReplyFormatter`**: pure functions, easiest to unit test exhaustively (missing-field omission, multi-app blocks, pagination note wording) before it's relied on by two different callers.
6. **`directquery/`**: wires `repository` + `format` together — no LLM dependency, so it's fully testable end-to-end at this point via `/chat/direct`.
7. **`tools/`**: schema constants + dispatcher, unit-tested by calling `dispatch()` directly with a fake `AuthContext` for each tool.
8. **`llm/LlmClient`**: isolated OpenRouter wrapper, testable with a mocked HTTP response.
9. **`chat/ChatEngineService`**: the integration point — system prompt, tool loop, small-talk short-circuit, fast-path formatting, history trimming/persistence. Build this last since it depends on everything above.
10. **`controller/`**: thin HTTP layer wiring it all together, plus `GlobalExceptionHandler` for the "never leak internals" 500 behavior.
11. **`dev/SeedDataRunner`**: local-profile-only seed data for manual testing, mirroring `seed_data.py`.
12. **Cross-check against Angular**: once the frontend zip is re-uploaded, diff its actual HTTP calls against §8 to catch any casing/shape mismatch before wiring the real base URL over.

---

## 10. Things intentionally *not* changing in this migration

- No new endpoints, no removed endpoints, no renamed fields — this is a language/runtime port, not a redesign.
- No change to the authorization model (owner-only vs admin).
- No change to which fields are ever exposed (PAN and friends stay permanently unreachable).
- No change to the RAG-backend handoff protocol.
- No change to the SQLite log files' schemas (so existing `.db` files can be reused as-is if you want to carry historical logs forward — SQLite files are engine-agnostic, a Java `sqlite-jdbc` connection reads the exact same files Python's `sqlite3` wrote).

Anything beyond a straight port (Redis for sessions, moving logs into Postgres, restricting CORS, real SSO) is flagged above as a deliberate "do this later, not during the port" item so migration risk stays isolated to "same behavior, new language."
