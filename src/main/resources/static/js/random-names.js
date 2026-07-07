const TEAM_NAMES = [
    "Levski", "CSKA", "Slavia", "Botev", "Lokomotiv",
    "Chernomorets", "Beroe", "Spartak", "Arda", "Litex",
    "Minyor", "Akademik", "Balkan", "Dunav", "Svetkavitsa",
    "Vidima", "Etar", "Rilski", "Nesebar", "Pirin",
    "Hebar", "Marek", "Dobrudzha", "Neftochimic", "Maritsa",
    "Yantra", "Tundja", "Bdin", "Zagorets", "Ludogorets",
    "Feniks", "Titan", "Vihor", "Grom", "Orel",
    "Panteri", "Pirati", "Gladiatori", "Komandosi", "Strelite",
    "Lavovi", "Blitz", "Uragan", "Gepardi", "Volk",
    "Sokol", "Yastrebi", "Orlovi", "Skorpioni", "Tigrove",
    "Buri", "Meteor", "Puls", "Zenit", "Meridian",
    "Stara Planina", "Rodopi", "Rila", "Vitosha", "Rhodope",
    "Izgrev", "Zalez", "Zvezda", "Kometa", "Galaktika",
    "United", "Athletic", "Sporting", "Dynamo", "Rapid",
    "Torpedo", "Arsenal", "Fortuna", "Victoria", "Gloria",
    "Olimpik", "Olimpia", "Trakia", "Makedonia", "Thrakia",
    "Strela", "Munja", "Vodopad", "Planinska", "Kraibrezhie"
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
    "Open", "Business"
];

const LEAGUE_NAME_SUFFIXES = [
    "League", "Division", "Championship", "Cup", "Series", "Tournament"
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
