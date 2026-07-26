# Kickoff Sim

A Spring Boot web application for managing mini-football leagues — create teams, build squads, generate round-robin schedules, record goals, follow live standings, receive push notifications, and read AI-generated round and season recaps.

Built as an individual project for the Spring Advanced course at SoftUni. The system consists of two applications:

- **kickoff-sim** (this repository) — the main MVC web application
- **kickoff-notifications** — a REST microservice for subscriptions and notifications, consumed over a Feign client

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 4.1.0 |
| Web / Templating | Spring MVC, Thymeleaf |
| Persistence | Spring Data JPA (Hibernate), MySQL 8 |
| Security | Spring Security — session-based auth, BCrypt, CSRF |
| Inter-service | OpenFeign (notifications microservice), RestClient (weather API) |
| AI | Spring AI 2.0 — Ollama chat model (local, self-hosted) |
| Cross-cutting | Spring AOP (service execution logging), Spring Cache |
| Real-time | Server-Sent Events (SSE) |
| i18n | English, Bulgarian, German (cookie-based locale) |
| Build | Maven |
| Utilities | Lombok, Apache POI, commons-codec |
| Infrastructure | Docker (multi-stage build), Docker Compose |

---

## Quick Start

See [SETUP.md](SETUP.md) for the full setup guide, including IntelliJ configuration and the Ollama installation needed for the AI recaps.

Short version:

```bash
docker-compose up -d --build
```

Then open `http://localhost:8080`. The first registered account becomes ADMIN.

---

### Timezone

The **08:00–23:30** kick-off window for league scheduling is always validated against **Europe/Sofia (Bulgarian) time** — this is fixed regardless of where the server runs.

If the person creating the league is in a different timezone, the form also shows the equivalent kick-off time in **their local timezone**. The schedule itself is still generated and stored in Sofia time. Day-grouping in the UI follows the viewer's timezone, read from a `tz` cookie.

---

### Language

The interface is available in **English**, **Bulgarian**, and **German**. The language is chosen from the topbar switcher, stored in a `lang` cookie for one year, and can also be forced with `?lang=bg` on any URL. All flash messages, validation errors, and AI recaps follow the active locale.

---

## First-time Walkthrough

Follow these steps on a fresh database to see all features in action.

### Step 1 — Register your admin account

Go to `/register`. The **first account created on an empty database is automatically granted ADMIN** — no setup needed. All subsequent registrations receive the USER role.

### Step 2 — Create a league with the wizard

Go to **Leagues → New League**. The wizard walks through three steps:

1. **Format** — pick 6, 8, 10, or 16 teams
2. **Teams** — select any existing free teams with at least 6 players (skipped automatically if none exist)
3. **New teams** — fill in exactly as many new teams as are still missing, each with a squad; randomiser buttons fill names, cities, and players instantly

Set the Round 1 date and a kick-off time **5 to 40 minutes ago** to see a live match immediately. Everything is created and the full round-robin schedule generated in one submit.

Teams can also still be created one at a time via **Teams → Create Team**, and a league assembled from them via the classic **Leagues → Create League** form.

### Step 3 — Watch the standings

Open the league detail page. The **Standings** tab shows a green row for the team currently winning, orange for a draw, red for losing. Each live team shows a pulsing **LIVE** badge with the current score, refreshed every 30 seconds without reloading.

A match is live for exactly **46 minutes** from kick-off. After **50 minutes**, the result is auto-simulated by a background job.

### Step 4 — Generate an AI round recap

Once every match in a round is finished, the league detail page shows a **Generate recap** button (ADMIN only). It sends the verified results, goal events, and standings to a locally running Ollama model and stores a 3–4 paragraph journalistic summary, written in the currently selected language. A **season overview** can be generated the same way once at least one match is complete.

Recaps are cached per league, round, and language. Regenerating overwrites the stored text.

### Step 5 — Follow teams and matches

