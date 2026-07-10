# Football League Manager

A full-stack Spring Boot web application for managing football leagues — from creating teams and squads to generating round-robin schedules, recording goals, and watching live standings update in real time.

Built as an individual project for the Spring MVC & Thymeleaf courses at SoftUni.

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
| Infrastructure | Docker (MySQL container) |

---

## Domain Model

| Entity | Description |
|---|---|
| **League** | A football league; holds a set of teams and a schedule |
| **Team** | Belongs to at most one League; has a city and a squad |
| **Player** | Belongs to a Team; identified by shirt number, first name, last name |
| **Match** | A fixture between a home Team and an away Team within a League |
| **Goal** | Belongs to a Match; records scorer, optional assistant, minute, half, OG / penalty flag |

All entities use `UUID` as their primary key.
Technical entities not counted in the domain: **User** (authentication), **ChangeRequest** (approval workflow).

---

## Features

### Leagues
- Create a league by selecting exactly **6, 8, 10, or 16 teams** (each must have at least 6 players).
- Set a **Round 1 date and kick-off time** (15-minute granularity, 08:00–23:30) and the full round-robin schedule is generated automatically on creation.
- Schedules can also be generated or regenerated manually from the league detail page (admin only).
- View the league **standings table**, **round-by-round schedule**, and match results.

### Schedule Generation
- Round-robin algorithm (single or double leg depending on format).
- Matches within a round are spread across the same day, each starting 1 hour after the previous.
- Results are **auto-simulated** ~50 minutes after each kick-off (realistic goal distributions, scorers and assists drawn from the actual squad).

### Live Standings
- Any match kicked off within the last 46 minutes is treated as **live**.
- Standing rows for teams currently playing are highlighted: **green** (winning), **orange** (draw), **red** (losing).
- A pulsing **LIVE** badge next to each team shows the current score, updating every 30 seconds without a page reload.
- Live standings appear identically on both the league detail page and the individual team detail page.

### Teams & Players
- Create a team with name, city, and an optional squad (up to 12 players) all in one form.
- Randomizer buttons generate realistic names for players (full squad or minimum squad of 6).
- Each team gets a unique auto-generated SVG logo based on its name.
- Edit and delete teams and players (subject to the approval workflow for non-admin users).

### Goals & Match Detail
- Record goals with scorer, optional assist, minute (1–20 per half), OG and penalty flags.
- The half is derived from the minute; the running score after each goal is stored and displayed.
- The goal timeline is shown in an expandable match card, split by half with running scores.

### Teams Page
- Lists all teams with their league and player count.
- Sortable by name, city, or league.

---

## Security & Roles

| Role | Permissions |
|---|---|
| **Guest** | Home page, register, login only |
| **USER** | Browse everything; submit create / edit / delete proposals (see below) |
| **ADMIN** | All USER permissions plus: changes apply immediately, manage proposals, generate schedules |

The **first registered user** is automatically promoted to `ADMIN`.
Passwords are hashed with BCrypt.

---

## Change Approval Workflow

Regular users don't write directly to the database. When a `USER` submits a create, edit, or delete action, the request is stored as a **pending `ChangeRequest`** and an admin must act on it:

- **Approve** — re-runs the same validation as a direct admin action (stale proposals fail gracefully and stay pending rather than silently corrupting data), then applies the change.
- **Reject** — discards the proposal; the admin can select a domain-specific reason or enter a custom one.

Users track their own submissions on a **"My proposals"** page:
- Filter by status (Pending / Approved / Rejected) and entity type.
- See rejection reasons.
- **Resubmit** a corrected version of a rejected proposal with one click (form pre-filled with original input).
- **Cancel** pending proposals they no longer need.

Stale pending requests older than 7 days are automatically cleaned up by a scheduled job.

---

## Validation & Error Handling

- Every form is validated server-side with `@Valid` and Bean Validation annotations.
- Invalid submissions redisplay the form with field-level error messages in red.
- Business rules enforced both in the UI and in the service layer:
  - A match's home and away team must be different.
  - A team must have at least 6 players to join a league.
  - A league must have exactly 6, 8, 10, or 16 teams for schedule generation.
  - Shirt numbers must be unique within a team.
  - A team cannot be added to a league whose schedule has already started.
- Custom exceptions (`EntityNotFoundException`, `InvalidMatchException`, `InvalidLeagueOperationException`, etc.) are handled centrally by `GlobalExceptionHandler` (`@ControllerAdvice`), rendering a user-friendly error page instead of a raw stack trace.

---

## Running Locally

### Prerequisites
- JDK 17
- Maven (or use the included `mvnw` wrapper)
- Docker (for the database)

### Steps

**1. Start the database**

```bash
docker compose up -d
```

This starts a MySQL 8 container with the `football_league_manager` database, root password `12345`, exposed on host port **3307** (to avoid conflicts with a locally installed MySQL).

**2. Start the application**

```bash
./mvnw spring-boot:run
```

On Windows with a custom `JAVA_HOME`:

```powershell
.\run-local.ps1
```

**3. Open the app**

```
http://localhost:8080
```

Register a user — the first registration automatically receives the `ADMIN` role.

---

## Project Structure

```
src/main/java/bg/softuni/footballleague/
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
└── static/           CSS (style.css) and JS (random-names.js)
```
