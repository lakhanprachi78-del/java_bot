# LOS Chatbot Backend — Step-by-Step Java Build Plan

Companion to `JAVA_MIGRATION_ARCHITECTURE.md`. That doc explains the *what and why*;
this doc breaks it into **discrete, buildable steps in order** — each one is small
enough to build, compile, and test on its own before moving to the next. Work through
them top to bottom; each step lists exactly what to ask me to generate when you get there.

Legend: 🔴 high-risk/security-critical · 🟡 medium · 🟢 low-risk/mechanical

---

## Phase 0 — Project skeleton

### Step 1 — Bootstrap the Spring Boot project 🟢
**Goal:** an empty app that boots and responds to `/health`.
**Build:**
- `pom.xml` with: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `postgresql`, `org.xerial:sqlite-jdbc`, `lombok` (optional, your call), Spring Boot's built-in Jackson.
- `LosBotApplication.java` (main class).
- `application.yml` with placeholder Postgres URL + profile scaffolding (`local`, `dev`, `prod`).
- `controller/HealthController.java` → `GET /health` returning `{"status":"ok"}` (matches Python exactly).
**Acceptance check:** `mvn spring-boot:run`, hit `/health`, get `200 {"status":"ok"}`.
**Ask me:** *"Generate step 1: the Spring Boot skeleton with pom.xml, application.yml, and the health endpoint."*

### Step 2 — Central configuration 🟢
**Goal:** typed config replacing `config.py`.
**Build:**
- `config/AppProperties.java` (`@ConfigurationProperties(prefix="los")`) covering: DB URL, LLM API key/URL/model, `maxRowsReturned`, RAG handoff URL, support email — matching every value in `config.py` §4.1 of the architecture doc.
- Fill in `application.yml` keys for all of the above, env-var-overridable exactly like `.env` today.
- `config/CorsConfig.java` — wide-open CORS for now, same as Python (`allow_origins=["*"]`), with a comment flagging it for later tightening.
**Acceptance check:** properties bind and print correctly on startup (a `@PostConstruct` log line is fine for now, remove later).
**Ask me:** *"Generate step 2: AppProperties, application.yml config keys, and CorsConfig."*

---

## Phase 1 — Data layer (build this before anything else touches the DB)

