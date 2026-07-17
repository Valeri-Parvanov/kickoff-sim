# Kickoff Sim — Project Context

## Tech stack
- Spring Boot 4.1.0, JDK 17, MySQL 8, Thymeleaf, Spring Security, Spring Cache, Mockito/JUnit 5
- No Flyway/Liquibase — schema managed by `spring.jpa.hibernate.ddl-auto=update`
- DB: `kickoff_sim` @ localhost:3306 (root/12345)
- DB config: `src/main/resources/application.properties`
- Local run: `run-local.ps1` (sets JAVA_HOME then runs mvn spring-boot:run)

## Package layout (`bg.softuni.footballleague`)
```
controller/         AuthController, LeagueController, TeamController, PlayerController,
                    MatchController, SquadController, ChangeRequestController,
                    MyChangeRequestController, HomeController, GlobalModelAttributes,
                    UserAdminController, ProfileController
service/            LeagueService, TeamService, PlayerService, MatchService,
                    ScheduleService, ChangeRequestService, UserService
service/impl/       (implementations of the above)
repository/         LeagueRepository, TeamRepository, PlayerRepository, MatchRepository,
                    GoalRepository, UserRepository, ChangeRequestRepository
model/              League, Team, Player, Match, Goal, User, ChangeRequest,
                    enums: Role, ChangeAction, ChangeRequestStatus, EntityType, Half, LeagueFormat
dto/                LeagueDto, TeamDto, PlayerDto, MatchDto, GoalDto, GoalEventDto,
                    TeamCreateForm, TeamSquadPayload, ChangeRequestView, StandingRow,
                    LeagueDetailView, RegisterDto, ScheduleForm, SquadForm, PlayerRowDto
exception/          GlobalExceptionHandler, EntityNotFoundException,
                    ChangeRequestApprovalException, InvalidLeagueOperationException,
                    InvalidMatchException, DuplicateShirtNumberException,
                    SquadLimitExceededException, StaleSessionException,
                    UsernameAlreadyExistsException, InvalidGoalException
config/             JacksonConfig
security/           SecurityConfig, CustomUserDetailsService
scheduling/         ChangeRequestScheduler, ScheduleAutoGenerateListener, TeamCreatedEvent
web/                SortSupport, SquadRowValidator
```

## Templates (`src/main/resources/templates/`)
```
leagues/        list.html, detail.html, form.html
teams/          list.html, detail.html, create.html, form.html, squad.html
matches/        list.html, form.html, goals/new.html, goals/edit.html
players/        list.html, form.html
admin/          change-requests.html, users.html
fragments/      topbar.html, sortable.html
my-change-requests.html, profile.html, index.html, login.html, register.html, error.html
```

## Test structure (`src/test/`)
```
service/impl/   LeagueServiceImplTest, TeamServiceImplTest, PlayerServiceImplTest,
                MatchServiceImplTest, UserServiceImplTest, ChangeRequestServiceImplTest,
                ChangeRequestMaintenanceTest
controller/     SquadControllerTest, SquadPageRenderingTest, TeamCreatePageRenderingTest
scheduling/     ChangeRequestSchedulerTest
FootballLeagueManagerApplicationTests (context load only)
```

## Key domain rules
- Teams can exist without a league (leagueId nullable)
- League creation requires at least one team with 6+ players
- Deleting a league detaches teams (sets league_id = NULL), does NOT delete them
- USER role → changes go to pending (ChangeRequest); ADMIN role → applied immediately
- First registered user gets ADMIN role automatically
- @CacheEvict(value = "leagues", allEntries = true) on all league write operations

## Patterns learned from bug fixes

### Issue #4 (session 2) — Team (City) format in matches list + expand hint
- `matches/list.html`: home/away team spans now use `th:text="${match.homeTeamName + (match.homeTeamCity != null ? ' (' + match.homeTeamCity + ')' : '')}"` — both in Results and Upcoming sections.
- Expand hint: `<div class="match-expand-hint">▾ details</div>` added inside `<summary>` just before closing tag. Hidden via `details.match-card[open] > summary .match-expand-hint { display: none }` when expanded.

### Issue #2 (session 2) — Scrollbar appears even when content fits
- Root cause: `tbody tr:hover { transform: translateX(3px) }` pushes every hovered row 3px to the right → `.table-wrapper { overflow-x: auto }` detects overflow → scrollbar appears even on narrow tables.
- Fix: change to `transform: translateY(-1px)` — vertical lift, no horizontal overflow. Applies to ALL tables in the app via the same CSS rule.

### Issue #1б (session 2) — Auto-simulate match results 50 min after kickoff
- `MatchRepository.findZeroZeroMatchesInWindow(from, to)`: JPQL with `@EntityGraph` for matches in a 3-hour window ending at `now - 50min` with 0-0 score.
- `ScheduleService.simulatePastMatches()` + impl: skips matches that already have goals (admin-entered); calls existing private `simulateResult()`. Annotated `@CacheEvict(value="leagues", allEntries=true)`.
- `MatchResultScheduler` (`scheduling/`): `@Scheduled(fixedRate = 120_000)` — runs every 2 minutes.
- Ambiguity: a genuine admin-entered 0-0 result within the 3-hour window will get auto-simulated. Acceptable trade-off for this project.

