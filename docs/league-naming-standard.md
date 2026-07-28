# League Naming Standard — Kickoff Sim

The vocabulary used by the league-name generators, and the rules that decide what may
enter it. A name is not just text: `LeagueLogoThemes.match()` resolves it to a logo motif
by case-insensitive substring, so naming and artwork are one system.

---

## 0. The composition model

Names are **not** stored whole. They are composed at generation time from two parts, the
same way a team is *name + city* and a player is *first name + last name*:

```
league name = food + tournament type
```

`random-names.js` holds `LEAGUE_FOODS`, where each food carries **its own curated list of
allowed types**:

```json
{ "food": "Shopska", "types": ["Shield", "Showdown", "Cup", "Derby"] }
```

The types are curated per food rather than drawn from one global pool. That is the whole
point: a global pool would produce `Ćevapi Cup` and `Shopska Slam`, which §6 forbids. By
listing only sanctioned types per food, the alliteration and phoneme rules are enforced by
construction and cannot be violated by chance.

**Consequences**

- The food is always the leading token, so theme resolution is unambiguous — no
  `Shkembe Sunday` style collision where the type word steals the match.
- Adding one type to one food adds one name. Adding one food adds as many names as it has
  types, and requires a matching motif in `LeagueLogoThemes` (C3).
- Two foods may share a motif only if the standard explicitly accepts it (see §9).

---

## 1. Hard constraints (project-specific, non-negotiable)

These come from the codebase, not from taste. A name violating any of them is rejected
regardless of how funny it is.

| # | Constraint | Why |
|---|---|---|
| C1 | ≤ 22 characters including spaces | League cards, standings headers and the matches list truncate beyond this |
| C2 | 2 words preferred, 3 maximum | Third word almost always breaks C1 |
| C3 | The food word must map to a theme in `LeagueLogoThemes` | Otherwise the league silently falls back to the generic monogram badge |
| C4 | One canonical transliteration per food, forever | The matcher is substring-based: `Kiufte` and `Kyufte` are different strings and only one will match |
| C5 | A new keyword must not be a substring of an existing keyword, and vice versa | First match in `THEMES` wins; overlapping keywords make theme assignment order-dependent |
| C6 | The food must be drawable as a flat 2-colour silhouette at 40×40 px | The badge motif area is ~34 px wide inside an 80×90 viewBox |

**C6 is the one that kills most candidates.** Foods that are "a brown pile on a plate"
(kavarma, popara, drob sarma) score high on humour and zero on logo.

---

## 2. Naming patterns

### 2.1 Patterns worth keeping

Ranked by how many usable names they produce per attempt.

| Rank | Pattern | Example | Why it works |
|---|---|---|---|
| 1 | **Food + Tournament Word** | Banitsa Bowl | Most believable as a real amateur tournament. Shortest. Never sounds forced. |
| 2 | **Food + Mock-Prestige Word** | Lokum Invitational | The comedy engine: humble food × grand institution. The wider the status gap, the funnier. |
| 3 | **Food + Collective Noun** | Lukanka Legends | Best mascot generator — a collective noun implies a character. |
| 4 | **Food + Conflict Noun** | Rakia Rumble | High energy, good for derby-type leagues. Wears out fast if overused. |
| 5 | **Occasion + Food** | Shkembe Sunday | Mirrors how real amateur leagues are actually named (by when they play). |

### 2.2 Patterns to reject

| Pattern | Verdict |
|---|---|
| Food + Adjective + Tournament Word | Breaks C1 in ~90% of cases ("Golden Lyutenitsa Championship" = 31 chars) |
| Two foods joined | "Rakia & Lukanka Cup" — breaks C1, and C3 becomes ambiguous: which theme wins? |
| Place + Food + Word | Three words, breaks C1, and city names already appear on teams |
| Food + generic event noun ("Tournament", "Event", "Competition") | Adds length, adds nothing. "Tournament" is 10 characters of no personality. |

### 2.3 New pattern proposed

**Food-as-verdict.** Some Balkan food words are already funny words in their own right,
independent of the food. `Urnebes` literally means *uproar/chaos* in Serbian. `Mizeria`
reads as *misery*. Names built on these carry the joke without needing a second funny word,
so they can take a completely straight tournament word and stay funny:

> Urnebes Derby · Mizeria Masters

This is the highest-value pattern discovered, because it survives translation — a viewer
who doesn't know the food still gets a joke.

