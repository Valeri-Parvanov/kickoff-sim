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

const LEAGUE_FOODS = [
    { food: "Banitsa", types: ["Bowl", "Cup", "Derby", "Masters"] },
    { food: "Tarator", types: ["Trophy", "Cup", "Derby", "Open"] },
    { food: "Kyufte", types: ["Cup", "Kings", "Classic", "Derby"] },
    { food: "Kebapche", types: ["Kings", "Cup", "Derby", "Masters"] },
    { food: "Skara", types: ["Slam", "Showdown", "Cup", "Derby"] },
    { food: "Mekitsa", types: ["Masters", "Cup", "Derby", "Open"] },
    { food: "Kyoftavitsa", types: ["Cup", "Derby", "Klasika"] },
    { food: "Domat", types: ["Derby", "Cup", "Bowl", "Showdown"] },
    { food: "Lyutenitsa", types: ["League", "Derby", "Cup", "Masters"] },
    { food: "Zimnitsa", types: ["Zone", "Cup", "Derby"] },
    { food: "Shopska", types: ["Shield", "Showdown", "Cup", "Derby"] },
    { food: "Salata", types: ["Slam", "Series", "Cup", "Open"] },
    { food: "Sarma", types: ["Showdown", "Series", "Cup", "Derby"] },
    { food: "Sirene", types: ["Series", "Shield", "Cup", "Derby"] },
    { food: "Boza", types: ["Bowl", "Cup", "Nights", "Derby"] },
    { food: "Rakia", types: ["Rumble", "Cup", "Open", "Derby"] },
    { food: "Ayran", types: ["Arena", "Open", "Cup", "Derby"] },
    { food: "Tikvenik", types: ["Trophy", "Cup", "Derby"] },
    { food: "Snezhanka", types: ["Series", "Shield", "Cup", "Open"] },
    { food: "Mizeria", types: ["Masters", "Open", "Cup", "Derby"] },
    { food: "Chubritsa", types: ["Challenge", "Cup", "Derby"] },
    { food: "Kapama", types: ["Classic", "Cup", "Masters", "Derby"] },
    { food: "Ledeno Kafe", types: ["Cup", "Derby", "Open"] }
];