Click **Follow** on a team, league, or match. Subscriptions are stored in the notifications microservice. Kick-off, goal, half-time, and full-time events are pushed to the browser over SSE and shown as toasts. **My Feed** collects live, upcoming, and recent matches for everything you follow.

### Step 6 — Record a goal manually (admin)

Open any match. As admin, you see **Add Goal** on the match detail page. Fill in the minute (1–40, where 1–20 = first half and 21–40 = second half), the scorer, and optionally an assist.

### Step 7 — Test the approval workflow

Register a **second account** in an incognito window (this one gets USER role). Try editing a team or adding a player — the action is queued as a **pending proposal**. Switch back to ADMIN, go to **Admin → Change Requests**, and approve or reject it.

---

## Features

### AI Round and Season Recaps

- Generated by a **local Ollama model** through Spring AI — no external API key, no data leaves the machine
- Default model `gemma3:4b`, configurable via `KICKOFFSIM_OLLAMA_MODEL` / `OLLAMA_MODEL`
- Two scopes: a **round recap** (requires every match in the round finished) and a **season overview** (requires at least one finished match)
- Written in the viewer's language — Bulgarian, English, or German — as original prose, not a translation
- The prompt supplies only verified facts: final scores, the goal timeline with scorer, assist, minute, own-goal and penalty flags, and the full standings table
- Strict anti-hallucination instructions: no invented statistics, quotes, injuries, tactics, records, attendance, or form; the current leader and points total must come from position 1 of the standings
- Stored per `(league, round, locale)` with a SHA-256 fingerprint of the source data and a generation timestamp
- Failures degrade gracefully to a flash message; the page never breaks when Ollama is unreachable
- ADMIN-only generation; all users can read a stored recap

### Leagues

- Create leagues in four sizes: **6, 8, 10, or 16 teams**, either through the guided **wizard** (format → existing teams → new teams, all in one transaction) or the classic single-form flow
- Each selected team must have at least **6 players** and must not already belong to another league
- Set the Round 1 date and kick-off time (15-minute steps, 08:00–23:30 Bulgarian time); the full round-robin schedule generates automatically
- Schedule formats per size: 6 teams = 3-cycle (15 rounds total), 8 teams = 2-cycle (14 rounds), 10 teams = 2-cycle (18 rounds), 16 teams = 1-cycle (15 rounds)
- Standings table, round-by-round schedule, match results, and AI recaps on the league detail page
- Round-by-round navigation with a dropdown to jump to any round, plus a season progress bar
- Each league gets a unique auto-generated SVG logo, themed by its name (see [docs/league-naming-standard.md](docs/league-naming-standard.md))
- Export standings to **XLSX** or **PDF**
- ADMIN can (re)generate a schedule at any time
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
- The same live view appears on the league page, each team's detail page, and My Feed
- After **50 minutes**, the background job simulates the final result

### Notifications and My Feed

- Follow a **team**, a **league**, or an individual **match**; subscriptions live in the notifications microservice and are reached through a Feign client
- Following a team or league automatically backfills subscriptions for all its matches
- Events (kick-off, goal, half-time, second half, full time) are pushed to the browser over **Server-Sent Events** and rendered as toasts — no polling
- Only events created after the current login are toasted, so logging in does not replay a backlog
- **My Feed** groups everything followed into Live / Upcoming / Recent (last 14 days), with standing position and remaining fixtures per followed team
- If the microservice is unreachable, the feed degrades to an informational banner instead of an error page

### Match Weather

- Upcoming matches show a forecast for the home team's city, fetched from the Open-Meteo API via `RestClient`
- Geocoding and forecast responses are cached; forecasts are only available up to 15 days ahead
- A background job prefetches forecasts for upcoming matches so the page never waits on the API

### Schedule Generation and Auto-simulation

