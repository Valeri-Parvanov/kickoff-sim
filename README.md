# Kickoff Sim

A Spring Boot web application for managing mini-football leagues — create teams, build squads, generate round-robin schedules, record goals, and watch live standings update in real time.

Built as an individual project for the Spring Fundamentals course at SoftUni.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 4.1.0 |
| Web / Templating | Spring MVC, Thymeleaf |
| Persistence | Spring Data JPA (Hibernate), MySQL 8 |
| Security | Spring Security — session-based auth, BCrypt |
| Build | Maven |
| Utilities | Lombok |
| Infrastructure | Docker (full app — multi-stage build) |

---

## Quick Start

The application can be run in two modes: **locally** (IntelliJ / Maven) or **via Docker**. Both connect to the same MySQL instance running on the host machine.

### Prerequisites

- MySQL 8 running locally on port **3306**, root password `12345`
  - The `kickoff_sim` database is created automatically on first connect
- **Local mode:** JDK 17, Maven (or use the included `mvnw` wrapper)
- **Docker mode:** Docker Desktop running

---

### Option A — Run locally (IntelliJ / Maven)

```bash
./mvnw spring-boot:run
```

On Windows with a custom `JAVA_HOME`:

```powershell
.\run-local.ps1
```

Open the app at `http://localhost:8080`.

---

### Option B — Run via Docker

Docker builds and runs the full application in a container. It connects to your **host machine's MySQL** automatically.

```bash
docker-compose up -d --build
```

Open the app at `http://localhost:8080`.

To stop:

```bash
docker-compose down
```

> **Note:** On first run Docker downloads the Maven and JRE base images (~500 MB). Subsequent builds are fast because dependency layers are cached.

---

### Timezone

The **08:00–23:30** kick-off window for league scheduling is always validated against **Europe/Sofia (Bulgarian) time** — this is fixed regardless of where the server runs.

If the person creating the league is in a different timezone, the form also shows the equivalent kick-off time in **their local timezone**, so they know exactly when the match will start relative to where they are. The schedule itself is still generated and stored in Sofia time.

---

## First-time Walkthrough

Follow these steps on a fresh database to see all features in action.

### Step 1 — Register your admin account

Go to `/register`. The **first account created on an empty database is automatically granted ADMIN** — no setup needed. All subsequent registrations receive the USER role.

### Step 2 — Create teams

Go to **Teams → Create Team**. Use the **"Min squad (6)"** button to randomise six players instantly. Save, then repeat until you have at least **6 teams**. Eight teams give a better schedule (more rounds).

Each team must have at least 6 players before it can join a league. Teams can exist without a league.

### Step 3 — Create a league and see live matches immediately

Go to **Leagues → Create League**. Select at least 6 teams, give the league a name, then set:

- **Round 1 date**: today
- **Kick-off time**: a time **5 to 40 minutes ago** (example: if it is 16:00, enter 15:25)

The full round-robin schedule generates automatically on save. Matches within the same round are spaced **1 hour apart**, so the first match of Round 1 becomes live immediately. The second match goes live 1 hour later, the third 2 hours later, and so on.

A match is live for exactly **46 minutes** from its kick-off time. After **50 minutes**, the result is auto-simulated by a background job that runs every 30 seconds.

To see a completed result straight away instead of a live match, set the kick-off time to more than 50 minutes in the past (example: 15:05 if it is 16:00).

### Step 4 — Watch the standings

Open the league detail page. The **Standings** tab shows:

- A **green** row for the team that is currently winning
- An **orange** row for teams level in a draw
- A **red** row for the team that is losing

Each live team shows a pulsing **LIVE** badge next to its name with the current score. The score and highlights update every **30 seconds** without reloading the page.

Once the 50-minute mark passes, the background scheduler simulates a realistic result and the match leaves the live state. Scorers and assists are drawn from the real squad rosters.

### Step 5 — Record a goal manually (admin)

Open any match via the **Matches** list or from the league schedule. As admin, you see **Add Goal** on the match detail page. Fill in the minute (1–40, where 1–20 = first half and 21–40 = second half), the scorer, and optionally an assist. Goals update the running score and are shown in the timeline by half.

### Step 6 — Test the approval workflow

Open a second browser or an incognito window and register a **second account** (this one gets USER role). Try editing a team or adding a player — the action is queued as a **pending proposal** instead of applying immediately. Switch back to your ADMIN account, go to **Admin → Change Requests**, and approve or reject it.