### Issue #1а (session 2) — Team (City) format in standings and schedule
- `StandingRow`: add `teamCity` field; populate from `t.getCity()` in `LeagueServiceImpl.findDetail()` standings loop.
- `MatchDto`: add `homeTeamCity` / `awayTeamCity`; populate from `match.getHomeTeam().getCity()` in `MatchServiceImpl.toDto()`.
- Thymeleaf: `th:text="${row.teamName + (row.teamCity != null ? ' (' + row.teamCity + ')' : '')}"` — null-safe city suffix on every team reference.

### Issue #11 — Users can cancel requests + type filter
- New `cancelMine(UUID, Authentication)` in `ChangeRequestService` + impl: checks ownership + PENDING status, then `deleteById`. Throws `ChangeRequestApprovalException` otherwise.
- `MyChangeRequestController`: `DELETE /{id}` endpoint; `?type=` param filters by `EntityType.name()` alongside `?status=`.
- Status tabs preserve `type` param in href. Type dropdown preserves `status` via hidden input in the same form.
- Cancel button (`btn-del`) shown only for PENDING cards; Resubmit shown only for REJECTED non-DELETE cards.

### Issue #10 — Randomizer improvements
- `random-names.js`: added `randomizeMinSquad()` (fills first 6 empty rows) and `randomizeRow(btn)` (fills one row by `btn.closest('tr')`).
- `teams/create.html`: two buttons — "Full squad (12)" → `randomizeAllSquad()`, "Min squad (6)" → `randomizeMinSquad()`.
- `teams/squad.html`: header row has empty 5th column; each tbody row has a `dice-btn` cell calling `randomizeRow(this)`.

### Issue #9 — Users can propose adding players to existing teams
- `SquadController` already uses `submitOrExecute` → supports USER role out of the box.
- Only fix needed: change `sec:authorize="hasRole('ADMIN')"` → `sec:authorize="isAuthenticated()"` on the squad button in `teams/detail.html`.
- Button label changed to "Propose players" to signal intent for non-admin users.

### Issue #8 — My proposals compactness
- Status tabs (All / Pending / Rejected / Approved) with count badges via `?status=` query param in `MyChangeRequestController`.
- Hard limit: show max 50 most recent per status; show info banner when truncated.
- Filtering done in controller (not service) to avoid changing service interface/tests.
- CSS: `.cr-status-tabs` + `.cr-tab-count` + active state.

### Issue #7 — Horizontal overflow flicker
- Root cause: no `overflow-x: hidden` on `html`/`body` → horizontal scrollbar appears/disappears as elements resize → page flickers.
- Fix: `html { overflow-x: hidden }` + `body { overflow-x: hidden }` + `max-width: 220px` on `.ls-filter-select`.
- `position: sticky` on topbar continues to work with this fix in modern browsers.

### Issue #6 — Matches list: order + date filter
- Results section rendered BEFORE Upcoming (past = closer to today).
- Date filter: `@RequestParam LocalDate date` + `@DateTimeFormat(iso=ISO.DATE)` in `MatchController.list()`. `matchDates` = distinct sorted dates from all matches. Both selects in ONE `<form>` — `onchange="this.form.submit()"` preserves both filters. No hidden duplicate league input.
- Empty message uses `selectedLeague != null or selectedDate != null` to cover both filter cases.

### Issue #5 — Standings only from played matches
- Generated matches have `homeScore=0, awayScore=0` (not null) + future `playedAt`. The null-check alone is NOT enough.
- `LeagueServiceImpl.findDetail()`: skip match in standings loop if `playedAt.isAfter(LocalDateTime.now())`.
- Template condition `standings.isEmpty()` is ALWAYS false (has all teams). Use Thymeleaf selection: `#lists.isEmpty(league.standings.?[played > 0])`.

### Issue #4 — Team name list
- `TEAM_NAMES` in `random-names.js` — mix of real Bulgarian clubs + cool mini-tournament names (Feniks, Titan, Vihor…). No prefix+suffix, just standalone names.

### Issue #3 — City list
- `CITIES` array in `random-names.js` — 50 Bulgarian regional + major cities. Update there if expanding.

### Issue #2 — Flash / error messages always visible
- Messages fragment is in `fragments/topbar.html` → `th:fragment="messages"`. Shows `statusMessage` (green `.status-message`), `errorMessage` (red `.error-message`), `scheduleError` (red `.error-message`).
- Every template must have `<div th:replace="~{fragments/topbar :: messages}"></div>` as the FIRST element inside `<main class="page">`.
- Spring binding global errors (`#fields.hasGlobalErrors()`) belong at the TOP of the `<form>` element, never buried after fields.
- Goal forms use `errorMessage` model attribute (not flash); the fragment handles it.

### Issue #1 — Team name randomizer + league team display
- `random-names.js`: TEAM_NAMES is a flat list (no prefix+suffix); `randomTeamName()` returns `randomFrom(TEAM_NAMES)` — no city in the name.
- League form `availableTeams` are TeamDto objects → have `.city` field → display as `${team.name} + ' (' + ${team.city} + ')'`.

## Rules for Codex in this project
- The structure above is complete. Do not read files for orientation — ask if something is unclear.
- Never modify or delete the DB schema by touching model annotations without being asked.
- Run /compact when context reaches ~30% remaining.
- Tests are run manually by the user with `mvn test`. Never run them automatically.