- Round-robin rotation algorithm (positions array, last position fixed, rest rotate each round)
- Multi-cycle leagues swap home/away between cycles; odd-numbered rounds also swap home/away
- Matches within a round are played on the same day, each starting **1 hour after the previous**; each round is played on a separate calendar day
- Result simulation after 50 minutes: realistic goal totals weighted toward 3–6 goals per match; home-team advantage (55% of goals go to the home side); 8% own-goal probability; 12% penalty probability; 60% chance of an assist; scorers and assists drawn from the actual squad

### Teams and Players

- Create a team with name, city, and a squad of up to **12 players** in one form
- Randomiser buttons fill in realistic Bulgarian team names, city names, league names, and player names — full squad (12), minimum squad (6), or a single row
- Each team and league gets a unique auto-generated SVG logo, cached by the browser for 24 hours
- Add more players to an existing team at any time via the squad page; edit or delete individual players
- Teams list is sortable by name, city, players, position, or league

### Search

- Type-ahead search in the topbar over **teams, players, and leagues** (minimum 3 characters)
- Results are grouped by type and link straight to the relevant detail page

### Goals and Match Detail

- Record goals with: scorer (dropdown filtered to match participants), optional assist (same team as scorer), minute 1–40, own-goal flag, penalty flag
- The assist dropdown is hidden automatically when own-goal is checked
- Minutes 1–20 are stored as first-half goals; 21–40 as second-half goals (minute - 20)
- A goal's minute must be unique within its half for the same match
- Running score after each goal is stored and shown in the timeline
- Expandable match card on list pages shows the full timeline split by half

### Matches List and Filters

- Filter matches by league, team, or date
- Date calendar highlights days that have matches; clicking a day applies a date filter
- Without a date filter: Live / Upcoming / Results tabs
- When filtering by team: a mini-standings table for that team's league is shown alongside the matches

### Change Approval Workflow

- **ADMIN** actions apply immediately to the database
- **USER** actions are saved as a pending **ChangeRequest** for an admin to review
- Admins see a badge in the navigation with the count of pending requests
- On approval: the saved payload is re-validated; stale or now-invalid proposals fail gracefully and stay pending rather than silently corrupting data
- On rejection: admin writes a reason (free text or from a context-sensitive suggestion list)
- Users see all their proposals in **My Proposals**: filterable by status and entity type, paginated
- PENDING proposals can be edited (Resubmit opens the form pre-filled) or cancelled
- REJECTED proposals (non-delete) can be resubmitted with one click
- Whole-league bundles created in the wizard are submitted as a single `LEAGUE_BUNDLE` request
- PENDING requests older than **14 days** are automatically expired; APPROVED and REJECTED requests older than **30 days** are hard-deleted

### Account and User Management

- Users edit their own **profile** (email), **change their password** (current password required), and **deactivate their own account**
- **Brute-force protection**: 5 consecutive failed logins lock the account for 15 minutes; a successful login resets the counter
- ADMIN: paginated list of all users (20 per page), promote/demote roles, deactivate and reactivate any account
- The last remaining admin cannot be demoted or deactivated — enforced in the UI and on the server
- An admin who demotes themselves has their session invalidated immediately

---

## Automatic Background Behaviours

| Job | Schedule | What it does |
|---|---|---|
| Match auto-simulation | Every 30 seconds | Simulates results for matches kicked off more than 50 minutes ago that still have a 0-0 score |
| Weather prefetch | Periodic | Warms the forecast cache for upcoming matches |
| Stale request expiry | Every 6 hours (first run after 5 min) | Auto-rejects PENDING requests older than 14 days |
| Resolved request purge | Daily at 03:00 | Hard-deletes APPROVED and REJECTED requests older than 30 days |
| Finished league purge | Daily at 03:30 | Deletes leagues finished more than 90 days ago |
| Schedule auto-generation | On team save event | If a team is added to a league with a stored start date and a valid team count, the schedule is generated automatically |

---

## Security and Roles