### Step 3 — JPA entities 🟡
**Goal:** `LoanApplication`, `ApplicationApplicant`, `ApplicationApplicantEmail` matching `models.py` exactly (schema `dmcredit`, all columns, FK relationships, eager fetch on `applicants`/`emails`).
**Build:** the three `entity/` classes per §4.2 of the architecture doc.
**Acceptance check:** app boots against a real/test Postgres instance with `ddl-auto: validate` and does **not** error — proves the entity mapping matches the actual table shape. (If you don't have DB access yet, use `ddl-auto: none` and defer this check to Step 4.)
**Ask me:** *"Generate step 3: the three JPA entities."*

### Step 4 — Repository / query layer 🔴 (highest-risk step — take your time here)
**Goal:** full port of `repository.py`'s eight query functions **plus** the security machinery (`_scope_to_owner`, `_column_scoped`, `_redact_sensitive`, `_serialize`).
**Build, in this order:**
1. `repository/ApplicationSerializer.java` — redaction regexes + truncation + the app→DTO mapping. Unit test this in isolation first (feed it fake entities, assert PAN/password/long-digit-run redaction and 500-char truncation).
2. `repository/ApplicationQueryRepository.java` — Criteria API queries, one method per Python function, **every single one** applying the ownership-scope predicate unless `role == "admin"`. This is the file where a missed `WHERE` clause becomes a real data leak — review it against §4.3 line by line before moving on.
3. `SearchResult` record + `ApplicationDto` record (or reuse for both).
**Acceptance check:** write a focused test suite (H2 or a real Postgres test container) with: an admin session seeing everything, a normal-user session seeing only their own records, a query for someone else's application ID returning empty (not an error), and a redaction test on a remark containing a fake PAN.
**Ask me:** *"Generate step 4: ApplicationSerializer, then the ApplicationQueryRepository with all eight query methods."* (Ask for these as two separate turns if you want to review the serializer/redaction logic before moving to the bigger query file.)

### Step 5 — Query-log and feedback-log services 🟡
**Goal:** the two SQLite-backed logs, fully independent of the Postgres data layer.
**Build:**
- `config/QueryLogDataSourceConfig.java` + `config/FeedbackLogDataSourceConfig.java` — two extra `DataSource`/`JdbcTemplate` beans, qualified (`@Qualifier("queryLogJdbcTemplate")` etc.) per §7.
- `log/QueryLogService.java` — `logQuery()`, `addFeedback()` (with the exact-text dedup-at-feedback-time logic and `_normalize()` equivalent), `fetchAll()`. Table DDL from §7, created on startup.
- `log/FeedbackLogService.java` — `addFeedbackDetail()`, `fetchAll()` (tags as JSON array via Jackson), same DDL section.
**Acceptance check:** insert two "queries" with identical normalized text, give feedback to one, confirm feedback on the second lands on the same canonical row (mirrors Python's dedup test case). Confirm both services swallow DB errors without throwing (never break the chat response).
**Ask me:** *"Generate step 5: the two DataSource configs, QueryLogService, and FeedbackLogService."*

---

## Phase 2 — Pure logic layers (no LLM dependency — fully testable now)

### Step 6 — Reply formatter 🟢
**Goal:** `format/ReplyFormatter.java`, a pure-function port of `formatting.py`.
**Build:** `formatApplicationBlock`, `formatApplicationBlocks`, `formatSearchReply`, `formatSingleLookupReply` — exact field order, omit-if-missing, status upper-casing, count-line wording, pagination-note wording.
**Acceptance check:** unit tests covering: single app all fields present, single app with missing optional fields (confirm lines are omitted not blank), multi-app blank-line separation, zero-results message, `has_more=true` with and without a narrow hint.
**Ask me:** *"Generate step 6: ReplyFormatter with unit tests."*

### Step 7 — Auth service 🟢
**Goal:** `auth/AuthContext`, `auth/AuthService`, `auth/UnknownSessionException`, `auth/GreetingService`.
**Build:** hardcoded session map (all 11 sessions from `auth.py`), display-name lookup, time-based greeting (morning/afternoon/evening), `build_greeting()`.
**Acceptance check:** unit test every session ID resolves to the right username/role; unknown session throws; greeting text matches the `"Hey {name}, {time-based} greeting! 👋"` shape.
**Ask me:** *"Generate step 7: AuthContext, AuthService, and GreetingService."*

### Step 8 — Direct-query service 🟡
**Goal:** `directquery/DirectQueryService.java` + `RelativeDatePhraseResolver.java` — the non-LLM query path, wiring together Step 4 (repository) and Step 6 (formatter).
**Build:** the five field handlers (`application_id`, `status`, `applicant_name`, `applicant_email`, `date_time`), application-ID regex validation, the full relative-date-phrase table, `DirectQueryException`.
**Acceptance check:** integration test hitting each of the 5 fields end-to-end against your test DB from Step 4, confirming reply text matches the formatter's output exactly.
**Ask me:** *"Generate step 8: DirectQueryService and RelativeDatePhraseResolver."*

**🎯 Checkpoint:** at this point you can wire up a minimal `/chat/direct` controller (borrow the DTO/controller shape from Step 12 early if you want a working demo sooner) and have a fully functional, LLM-free slice of the app to test against Postman/the real frontend. This is a good place to pause and validate before tackling the LLM half.

---

## Phase 3 — LLM integration

### Step 9 — Tool schemas + dispatcher 🔴
**Goal:** `tools/ToolSchemas.java`, `tools/ToolDispatcher.java`, `tools/ToolException.java`.
**Build:** `STATUS_CODES` constant (exact order/spelling), the 8 tool schema definitions (as whatever shape your chosen LLM SDK/HTTP call expects — likely a `List<Map<String,Object>>` or typed builder), `dispatch()` routing to Step 4's repository methods, with `AuthContext` passed as a **method parameter only, never read from `arguments`.**
**Acceptance check:** unit test calling `dispatch()` directly for each tool name with a fake `AuthContext`, confirming it reaches the right repository method; a test asserting that no tool schema has a `username`/`auth`-shaped property (guards against the privilege-escalation regression called out in §4.4).
**Ask me:** *"Generate step 9: ToolSchemas and ToolDispatcher."*

### Step 10 — LLM client 🟡
**Goal:** `llm/LlmClient.java`, `llm/ChatMessage.java`, `llm/LlmException.java`.
**Build:** `RestClient`-based wrapper around the OpenRouter endpoint, same payload shape (`model`, `messages`, `temperature: 0`, optional `tools`/`tool_choice`), same guards (missing API key, missing `choices`), returns the raw assistant message + usage map.
**Acceptance check:** unit test with a mocked HTTP response (WireMock or `RestClient` test binder) covering: normal text reply, tool-call reply, missing-API-key exception, malformed-response exception.
**Ask me:** *"Generate step 10: ChatMessage, LlmClient, and LlmException."*

### Step 11 — Chat engine (the big one) 🔴
**Goal:** `chat/ChatEngineService.java` + `chat/OutOfScopeException.java` + `chat/SmallTalkDetector.java` + `chat/SearchFilterDescriber.java` + `chat/TokenAccumulator.java`.
**Build, in sub-steps (ask for these individually — this file is large):**
1. `TokenAccumulator` and `SmallTalkDetector` — small, standalone, do these first.
2. `SearchFilterDescriber` (`describeSearchFilters` + `narrowHint`) — depends only on Step 6/9's constants.
3. The `SYSTEM_PROMPT` text block — copy verbatim from `chat_engine.py`, keep the static/session-context split intact.
4. `run_chat_turn` equivalent — the tool-calling loop itself: history trimming, the single-tool-call fast path (Steps 6+9), the general multi-tool-call loop, `not_in_scope` → `OutOfScopeException`, `MAX_TOOL_ROUNDS` exhaustion fallback, and the `_persist()` collapse-to-two-messages behavior.
**Acceptance check:** this is the one component worth an integration test with a **mocked** `LlmClient` (don't hit the real OpenRouter API in CI) that scripts a few canned tool-call sequences and asserts: single-status-search takes the fast path (no second LLM call), `not_in_scope` throws with the clean topic text, small talk skips sending tool schemas, history longer than `MAX_HISTORY_MESSAGES` gets trimmed at a user-message boundary.
**Ask me:** *"Generate step 11a: TokenAccumulator and SmallTalkDetector."* then *"step 11b: SearchFilterDescriber."* then *"step 11c: the SYSTEM_PROMPT and ChatEngineService's run_chat_turn logic."*

---

## Phase 4 — HTTP layer

### Step 12 — DTOs 🟢
**Goal:** every `record` in `dto/` per §5 of the architecture doc — `ChatRequest`/`ChatResponse`, `DirectChatRequest`, `FeedbackRequest`/`FeedbackResponse`, `FeedbackDetailRequest`/`FeedbackDetailResponse`, `WhoAmIResponse`.
**Build:** records with Bean Validation annotations; decide snake_case vs camelCase JSON naming once the frontend zip is readable (default to matching Python's `snake_case` field names if unsure, since that's the contract today).
**Ask me:** *"Generate step 12: all the request/response DTOs."*

### Step 13 — Controllers + global exception handling 🟡
**Goal:** `ChatController`, `SessionController`, `FeedbackController`, `exception/GlobalExceptionHandler.java`.
**Build:** wire every endpoint from §8 of the architecture doc, including: the `_handoff_to_skaleup` RAG-forwarding logic with the `is_handoff` loop-prevention flag, the "never leak internals" 500 handler, 400 on blank chat message, 401 on unknown session, the feedback-detail "need tags or comment" 400 check.
**Acceptance check:** this is the point where you can run the whole thing end-to-end via Postman/curl against every route in §8 and compare responses to the live Python service for the same inputs — the most valuable regression check in the whole migration.
**Ask me:** *"Generate step 13: ChatController, SessionController, FeedbackController, and GlobalExceptionHandler."*

### Step 14 — Dev seed data 🟢
**Goal:** `dev/SeedDataRunner.java`, gated behind a `local`/`dev` Spring profile (`@Profile`), so it never runs in prod.
**Build:** port `seed_data.py`'s two sample applications + applicants + emails, only inserted if the table is empty.
**Ask me:** *"Generate step 14: SeedDataRunner."*

---

## Phase 5 — Validation

### Step 15 — Side-by-side contract test 🔴
**Goal:** confidence that the Java service is a drop-in replacement.
**Build:** a small script or Postman collection that fires the same request at both the Python service (port 5000) and the new Java service, for every route in §8, across a few session IDs (`session-prachi`, `session-admin`, etc.) and diffs the JSON responses.
**Acceptance check:** byte-for-byte (or field-for-field) match on reply text, status codes, and response shape, for at least: a single-app lookup, a multi-result status search with pagination, an out-of-scope handoff, and a feedback submission.
**Ask me:** *"Generate step 15: a comparison test harness."*

### Step 16 — Point Angular at the Java service 🟢
**Goal:** cut over.
**Build:** update the Angular environment config's API base URL from the Python port to the new Java service's port; no other frontend changes should be needed if §8's contract held. (Do this once `frontend_1.zip` is re-uploaded and I've confirmed the actual field casing/base-URL config location.)

---

## How to use this doc

Work top-to-bottom. For each step, just tell me which step number (and sub-step letter, where noted) you want, and I'll generate the actual Java files, tests, and any Maven dependency additions needed for that step — plus a short note on what to check before moving to the next one. Steps 4, 9, and 11 are flagged 🔴 because they carry the security-critical logic (authorization scoping, tool-argument isolation, and the LLM tool loop) — those are worth reviewing line-by-line against the architecture doc rather than just running the tests and moving on.
