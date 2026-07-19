const TEAM_NAMES = [
    "Kalpazanite", "Kyufteta", "Pensionerite", "Shegobiytsi", "Chudatsite",
    "Haimanite", "Marzelivite", "Bosonogite", "Palavnitsi", "Domati",
    "Zabravenite", "Poslednite", "Umornite", "Zakusnelite", "Sanlivite",
    "Shashavite", "Smotanite", "Zaspalite", "Gladnite", "Nepobedimite",
    "Shampionite", "Provalenite", "Bezgrizhnite", "Nadarenite",
    "Neudachnicite", "Divannite", "Birenite", "Pitsarite", "Sirenkite",
    "Banicharite", "Dyunerdzhiite", "Rakidzhiite", "Naglite", "Smahnatite",
    "Otkachalkite", "Propadnalite", "Zhadnite", "Bezpametnite", "Objurkanite",
    "Zagubenite"
];

const PLAYER_FIRST_NAMES = [
    "Ivan", "Georgi", "Nikolay", "Stefan", "Martin", "Dimitar",
    "Petar", "Viktor", "Hristo", "Boyan", "Plamen", "Stoyan",
    "Krasimir", "Mihail", "Radoslav", "Yavor", "Valentin", "Deyan",
    "Zhivko", "Kostadin", "Lyubomir", "Todor", "Aleksandar", "Stanimir",
    "Milen", "Angel", "Atanas", "Rosen", "Ilian", "Branimir",
    "Tsvetomir", "Galin", "Emil", "Kalin", "Tihomir", "Blagovest",
    "Momchil", "Dobromir", "Desislav", "Ventsislav"
];

const PLAYER_LAST_NAMES = [
    "Ivanov", "Petrov", "Georgiev", "Dimitrov", "Stoyanov", "Kolev",
    "Todorov", "Marinov", "Atanasov", "Hristov", "Kostadinov", "Slavov",
    "Popov", "Nikolov", "Yordanov", "Borisov", "Angelov", "Tsonev",
    "Nedyalkov", "Penchev", "Petkov", "Rusev", "Lazarov", "Stoichev",
    "Genov", "Vasilev", "Simeonov", "Spasov", "Tsvetkov", "Stefanov",
    "Mihaylov", "Iliev", "Stanchev", "Raykov", "Nedelchev", "Mitrov",
    "Blagoev", "Zhivkov", "Aleksandrov", "Manchev"
];

const LEAGUE_NAME_PREFIXES = [
    "Sofia", "National", "Premier", "Capital", "Regional",
    "Amateur", "Municipal", "Indoor", "Winter", "Summer",
    "Open", "Business", "Rooftop", "Weekend", "Midnight",
    "Underdog", "Sunday", "Backstreet", "Iron", "Golden"
];

const LEAGUE_NAME_SUFFIXES = [
    "League", "Division", "Championship", "Cup", "Series", "Tournament",
    "Derby", "Showdown", "Circuit", "Classic", "Invitational"
];

const LEAGUE_NAMES = [
    "Kyufte Cup", "Banitsa Bowl", "Rakia Rumble", "Sirene Series",
    "Shopska Shield", "Pensioner's Pride", "Domat Derby", "Mekitsa Masters",
    "Ayrian Arena", "Tarator Trophy", "Kapama Klasico", "Lyutenitsa League",
    "Boza Bracket", "Kebapche Kings", "Sarma Showcase", "Salata Slam",
    "Snezhanka Cup", "Chubritsa Challenge", "Mecho Millennium", "Zimnitsa Zone",
    "Barcode United Cup", "Sunday Sunflower League", "Noshten Nadprevar",
    "Gradinska Gauntlet", "Tikvenik Trophy", "Kyoftavitsa Klasika",
    "Party Boza League", "Skara Slam", "Ledeno Kafe Cup", "Mizeria Masters"
];

const CITIES = [
    "Sofia", "Plovdiv", "Varna", "Burgas", "Ruse", "Pleven",
    "Stara Zagora", "Sliven", "Dobrich", "Shumen", "Pernik", "Haskovo",
    "Yambol", "Pazardzhik", "Blagoevgrad", "Veliko Tarnovo", "Vratsa", "Gabrovo",
    "Vidin", "Montana", "Lovech", "Targovishte", "Razgrad", "Silistra",
    "Kardzhali", "Kyustendil", "Smolyan", "Sozopol", "Kazanlak", "Botevgrad",
    "Dupnitsa", "Sandanski", "Petrich", "Velingrad", "Asenovgrad", "Gotse Delchev",
    "Samokov", "Sevlievo", "Troyan", "Popovo", "Lom", "Berkovitsa",
    "Gorna Oryahovitsa", "Svishtov", "Lyaskovets", "Tryavna", "Nessebar", "Pomorie",
    "Aytos", "Nova Zagora"
];

function randomFrom(list) {
    return list[Math.floor(Math.random() * list.length)];
}

function randomTeamName() {
    return randomFrom(TEAM_NAMES);
}

function randomPlayerFirstName() {
    return randomFrom(PLAYER_FIRST_NAMES);
}

function randomPlayerLastName() {
    return randomFrom(PLAYER_LAST_NAMES);
}

function randomLeagueName() {
    if (Math.random() < 0.5) {
        return randomFrom(LEAGUE_NAMES);
    }
    return randomFrom(LEAGUE_NAME_PREFIXES) + " " + randomFrom(LEAGUE_NAME_SUFFIXES);
}

function randomCity() {
    return randomFrom(CITIES);
}

function fillRandomTeamName(inputId) {
    document.getElementById(inputId).value = randomTeamName();
}

