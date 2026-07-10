# Setup Guide

Two ways to run the project: **fully in Docker** (recommended — identical setup for everyone,
no "works on my machine" surprises) or **locally via IntelliJ** (only the database in Docker).

## Option A — Run everything in Docker (recommended)

This builds the app from source inside a container and starts it together with MySQL, so
everyone runs the exact same build with no stale `target/` folders or IDE caches involved.

1. Make sure Docker Desktop is running.
2. From the project root:
   ```
   docker-compose up -d --build
   ```
   `--build` forces a fresh image build from the current source — always use it after pulling
   new changes, otherwise Docker may reuse an old cached image and you'll see outdated behavior
   (this was the cause of the "looks different in Docker" bug).
3. Open `http://localhost:8080`, register a user (the first one becomes `ADMIN`), and log in.
4. To stop: `docker-compose down` (add `-v` only if you also want to wipe the database volume).

## Option B — Run locally via IntelliJ (DB in Docker, app on your machine)

### 1. Project Structure (do this once, before running)

Different "Project Structure" between teammates is almost always one of these three settings —
check all three:

- **File → Project Structure → Project**
  - SDK: **17** (or any 17.x JDK)
  - Language level: **17**
- **File → Project Structure → Modules → football-league-manager**
  - Language level: inherit from project (17)
- **Settings → Build, Execution, Deployment → Build Tools → Maven → Runner**
  - JRE: matches the project SDK (17)
- **Settings → Build, Execution, Deployment → Compiler → Annotation Processors**
  - **Enable annotation processing** must be checked — required for Lombok
    (`@Getter`/`@Setter`/`@RequiredArgsConstructor`). If unchecked, the project won't compile in
    the IDE even though Maven builds fine on the command line.

If IntelliJ still shows red/unresolved symbols after this, do **Maven panel → Reload All Maven
Projects** (the circular-arrows icon).

### 2. Start the database

```
docker compose up -d mysql
```

This starts only the MySQL container, mapped to **`localhost:3307`** on the host (database
`football_league_manager`, root password `12345`). The port is 3307 (not 3306) to avoid
conflicts with a locally installed MySQL instance. Data persists in a named Docker volume across
restarts.

Make sure `src/main/resources/application.properties` has the matching URL:

```
spring.datasource.url=jdbc:mysql://localhost:3307/football_league_manager
```

If you have a local MySQL running on 3306 and prefer to use that instead, point the URL at 3306
and skip the Docker step above.

### 3. Run the app

Either click ▶ on `FootballLeagueManagerApplication` in IntelliJ, or:

```
./mvnw spring-boot:run
```

Open `http://localhost:8080`, register a user (the first one becomes `ADMIN`), and log in.

### If you see unexpected/outdated behavior

- Run **Maven panel → Reload All Maven Projects**, then **Build → Rebuild Project**.
- If running the packaged jar instead of via IDE/`spring-boot:run`, run `./mvnw clean package`
  first — an old jar in `target/` does not get new templates/CSS automatically.
- If you get redirected to the login page with "Your session has expired", that's expected after
  the MySQL volume was wiped/recreated (`docker-compose down -v`) while your browser still had a
  login cookie for a user that no longer exists — just log in again.

## Troubleshooting: port already in use

If `localhost:8080` or `localhost:3307` is already taken by a previous run, stop it first:

```
docker compose down
```

and/or stop any leftover local `spring-boot:run` process before starting again.