---

## Features

### Leagues

- Create leagues in four sizes: **6, 8, 10, or 16 teams**
- Each selected team must have at least **6 players** and must not already belong to another league
- Set the Round 1 date and kick-off time (15-minute steps, 08:00–23:30 Bulgarian time; local-time equivalent shown for users in other timezones); the full round-robin schedule generates automatically
- Schedule formats per size: 6 teams = 3-cycle (15 rounds total), 8 teams = 2-cycle (14 rounds), 10 teams = 2-cycle (18 rounds), 16 teams = 1-cycle (15 rounds)
- View the **standings table**, **round-by-round schedule**, and match results on the league detail page
- Round-by-round navigation with a dropdown to jump to any round
- A progress bar shows how many of the season's matches have been completed
- ADMIN can (re)generate a schedule at any time from the league detail page
- Deleting a league detaches all teams (they keep their players) and removes all matches

### Live Standings and Scores

- A match is considered live during the **first 46 minutes** after kick-off
- Live teams are highlighted in the standings: green (winning), orange (drawing), red (losing)
- A pulsing **LIVE** badge next to each live team shows the current score, updated every 30 seconds without a page reload
- The score displayed during a live match is computed in real time from the stored goal timeline:
  - Minutes 0–20 of real time = first half in progress
  - Minutes 21–25 = half-time break
  - Minutes 26–45 = second half in progress
  - After 45 minutes the match is shown as full time
- The same live view appears on both the league page and each individual team's detail page
- After **50 minutes**, the background job simulates the final result (runs every 30 seconds)

### Schedule Generation and Auto-simulation

- Round-robin rotation algorithm (positions array, last position fixed, rest rotate each round)
- Multi-cycle leagues swap home/away between cycles; odd-numbered rounds also swap home/away
- Matches within a round are played on the same day, each starting **1 hour after the previous**
- Each round is played on a separate calendar day
- Result simulation after 50 minutes: realistic goal totals weighted toward 3–6 goals per match; home-team advantage (55% of goals go to the home side); 8% own-goal probability; 12% penalty probability; 60% chance of an assist (if conditions allow); scorers and assists drawn from the actual squad

### Teams and Players

- Create a team with name, city, and a squad of up to **12 players** all in one form
- Randomiser buttons fill in realistic Bulgarian team names, city names, and player names — full squad (12) or minimum squad (6)
- Each team gets a unique auto-generated SVG logo based on its initials and UUID, cached by the browser for 24 hours
- Add more players to an existing team at any time (up to the 12-player limit) via the squad page
- Edit or delete individual players
- Teams list is sortable by name, city, or league

### Goals and Match Detail

- Record goals with: scorer (dropdown filtered to match participants), optional assist (same team as scorer), minute 1–40, own-goal flag, penalty flag
- The assist dropdown is hidden automatically when own-goal is checked
- Minutes 1–20 are stored as first-half goals; 21–40 are stored as second-half goals (minute - 20)
- A goal's minute must be unique within its half for the same match
- Running score after each goal is stored and shown in the timeline
- Expandable match card on list pages shows the full timeline split by half

### Matches List and Filters

- Filter matches by league, team, or date
- Date calendar highlights days that have matches; clicking a day applies a date filter
- When browsing without a date filter: Live / Upcoming / Results tabs
- When filtering by team: a mini-standings table for that team's league is shown alongside the matches

### Change Approval Workflow

- **ADMIN** actions apply immediately to the database
- **USER** actions are saved as a pending **ChangeRequest** for an admin to review
- Admins see a badge in the navigation with the count of pending requests
- On approval: the saved payload is re-validated; stale or now-invalid proposals fail gracefully and stay pending rather than silently corrupting data
- On rejection: admin writes a reason (free text or from a context-sensitive suggestion list)
- Users see all their proposals in **My Proposals**: filterable by status and entity type, paginated
- PENDING proposals: can be edited (Resubmit button opens the form pre-filled) or cancelled
- REJECTED proposals (non-delete): can be resubmitted with one click; the old pending request is removed automatically
- PENDING requests older than **14 days** are automatically expired (rejected) by a background job
- APPROVED and REJECTED requests older than **30 days** are hard-deleted by a nightly cleanup job

### User Management (Admin)

- Paginated list of all users (20 per page, sorted by username)
- Promote any USER to ADMIN or demote any ADMIN to USER
- The last remaining admin cannot be demoted — the button is disabled and the server enforces the rule
- If an admin demotes themselves, their session is invalidated immediately and they are redirected to the login page

