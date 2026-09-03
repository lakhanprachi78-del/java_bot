# application_status_backend (Java / Spring Boot port)

Java/Spring Boot port of the Python LOS Application Status chatbot backend.
Same logic, same rules, same LLM prompt, same auth/redaction — different language.

## Requirements
- Java 17+
- Maven 3.9+
- Postgres reachable via your `DATABASE_URL`

## Environment variables (same names as your old .env)

```
DATABASE_URL=postgresql://username:password@host:5432/databasename
LLM_API_KEY=your-gemini-api-key
LLM_MODEL=gemini-2.0-flash          # must be tool-calling-capable
```

Optional (all have sane defaults — see application.properties):
```
MAX_ROWS_RETURNED=25
RAG_CHAT_URL=http://127.0.0.1:8000/api/chat
SUPPORT_EMAIL=skaleupprodsupport@ugrocapital.com
LOS_QUERY_LOG_DB_PATH=los_query_log.db
LOS_FEEDBACK_LOG_DB_PATH=los_feedback_log.db
```

Export these before running (PowerShell example):
```powershell
$env:DATABASE_URL="postgresql://user:pass@host:5432/db"
$env:LLM_API_KEY="your-key"
$env:LLM_MODEL="gemini-2.0-flash"
mvn spring-boot:run
```

## Run

```bash
mvn clean compile      # I could not run this myself in my sandbox (no Maven
                        # Central access) - please run this first to catch
                        # anything I missed.
mvn spring-boot:run
```

Server starts on **port 5000** — this matches your Angular frontend's
`environment.ts` (`losWsUrl: 'ws://127.0.0.1:5000/ws'`) exactly, so no
frontend changes are needed.

## ⚠️ Important things to know before running this

1. **Transport: WebSocket, not REST.** Your Python backend was a plain
   REST API (FastAPI). Your Angular frontend, however, only ever speaks
   WebSocket — it sends `{"type": "...", "payload": {...}}` frames to
   `ws://127.0.0.1:5000/ws`. There was no WebSocket layer anywhere in the
   Python code you gave me. I built one (`ChatWebSocketHandler` +
   `WebSocketConfig`) that implements the exact protocol your frontend's
   `chat.service.ts` already expects (message types: `chat`, `chat.direct`,
   `greet`, `chat.reset`, `feedback`, `feedback.detail`, `health`), wired to
   the same ported business logic. I also kept plain REST endpoints
   (`ChatController`, `FeedbackController`) matching the original Python
   routes 1:1, for testing/parity — your frontend doesn't use these.

2. **Status code mismatch with the frontend's status menu.** `STATUS_CODES`
   (in `ToolSchemas.java`, ported verbatim from `tools.py`) is:
   `pre-login, pre-login review, pre login discrepant, sales discrepant,
   credit assessment, pre-disbursement, approved, rejected, sent to lms`.
   The Angular frontend's "Find by Status" menu we built together has
   top-level categories `Sales / Credit / Commercial / Operations`, with
   `Credit`, `Commercial`, and `Operations` sent as-is as status values —
   none of which exist in this list. Clicking those three will currently
   fail validation. We should reconcile this (either the frontend's status
   values need to match this list, or this list needs updating to match
   the real database status codes) before this goes live.

3. **`LLM_MODEL` default.** I preserved the Python default
   (`openai/gpt-4o-mini`) even though the LLM endpoint is hardcoded to
   Google's Gemini OpenAI-compatible endpoint — that mismatch already
   existed in the given Python code (leftover from an earlier OpenRouter
   setup, most likely). Since you'll set `LLM_MODEL` via env anyway, this
   only matters if it's ever left unset.

4. **I could not compile-test this project.** My sandbox has no access to
   Maven Central, so unlike the Angular frontend (which I actually built
   and ran), I wrote and reviewed this by hand without a compiler. Please
   run `mvn clean compile` first and send me any errors — I'll fix them
   immediately.

## Project layout

```
pom.xml
src/main/resources/application.properties
src/main/java/com/ugrocapital/appstatus/
  AppStatusBackendApplication.java     # main()
  config/            # DataSourceConfig (parses DATABASE_URL), CorsConfig,
                      # WebSocketConfig, AppProperties (= config.py)
  auth/              # AuthContext, AuthService, UnknownSessionException (= auth.py)
  model/             # JPA entities: LoanApplication, ApplicationApplicant,
                      # ApplicationApplicantEmail (= models.py)
  repository/        # LoanApplicationRepository, SensitiveDataRedactor (= repository.py)
  service/           # ChatEngine (= chat_engine.py), ToolSchemas + ToolDispatcher
                      # (= tools.py), DirectQueryService (= direct_queries.py),
                      # FormattingService (= formatting.py), LlmClient (= llm_client.py),
                      # TokenTracker (= token_tracker.py), QueryLogService (= query_log.py),
                      # FeedbackLogService (= feedback_log.PY), HandoffService,
                      # ChatOrchestrationService (shared /chat + /chat/direct logic),
                      # ConversationStore (= CONVERSATIONS dict in main.py)
  web/               # ChatWebSocketHandler (the real frontend's transport),
                      # ChatController + FeedbackController (REST parity, = main.py)
```

Every Java file's top comment says which Python file it ports and calls
out anything that had to be adapted (mainly: JPA idioms replacing raw
SQLAlchemy calls, and the WebSocket bridge described above).