---

## 3. Food ranking

Scored 1–5 on: **M**emorability, **Ma**scot, **L**ogo, **R**ecognisability, **N**atural combinations.
Sorted by total. Only Tier A and B may enter the shipping vocabulary.

### Tier A — ship these

| Food | M | Ma | L | R | N | Note |
|---|---|---|---|---|---|---|
| Banitsa | 5 | 4 | 5 | 5 | 5 | Spiral is instantly readable as a silhouette |
| Rakia | 5 | 5 | 5 | 5 | 4 | Bottle + shot glass; the mascot writes itself |
| Tarator | 5 | 4 | 5 | 5 | 5 | Bowl + cucumber discs, unmistakable |
| Boza | 5 | 4 | 5 | 4 | 4 | Brown liquid + foam; the spill variant is the best animation in the set |
| Lyutenitsa | 5 | 4 | 5 | 5 | 4 | Jar with red spread, strong colour identity |
| Kebapche | 5 | 5 | 4 | 5 | 5 | Grill marks read at small size |
| Shopska | 4 | 4 | 5 | 5 | 5 | Three-colour salad = built-in palette |
| Ayran | 4 | 4 | 5 | 4 | 4 | White spill is the most distinctive silhouette available |
| Kyufte | 4 | 5 | 4 | 5 | 5 | Round, simple, mascot-friendly |
| Mekitsa | 4 | 4 | 4 | 4 | 5 | Irregular golden blob, distinct from banitsa |
| Turshiya | 4 | 4 | 5 | 4 | 4 | Jar of pickles; strong shape, natural alliteration with Trophy |
| Sarma | 4 | 3 | 4 | 5 | 4 | Green roll, clean silhouette |
| Baklava | 5 | 3 | 5 | 5 | 4 | Diamond lattice is a graphic gift |
| Urnebes | 5 | 5 | 3 | 2 | 5 | Weak recognisability, unbeatable word |
| Lukanka | 4 | 4 | 4 | 4 | 5 | Flat sausage, distinct from nadenitsa |
| Shkembe | 5 | 5 | 3 | 4 | 4 | Peak comedy, mediocre logo — carried by the word |
| Ajvar | 4 | 3 | 4 | 4 | 4 | Red pepper, regional reach beyond Bulgaria |
| Kashkaval | 4 | 4 | 5 | 4 | 4 | Yellow wheel with holes; reads at any size |
| Tulumba | 4 | 4 | 4 | 4 | 5 | Ridged syrup tube, unlike any other item |
| Meze | 4 | 3 | 4 | 4 | 5 | Short word, plate of small things |

### Tier B — usable, with care

Kompot, Snezhanka, Kyopolou, Kajmak, Musaka, Tikvenik, Burek, Ćevapi, Pljeskavica, Katak,
Bob Chorba, Lokum, Gyuvech, Kozunak, Sudzhuk, Mastika, Menta, Chushki, Halva, Patatnik,
Zelnik, Kachamak, Pindjur, Kadaif, Skara, Pastarma, Nadenitsa, Sirene.

### Tier C — rejected

| Food | Reason |
|---|---|
| Kavarma, Popara, Drob Sarma, Trahana | Fail C6 — no distinguishable silhouette |
| Tutmanik | Fails distinctiveness: fourth golden pastry after Banitsa, Tikvenik, Burek |
| Somun, Ashure | Too obscure; recognisability 1 |
| Any brand name (Kamenitza etc.) | Trademark |

### Discovered beyond the supplied list

**Urnebes**, **Turshiya**, **Kashkaval**, **Meze**, **Gyuvech**, **Chushki**, **Mastika**,
**Menta**, **Sudzhuk**, **Kozunak**, **Patatnik**, **Kachamak**, **Pindjur**, **Zelnik**.
Of these, Urnebes and Turshiya are the strongest additions.

---

## 4. Second-word ranking

| Tier | Words | Character |
|---|---|---|
| **1 — always safe** | Derby, Cup, Bowl, Masters, Trophy, Classic | Read as genuine tournaments; never fight the food word |
| **2 — strong, use freely** | Kings, Legends, Titans, Showdown, Shield, Series, Slam, Open, Challenge | Mascot-friendly; Titans/Kings imply a character directly |
| **3 — use sparingly, max 3 per 50** | Rumble, Brawl, Clash, Ambush, Gauntlet, Nights | High energy, low believability. Fun in isolation, exhausting in a list |
| **Rejected** | Tournament, Competition, Event, Grand Prix, Championship Series | Length without personality; Grand Prix is the wrong sport |