---

## Automatic Background Behaviours

| Job | Schedule | What it does |
|---|---|---|
| Match auto-simulation | Every 30 seconds | Simulates results for matches with kick-off more than 50 minutes ago that still have 0-0 score |
| Stale request expiry | Every 6 hours (first run after 5 min) | Auto-rejects PENDING requests older than 14 days |
| Resolved request purge | Daily at 03:00 | Hard-deletes APPROVED and REJECTED requests older than 30 days |
| Schedule auto-generation | On team save event | If a team is added to a league with a stored start date and a valid team count, the schedule is generated automatically |

---

## Security and Roles

| Role | Access |
|---|---|
| **Guest** | Home page (`/`), register (`/register`), login (`/login`), team logos (`/teams/*/logo`) |
| **USER** | All browsing pages; submit create / edit / delete proposals via the change request workflow |
| **ADMIN** | Everything; changes apply immediately; access to `/admin/**`; manage change requests; generate schedules; add goals directly; manage users |

- The **first registered user** on an empty database is automatically promoted to ADMIN
- All passwords hashed with BCrypt
- Access-denied responses return HTTP 404 to hide the existence of admin-only routes from non-admins
- Role-based rules are enforced both in the Spring Security filter chain and via `@PreAuthorize` on individual controller methods

---

## Standings Tiebreakers

When two or more teams are level on points, the following criteria are applied in order:

1. Goal difference (overall)
2. Goals scored (overall)
3. Head-to-head points among the tied group
4. Head-to-head goal difference
5. Head-to-head goals scored
6. Alphabetical order by team name

Points system: **Win = 3 pts, Draw = 1 pt, Loss = 0 pts**.

---

## Validation and Error Handling

Every form is validated server-side with `@Valid` and Bean Validation annotations. Invalid submissions redisplay the form with field-level error messages shown in red next to the specific field.

Business rules enforced at the service layer:

- A league must have exactly 6, 8, 10, or 16 teams
- Every team in a league must have at least 6 players
- A team cannot join a league that already has a generated schedule
- The last match in a round must not start after 23:30
- Shirt numbers must be unique within a team and between 1 and 99
- Maximum squad size is 12 players per team
- Home and away team in a match must be different
- A goal can only be recorded if the declared score has room for it
- A goal's minute must be unique within its half for the same match
- Own goals cannot have an assist; assists must come from the same team as the scorer
- Duplicate league names and duplicate team name + city combinations are rejected
- The last admin cannot be demoted

Custom exceptions (`EntityNotFoundException`, `InvalidMatchException`, `InvalidLeagueOperationException`, `DuplicateShirtNumberException`, `SquadLimitExceededException`, and others) are caught centrally by `GlobalExceptionHandler` (`@ControllerAdvice`) and rendered as a user-friendly error page instead of a raw stack trace.

---

## Domain Model

| Entity | Key fields | Notes |
|---|---|---|
| **League** | name, scheduleStartDate, scheduleStartTime | Has many Teams and Matches |
| **Team** | name, city, league (nullable) | Has many Players; can exist without a league |
| **Player** | firstName, lastName, shirtNumber | Belongs to one Team |
| **Match** | homeTeam, awayTeam, homeScore, awayScore, playedAt, roundNumber | Belongs to one League implicitly via teams |
| **Goal** | scorer, assistant, minute, half, ownGoal, penalty | Belongs to one Match |

Technical entities (not counted as domain): **User** (authentication) and **ChangeRequest** (approval workflow).

All entities use `UUID` as primary key. All passwords are stored hashed (BCrypt).

---

## Project Structure

```
src/main/java/com/kickoffsim/
├── controller/       HTTP layer — one controller per aggregate
├── service/          Business logic interfaces
├── service/impl/     Implementations
├── repository/       Spring Data JPA repositories
├── model/            JPA entities and enums
├── dto/              Data transfer objects and view models
├── exception/        Custom exceptions + GlobalExceptionHandler
├── security/         SecurityConfig, CustomUserDetailsService
├── scheduling/       Auto-simulate results, stale request cleanup, schedule auto-generation
└── web/              Utilities (sort support, squad row validator, SVG logo generator)

src/main/resources/
├── templates/        Thymeleaf templates (leagues, teams, matches, players, admin, fragments)
└── static/           CSS (style.css), JS (random-names.js, goal-assist.js)
```
