const TEAM_NAME_PREFIXES = [
    "Iron", "Sun", "River", "Storm", "North", "South", "Golden", "Silver",
    "Stone", "Wolf", "Eagle", "Falcon", "Thunder", "Granite", "Maple",
    "Harbor", "Summit", "Ember", "Frost", "Crimson"
];

const TEAM_NAME_SUFFIXES = [
    "FC", "United", "City", "Athletic", "Rovers", "Wanderers", "Dynamo",
    "Sporting", "Albion", "Town", "Rangers", "Hotspur"
];

const PLAYER_FIRST_NAMES = [
    "Ivan", "Georgi", "Nikolai", "Stefan", "Martin", "Alex", "Daniel",
    "Viktor", "Petar", "Dimitar", "Lucas", "Mateo", "Diego", "Bruno",
    "Marco", "Luca", "Kevin", "Erik", "Omar", "Sami"
];

const PLAYER_LAST_NAMES = [
    "Ivanov", "Petrov", "Georgiev", "Dimitrov", "Stoyanov", "Kolev",
    "Silva", "Santos", "Rossi", "Bianchi", "Müller", "Schmidt",
    "Garcia", "Fernandez", "Novak", "Kowalski", "Andersson", "Olsen"
];

const LEAGUE_NAME_PREFIXES = [
    "Northern", "Southern", "Eastern", "Western", "Coastal", "Metro",
    "Regional", "National", "Premier", "Capital", "United", "Central"
];

const LEAGUE_NAME_SUFFIXES = [
    "League", "Division", "Championship", "Cup", "Series", "Conference"
];

const COUNTRIES = [
    "Bulgaria", "Italy", "Germany", "Spain", "France", "Portugal",
    "Netherlands", "Belgium", "Austria", "Switzerland", "Poland",
    "Croatia", "Serbia", "Greece", "Romania", "Czechia", "Sweden", "Norway"
];

const CITIES = [
    "Sofia", "Plovdiv", "Varna", "Burgas", "Ruse", "Madrid", "Milan",
    "Munich", "Lyon", "Porto", "Rotterdam", "Vienna", "Zurich", "Krakow",
    "Zagreb", "Belgrade", "Athens", "Bucharest", "Prague", "Gothenburg"
];

function randomFrom(list) {
    return list[Math.floor(Math.random() * list.length)];
}

function randomTeamName() {
    return randomFrom(TEAM_NAME_PREFIXES) + " " + randomFrom(TEAM_NAME_SUFFIXES);
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

function randomCountry() {
    return randomFrom(COUNTRIES);
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

function fillRandomCountry(inputId) {
    document.getElementById(inputId).value = randomCountry();
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