**Championship** is demoted from the supplied list: 12 characters, so it breaks C1 with
almost every food longer than "Bob".

---

## 5. Final tournament words

Ranked for this project: **Cup · Derby · Bowl · Masters · Trophy · Classic · Open ·
Series · Shield · Challenge · Invitational · League**

- `League` ranks last despite being obvious — it is the least distinctive word available and
  the app already says "league" everywhere in the UI.
- `Invitational` is the best mock-prestige word but costs 12 characters, so it pairs only
  with foods of ≤ 9 letters.
- Additions worth using: **Bowl** (short, sporty, underused in Bulgarian context) and
  **Sunday** (as an occasion word — reads exactly like real amateur football).

---

## 6. Alliteration rules

Alliteration is a bonus, never a goal.

1. **Match the phoneme, not the letter.** `Ćevapi Cup` looks alliterative and is not —
   /tʃ/ against /k/. Forbidden. `Kompot Cup` is /k/ against /k/. Allowed.
2. **Consonant clusters must agree.** `Shkembe Showdown` (/ʃ/–/ʃ/) works.
   `Shopska Slam` (/ʃ/–/s/) does not.
3. **Never trade down for alliteration.** If the alliterative pairing is a Tier-3 second
   word and the non-alliterative one is Tier 1, take Tier 1.
4. **Density cap: at most 1 alliterative name per 3.** Beyond that the vocabulary reads as
   a tongue-twister rather than a set of tournaments.
5. **Never alliterate a food that is already hard to pronounce.** `Pljeskavica Playoff`
   compounds two difficulties.

---

## 7. Quality bar

A name ships only if it passes all of:

- ≤ 22 characters, 2–3 words
- Pronounceable on first read by someone who doesn't speak Bulgarian
- Funny by contrast or by sound, never by absurdity
- Plausible as a real Bulgarian amateur mini-football tournament
- Implies one specific mascot, not a vague mood
- Maps to a theme that is drawable per C6
- Distinct in silhouette from every other name already in the set

---

## 8. Self-critique of the above, and what changed

The first draft of this standard had four real weaknesses:

1. **It optimised names in isolation, not as a set.** Fifty individually good names can still
   be a bad vocabulary — four golden pastries produce four near-identical badges.
   *Fix:* added set-level distinctiveness to the quality bar and cut Tutmanik.
2. **It treated alliteration as a spelling property.** That admits `Ćevapi Cup` and
   `Kyopolou Kings`, which are not alliterative when spoken.
   *Fix:* rule 6.1, phoneme not letter.
3. **It had no length budget**, so "Food + Adjective + Tournament Type" looked viable.
   Measured against the UI it is not.
   *Fix:* C1/C2, and the pattern is now rejected outright.
4. **It ignored the substring matcher.** Two spellings of one food, or a keyword contained
   in another keyword, silently break logo assignment.
   *Fix:* C4 and C5.

A fifth weakness remains and is accepted: humour and logo strength are in tension.
`Shkembe` is the funniest word available and one of the weakest motifs. The standard
resolves this by allowing word-carried names (§2.3) rather than pretending the conflict
does not exist.

---

## 9. Known debt: foods sharing a motif

The composition model puts every food into rotation, which exposed something the
whole-name list hid — several foods currently resolve to the **same** badge:

| Shared motif | Foods |
|---|---|
| Fried dough | Mekitsa, Kyoftavitsa |
| White bowl with cucumber | Tarator, Snezhanka, Mizeria |
| Grilled meat | Kyufte, Kebapche, Skara |
| Golden spiral | Banitsa, Tikvenik |
| Green roll | Sarma, Salata, Chubritsa |
| Jar with red spread | Lyutenitsa, Zimnitsa |

Grouping is defensible where the foods genuinely look alike (Kyufte/Kebapche, both grilled
minced meat). It is **not** defensible for Salata vs Chubritsa, or Tarator vs Mizeria,
which share nothing visually.

Rule going forward: a food may join an existing motif group only if a viewer who knows both
foods would accept the drawing as either. Otherwise it needs its own motif before it may
enter `LEAGUE_FOODS`.
