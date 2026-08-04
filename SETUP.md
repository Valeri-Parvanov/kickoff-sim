# Setup Guide

Kickoff Sim is two applications plus two optional external dependencies:

| Component                                                                               | Port  | Required?                           |
|-----------------------------------------------------------------------------------------|-------|-------------------------------------|
| `kickoff-sim` (this repo, main web app)                                                 | 8080  | yes                                 |
| `kickoff-notifications` (REST microservice, sibling repo at `../kickoff-notifications`) | 8081  | yes for follow/feed/toasts          |
| MySQL 8 on the host                                                                     | 3306  | yes                                 |
| Ollama (local AI runtime)                                                               | 11434 | only for the AI round/season recaps |

Both applications use **separate databases** on the same MySQL instance: `kickoff_sim` and
`kickoff_notifications`. Both are created automatically on first connect.

---

## Prerequisites

- **JDK 17** (or any 17.x)
- **MySQL 8** running on the host at port **3306**, root password `12345`
- **Docker Desktop** (for Option A)
- **Ollama** (optional — see the AI section at the bottom)
- The `kickoff-notifications` repository cloned as a **sibling directory**:
  ```
  Desktop/
  ├── kickoff-sim/
  └── kickoff-notifications/
  ```
  `docker-compose.yml` builds the microservice from `../kickoff-notifications`, so Option A
  fails without it.

---

## Option A — Run everything in Docker (recommended)

Builds both applications from source and starts them together. Both connect to your **host
machine's MySQL** via `host.docker.internal`.

1. Make sure Docker Desktop and your local MySQL are running.
2. From the project root:
   ```
   docker-compose up -d --build
   ```
   `--build` forces a fresh image build from the current source — always use it after pulling
   new changes; otherwise, Docker may reuse an old cached image, resulting in outdated behavior.
3. Open `http://localhost:8080`, register a user (the first one becomes `ADMIN`), and log in.
4. To stop: `docker-compose down`.

The root password can be overridden with the `MYSQL_ROOT_PASSWORD` environment variable.

> The database lives on your host MySQL, not in a container, so `docker-compose down` never
> wipes your data.

---

## Option B — Run locally via IntelliJ

### 1. Project Structure (do this once, before running)

Different "Project Structure" between machines is almost always one of these settings —
check all of them:

- **File → Project Structure → Project**
    - SDK: **17** (or any 17.x JDK)
    - Language level: **17**
- **File → Project Structure → Modules → kickoff-sim**
    - Language level: inherit from project (17)
- **Settings → Build, Execution, Deployment → Build Tools → Maven → Runner**
    - JRE: matches the project SDK (17)
- **Settings → Build, Execution, Deployment → Compiler → Annotation Processors**
    - **Enable annotation processing** must be checked — required for Lombok
      (`@Getter`/`@Setter`/`@RequiredArgsConstructor`). If unchecked, the project won't compile in
      the IDE even though Maven builds fine on the command line.

If IntelliJ still shows red/unresolved symbols after this, do **Maven panel → Reload All Maven
Projects** (the circular-arrows icon).

### 2. Start MySQL

The app connects to `jdbc:mysql://localhost:3306/kickoff_sim` with user `root` and password
`12345` (see `src/main/resources/application.properties`). Start your local MySQL 8 service —
the database itself is created automatically on first connect.

### 3. Start the notifications microservice

From `../kickoff-notifications`:

```
./mvnw spring-boot:run
```

It listens on **8081**, which is what `notifications.service.url` in
`application.properties` points to. Without it, the app still runs — following, My Feed, and
toasts degrade to an informational banner instead of failing.

### 4. Run the app

Either click Run on `KickoffSimApplication` in IntelliJ, or:

```bash
./mvnw spring-boot:run
```

On Windows with a custom `JAVA_HOME`:

```powershell
.\run-local.ps1
```

Open `http://localhost:8080`, register a user (the first one becomes `ADMIN`), and log in.

---

## AI round and season recaps (Ollama)

The recap feature calls a **local** Ollama model through Spring AI to rewrite the narrative story
cards into natural prose. Nothing is sent to an external provider and no API key is needed. The app
works fine without Ollama — when it is disabled, unreachable, or too slow, recaps fall back to the
deterministic template writer, so the feature always produces a result and the page never breaks.

### 1. Install and start Ollama

Download from [ollama.com](https://ollama.com) and install. It runs as a background service on
`http://localhost:11434`.

### 2. Pull the model

```bash
ollama pull gemma3:4b
```

### 3. Verify it responds

```bash
ollama run gemma3:4b "Say hello"
```

### Configuration

| Property                            | Environment variable     | Default                  |
|-------------------------------------|--------------------------|--------------------------|
| `kickoffsim.ollama.enabled`         | `OLLAMA_ENABLED`         | `true`                   |
| `kickoffsim.ollama.base-url`        | `OLLAMA_BASE_URL`        | `http://localhost:11434` |
| `kickoffsim.ollama.model`           | `OLLAMA_MODEL`           | `gemma3:4b`              |
| `kickoffsim.ollama.timeout-seconds` | `OLLAMA_TIMEOUT_SECONDS` | `12`                     |

Set `OLLAMA_ENABLED=false` to skip the model entirely and always use the deterministic template
recaps. `OLLAMA_TIMEOUT_SECONDS` caps how long a page will wait for the model before falling back.

A larger model produces better prose at the cost of generation time — pull it and set
`OLLAMA_MODEL` to its tag.

> **Docker note:** `docker-compose.yml` points the `app` container at
> `http://host.docker.internal:11434`, so Ollama running on your host is reached from inside the
> container without extra setup. Override `OLLAMA_BASE_URL` or `OLLAMA_MODEL` in your shell (or a
> `.env` file next to `docker-compose.yml`) to change either.

---

## Running the tests

Tests are run manually:

```bash
mvn test
```

In IntelliJ: right-click `src/test/java` → **Run 'All Tests'**, or open a single test class and
click the green arrow next to the class name. The JaCoCo coverage report is written to
`target/site/jacoco/index.html`.

---

## Troubleshooting

### Port already in use

If `localhost:8080` or `localhost:8081` is taken by a previous run:

```bash
docker compose down
```

On Windows, `free-port-8080.ps1` kills whatever is holding 8080.

### Unexpected or outdated behavior

- Run **Maven panel → Reload All Maven Projects**, then **Build → Rebuild Project**.
- If running the packaged jar instead of via IDE/`spring-boot:run`, run `./mvnw clean package`
  first — an old jar in `target/` does not pick up new templates or CSS.
- In Docker, always rebuild with `docker-compose up -d --build`.

### "Your session has expired" on every request

Expected after the database was recreated while your browser still held a login cookie for a
user that no longer exists. Log in again.

### Follow buttons and My Feed show a banner

The notifications microservice is not reachable on 8081. Start it, or ignore it if you are not
testing that area.

### Recap button reports a failure

Ollama is not running, or the configured model has not been pulled. Check with
`ollama list` and see the AI section above.

### Account locked

Five consecutive failed logins lock an account for 15 minutes. An ADMIN can clear it
immediately from **Admin → Users** by reactivating the account.