const CITIES = [
    "Sofia", "Bankya", "Novi Iskar", "Blagoevgrad", "Bansko", "Belitsa",
    "Gotse Delchev", "Hadzhidimovo", "Kresna", "Melnik", "Petrich", "Razlog",
    "Sandanski", "Simitli", "Yakoruda", "Burgas", "Aheloy", "Ahtopol",
    "Aytos", "Bulgarovo", "Chernomorets", "Kableshkovo", "Kameno", "Karnobat",
    "Malko Tarnovo", "Nesebar", "Obzor", "Pomorie", "Primorsko", "Sozopol",
    "Sredets", "Sungurlare", "Sveti Vlas", "Tsarevo", "Varna", "Aksakovo",
    "Beloslav", "Byala", "Dalgopol", "Devnya", "Ignatievo", "Provadia",
    "Suvorovo", "Valchi Dol", "Veliko Tarnovo", "Byala Cherkva", "Debelets", "Elena",
    "Gorna Oryahovitsa", "Kilifarevo", "Lyaskovets", "Pavlikeni", "Polski Trambesh", "Strazhitsa",
    "Suhindol", "Svishtov", "Zlataritsa", "Vidin", "Belogradchik", "Bregovo",
    "Dimovo", "Gramada", "Kula", "Vratsa", "Byala Slatina", "Knezha",
    "Kozloduy", "Krivodol", "Mezdra", "Miziya", "Oryahovo", "Roman",
    "Gabrovo", "Dryanovo", "Plachkovtsi", "Sevlievo", "Tryavna", "Dobrich",
    "Balchik", "General Toshevo", "Kavarna", "Shabla", "Tervel", "Kardzhali",
    "Ardino", "Dzhebel", "Krumovgrad", "Momchilgrad", "Kyustendil", "Boboshevo",
    "Bobov Dol", "Dupnitsa", "Kocherinovo", "Rila", "Sapareva Banya", "Lovech",
    "Apriltsi", "Letnitsa", "Lukovit", "Teteven", "Troyan", "Ugarchin",
    "Yablanitsa", "Montana", "Berkovitsa", "Boychinovtsi", "Brusartsi", "Chiprovtsi",
    "Lom", "Valchedram", "Varshets", "Pazardzhik", "Batak", "Belovo",
    "Bratsigovo", "Kostandovo", "Panagyurishte", "Peshtera", "Rakitovo", "Sarnitsa",
    "Septemvri", "Strelcha", "Velingrad", "Pernik", "Batanovtsi", "Breznik",
    "Radomir", "Tran", "Zemen", "Pleven", "Belene", "Dolna Mitropolia",
    "Dolni Dabnik", "Gulyantsi", "Levski", "Nikopol", "Pordim", "Slavyanovo",
    "Trastenik", "Plovdiv", "Asenovgrad", "Banya", "Brezovo", "Hisarya",
    "Kalofer", "Karlovo", "Klisura", "Krichim", "Kuklen", "Laki",
    "Parvomay", "Perushtitsa", "Rakovski", "Sadovo", "Saedinenie", "Sopot",
    "Stamboliyski", "Razgrad", "Isperih", "Kubrat", "Loznitsa", "Senovo",
    "Tsar Kaloyan", "Zavet", "Ruse", "Borovo", "Dve Mogili", "Marten",
    "Slivo Pole", "Vetovo", "Silistra", "Alfatar", "Dulovo", "Glavinitsa",
    "Tutrakan", "Sliven", "Kermen", "Kotel", "Nova Zagora", "Shivachevo",
    "Smolyan", "Chepelare", "Devin", "Dospat", "Madan", "Nedelino",
    "Rudozem", "Zlatograd", "Botevgrad", "Dolna Banya", "Dragoman", "Elin Pelin",
    "Etropole", "Godech", "Ihtiman", "Koprivshtitsa", "Kostenets", "Kostinbrod",
    "Pirdop", "Pravets", "Samokov", "Slivnitsa", "Svoge", "Zlatitsa",
    "Stara Zagora", "Chirpan", "Gurkovo", "Gulabovo", "Kazanlak", "Maglizh",
    "Nikolaevo", "Pavel Banya", "Radnevo", "Shipka", "Targovishte", "Antonovo",
    "Omurtag", "Opaka", "Popovo", "Haskovo", "Dimitrovgrad", "Harmanli",
    "Ivaylovgrad", "Lyubimets", "Madzharovo", "Merichleri", "Simeonovgrad", "Svilengrad",
    "Topolovgrad", "Shumen", "Kaolinovo", "Kaspichan", "Novi Pazar", "Pliska",
    "Smyadovo", "Varbitsa", "Veliki Preslav", "Yambol", "Bolyarovo", "Elhovo",
    "Straldzha"
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

const LEAGUE_TYPES_FALLBACK = [
    "Cup", "Derby", "League", "Masters", "Trophy",
    "Classic", "Open", "Series", "Shield", "Challenge"
];

function leagueTypesFor(food) {
    const key = (food || "").trim().toLowerCase();
    const entry = LEAGUE_FOODS.find(function (e) {
        return e.food.toLowerCase() === key;
    });
    return entry ? entry.types : LEAGUE_TYPES_FALLBACK;
}

function syncLeagueName(foodId, typeId, targetId) {
    const food = document.getElementById(foodId).value.trim();
    const type = document.getElementById(typeId).value.trim();
    document.getElementById(targetId).value = [food, type].filter(Boolean).join(" ");
}

function splitLeagueName(foodId, typeId, targetId) {
    const full = document.getElementById(targetId).value.trim();
    if (!full) return;
    const cut = full.lastIndexOf(" ");
    if (cut === -1) {
        document.getElementById(foodId).value = full;
        return;
    }
    document.getElementById(foodId).value = full.slice(0, cut);
    document.getElementById(typeId).value = full.slice(cut + 1);
}

function fillRandomLeagueFood(foodId, typeId, targetId) {
    const entry = randomFrom(LEAGUE_FOODS);
    document.getElementById(foodId).value = entry.food;

    const typeEl = document.getElementById(typeId);
    if (entry.types.indexOf(typeEl.value.trim()) === -1) {
        typeEl.value = randomFrom(entry.types);
    }
    syncLeagueName(foodId, typeId, targetId);
}

function fillRandomLeagueType(foodId, typeId, targetId) {
    const foodEl = document.getElementById(foodId);
    if (!foodEl.value.trim()) {
        foodEl.value = randomFrom(LEAGUE_FOODS).food;
    }
    document.getElementById(typeId).value = randomFrom(leagueTypesFor(foodEl.value));
    syncLeagueName(foodId, typeId, targetId);
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
    if (label) {
        label.textContent = document.getElementById("global-js-i18n")
            .dataset.playerCount.replace("{0}", visible);
    }
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