| Role | Access |
|---|---|
| **Guest** | Home page (`/`), register (`/register`), login (`/login`), team and league logos (`/teams/*/logo`, `/leagues/*/logo`) |
| **USER** | All browsing pages; own profile, password, and account; follow/unfollow; submit create / edit / delete proposals via the change request workflow |
| **ADMIN** | Everything; changes apply immediately; access to `/admin/**`; manage change requests; generate schedules and AI recaps; add goals directly; manage users |

- The **first registered user** on an empty database is automatically promoted to ADMIN
- All passwords hashed with BCrypt
- CSRF protection is enabled; an eager CSRF token filter guarantees a token is available to the JavaScript-driven endpoints
- Access-denied responses return HTTP 404 to hide the existence of admin-only routes from non-admins
- Role-based rules are enforced both in the Spring Security filter chain and via `@PreAuthorize` on individual controller methods
- Account lockout after 5 failed login attempts (15 minutes)

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

Every form is validated server-side with `@Valid` and Bean Validation annotations. Invalid submissions redisplay the form with field-level error messages next to the specific field, localised into the active language.

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
- A round recap requires every match in that round to be finished
- The last admin cannot be demoted or deactivated

Custom exceptions (`EntityNotFoundException`, `InvalidMatchException`, `InvalidLeagueOperationException`, `DuplicateShirtNumberException`, `SquadLimitExceededException`, `RoundRecapGenerationException`, and others) are caught centrally by `GlobalExceptionHandler` (`@ControllerAdvice`) and rendered as a user-friendly error page instead of a raw stack trace.

---

## Domain Model

| Entity | Key fields | Notes |
|---|---|---|
| **League** | name, scheduleStartDate, scheduleStartTime | Has many Teams, Matches, and RoundRecaps |
| **Team** | name, city, league (nullable) | Has many Players; can exist without a league |
| **Player** | firstName, lastName, shirtNumber | Belongs to one Team |
| **Match** | homeTeam, awayTeam, homeScore, awayScore, playedAt, roundNumber | Belongs to one League implicitly via teams |
| **Goal** | scorer, assistant, minute, half, ownGoal, penalty | Belongs to one Match |
| **RoundRecap** | roundNumber, localeTag, content, generatedAt, sourceFingerprint | AI-generated text, unique per league + round + locale |

Technical entities (not counted as domain): **User** (authentication, lockout state) and **ChangeRequest** (approval workflow).

All entities use `UUID` as primary key. All passwords are stored hashed (BCrypt).

---

## Project Structure

```
src/main/java/com/kickoffsim/
├── aop/              Service execution logging aspect
├── client/           Feign client + DTOs for the notifications microservice
├── config/           Jackson, Feign, RestClient, Clock, WebMvc (i18n) configuration
├── controller/       HTTP layer — one controller per aggregate
├── service/          Business logic interfaces (incl. RoundRecapService, RoundRecapAiClient)
├── service/impl/     Implementations (incl. OllamaRoundRecapClient, WeatherServiceImpl)
├── repository/       Spring Data JPA repositories
├── model/            JPA entities and enums
├── dto/              Data transfer objects, view models, and AI prompt data records
├── exception/        Custom exceptions + GlobalExceptionHandler
├── security/         SecurityConfig, CustomUserDetailsService, login attempt listener, CSRF filter
├── scheduling/       Match simulation, weather prefetch, request cleanup, league retention
└── web/              Support utilities (sorting, logos, standings, SSE registry, exports)

src/main/resources/
├── templates/        Thymeleaf templates (leagues, teams, matches, players, admin, feed, fragments)
├── static/           CSS (style.css), JS (random-names.js, goal-assist.js, livescore.js)
└── messages*.properties   EN / BG / DE translations
```

---

## Documentation

- [SETUP.md](SETUP.md) — running the project locally or in Docker, including Ollama
- [docs/league-naming-standard.md](docs/league-naming-standard.md) — the vocabulary and rules behind generated league names and their logo motifs