function fillRandomPlayerName(firstNameInputId, lastNameInputId) {
    document.getElementById(firstNameInputId).value = randomPlayerFirstName();
    document.getElementById(lastNameInputId).value = randomPlayerLastName();
}

function fillRandomLeagueName(inputId) {
    document.getElementById(inputId).value = randomLeagueName();
}

function fillRandomCity(inputId) {
    document.getElementById(inputId).value = randomCity();
}

function randomizeAllSquad() {
    document.querySelectorAll(".js-firstname").forEach(function (el) {
        if (!el.value.trim()) {
            el.value = randomPlayerFirstName();
        }
    });
    document.querySelectorAll(".js-lastname").forEach(function (el) {
        if (!el.value.trim()) {
            el.value = randomPlayerLastName();
        }
    });
}

function randomizeMinSquad() {
    var rows = document.querySelectorAll("tbody tr");
    for (var i = 0; i < rows.length; i++) {
        var fn = rows[i].querySelector(".js-firstname");
        var ln = rows[i].querySelector(".js-lastname");
        if (i >= 6) {
            if (fn) fn.value = "";
            if (ln) ln.value = "";
        } else if (fn && !fn.value.trim()) {
            fn.value = randomPlayerFirstName();
            if (ln) ln.value = randomPlayerLastName();
        }
    }
}

function randomizeAllSquadIn(scopeEl) {
    scopeEl.querySelectorAll("tbody tr:not(.wizard-row-hidden) .js-firstname").forEach(function (el) {
        if (!el.value.trim()) {
            el.value = randomPlayerFirstName();
        }
    });
    scopeEl.querySelectorAll("tbody tr:not(.wizard-row-hidden) .js-lastname").forEach(function (el) {
        if (!el.value.trim()) {
            el.value = randomPlayerLastName();
        }
    });
}

function setSquadSizeIn(blockEl, size) {
    var rows = Array.from(blockEl.querySelectorAll("tbody tr"));
    rows.forEach(function (row, i) {
        var fn = row.querySelector(".js-firstname");
        var ln = row.querySelector(".js-lastname");
        if (i < size) {
            row.classList.remove("wizard-row-hidden");
            if (fn && !fn.value.trim()) fn.value = randomPlayerFirstName();
            if (ln && !ln.value.trim()) ln.value = randomPlayerLastName();
        } else {
            if (fn) fn.value = "";
            if (ln) ln.value = "";
            row.classList.add("wizard-row-hidden");
        }
    });
    wizardUpdateSquadCount(blockEl);
}

function clearSquadIn(blockEl) {
    blockEl.querySelectorAll(".js-firstname").forEach(function (el) { el.value = ""; });
    blockEl.querySelectorAll(".js-lastname").forEach(function (el) { el.value = ""; });
}

function wizardUpdateSquadCount(blockEl) {
    var visible = blockEl.querySelectorAll("tbody tr:not(.wizard-row-hidden)").length;
    var label = blockEl.querySelector(".wizard-squad-count");
    if (label) label.textContent = visible + " player" + (visible === 1 ? "" : "s");
    var growBtn = blockEl.querySelector(".wizard-grow-btn");
    var shrinkBtn = blockEl.querySelector(".wizard-shrink-btn");
    if (growBtn) growBtn.disabled = visible >= 12;
    if (shrinkBtn) shrinkBtn.disabled = visible <= 6;
}

function growSquadIn(blockEl) {
    var hiddenRow = blockEl.querySelector("tbody tr.wizard-row-hidden");
    if (hiddenRow) {
        hiddenRow.classList.remove("wizard-row-hidden");
        var fn = hiddenRow.querySelector(".js-firstname");
        var ln = hiddenRow.querySelector(".js-lastname");
        if (fn && !fn.value.trim()) fn.value = randomPlayerFirstName();
        if (ln && !ln.value.trim()) ln.value = randomPlayerLastName();
    }
    wizardUpdateSquadCount(blockEl);
}

function shrinkSquadIn(blockEl) {
    var visibleRows = blockEl.querySelectorAll("tbody tr:not(.wizard-row-hidden)");
    if (visibleRows.length <= 6) return;
    var last = visibleRows[visibleRows.length - 1];
    var fn = last.querySelector(".js-firstname");
    var ln = last.querySelector(".js-lastname");
    if (fn) fn.value = "";
    if (ln) ln.value = "";
    last.classList.add("wizard-row-hidden");
    wizardUpdateSquadCount(blockEl);
}

function randomizeRow(btn) {
    var row = btn.closest("tr");
    var fn = row.querySelector(".js-firstname");
    var ln = row.querySelector(".js-lastname");
    if (fn) fn.value = randomPlayerFirstName();
    if (ln) ln.value = randomPlayerLastName();
}

function randomizeOneRow() {
    var rows = document.querySelectorAll("tbody tr");
    var firstFn = null;
    var firstLn = null;
    for (var i = 0; i < rows.length; i++) {
        var fn = rows[i].querySelector(".js-firstname");
        var ln = rows[i].querySelector(".js-lastname");
        if (!fn) continue;
        if (firstFn === null) { firstFn = fn; firstLn = ln; }
        if (!fn.value.trim()) {
            fn.value = randomPlayerFirstName();
            if (ln) ln.value = randomPlayerLastName();
            return;
        }
    }
    if (firstFn) {
        firstFn.value = randomPlayerFirstName();
        if (firstLn) firstLn.value = randomPlayerLastName();
        var skippedFirst = false;
        for (var j = 0; j < rows.length; j++) {
            var fn2 = rows[j].querySelector(".js-firstname");
            var ln2 = rows[j].querySelector(".js-lastname");
            if (!fn2) continue;
            if (!skippedFirst) { skippedFirst = true; continue; }
            fn2.value = "";
            if (ln2) ln2.value = "";
        }
    }
}
