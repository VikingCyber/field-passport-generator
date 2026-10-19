# AGENTS.md

Single-module Maven project (Java 21): generates PDF "pasporty poley" (field passports) for the AgroSignal farm-management API. UI strings, log messages, comments, and tests are in Russian — keep that convention.

## Commands

- Build / test: `./mvnw test`, package: `./mvnw package` (Maven 3.9.11 wrapper, JDK 21 — `maven.compiler.release=21`).
- Run the app: `./mvnw exec:java` (exec plugin is configured with the real main class `com.viking.generator.Main`).
- Single test: `./mvnw test -Dtest=SatelliteImageLoaderTest` (surefire runs JUnit 5; existing tests: `FieldDataAggregatorIntegrationTest`, `SatelliteImageLoaderTest`, `AgroMetadataSyncServiceTest`, `NoteImageComparatorsTest`).
- Mockito needs the JVM agent flag `-XX:+EnableDynamicAgentLoading`; it is already set in the surefire config — do not remove it or mocked tests break.
- ⚠️ The `maven-shade-plugin` manifest still points at `com.viking.Main`, which no longer exists (package was renamed to `com.viking.generator.*`). `java -jar target/*-shaded.jar` will fail; always run via `./mvnw exec:java`.

## Running requires untracked config/data (gitignored)

- `application.yml` is gitignored and holds the live AgroSignal API key + `companyId: 390557L`. `AppConfig` first looks for an external `./application.yml` in the working dir, then on the classpath; a fresh checkout has neither, so the app throws "Config not found". Keep a local copy when running.
- All runtime data is gitignored and created at runtime: `data/` (JSON metadata pulled by sync), `output/` (generated PDFs), `cache/images/` (notes/satellite/charts), `notes/`, `charts/`, `logs/`. Without prior sync, passport generation has no source data.

## Architecture

- Entry: `com.viking.generator.Main` builds `AppConfig` (parses YAML via Jackson into typed records under `config/record`, key names in `ConfigKeys`) and wires everything manually in `AppContainer` (no DI framework).
- Two modes via `app.mode` in config: `web` (default, port 8080) → `AppServer` (Javalin; static SPA from classpath `/public`, API routes under `/api` in `web/controller/FieldController`); otherwise an interactive console menu in Russian.
- Data pipeline: `AgroMetadataSyncService` downloads AgroSignal metadata to `data/*.json` → `FileDataProvider` + `FieldDataAggregator` (JSON → `FieldPassport` model) → `InMemoryDataProvider` (runtime cache) → `PassportOrchestrator` → `SyncService`/`ImageCacheService` with per-source strategies (`NoteStrategy`, `SatelliteStrategy`, `ChartStrategy`) → `PdfGeneratorService` (OpenPDF renders into `output/`).
- Concurrency is by virtual threads with a `Semaphore(memoryGuard)`. `generationTracker` dedups in-flight generations by `PassportKey(fieldId, year)` — keep idempotency/thread-safety assumptions when editing the orchestrator.
- HTTP API (from `FieldController`): `GET /api/passports`, `POST /api/passports/sync`, `POST /api/passports/generate?force=`, `GET /api/passports/{id}/{year}/status`, `GET /api/passports/{id}/{year}/pdf`, `POST /api/passports/{id}/{year}/generate?force=` (note: force defaults to `true` on the per-field endpoint, `false` on mass generate).

## Style

- Code style is Google Java Format / Checkstyle; configs live in `.idea/` (`intellij-java-google-style.xml`, `checkstyle-google.xml`). No enforcing plugin is wired into Maven — follow the convention manually.
- Tests are offline (Mockito-mocked `InternalHttpClient`, no live API calls), use `@TempDir` for files. `MockWebServer3` (okhttp) is on the classpath but not yet used.