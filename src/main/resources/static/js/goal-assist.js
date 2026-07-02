// Keeps the "Assist" dropdown in sync with the selected scorer:
// only players from the scorer's team can be chosen as assistant.
(function () {
    var scorer = document.getElementById('scorerId');
    var assist = document.getElementById('assistantId');
    if (!scorer || !assist) {
        return;
    }

    function selectedTeam(select) {
        var opt = select.options[select.selectedIndex];
        var team = opt ? opt.getAttribute('data-team') : null;
        return team || '';
    }

    function refresh() {
        var team = selectedTeam(scorer);

        for (var i = 0; i < assist.options.length; i++) {
            var opt = assist.options[i];
            var optTeam = opt.getAttribute('data-team');
            if (optTeam === null) {
                continue; // the "-- no assist --" placeholder stays available
            }
            var visible = team !== '' && optTeam === team;
            opt.hidden = !visible;
            opt.disabled = !visible;
        }

        var groups = assist.getElementsByTagName('optgroup');
        for (var g = 0; g < groups.length; g++) {
            var match = groups[g].getAttribute('data-team') === team;
            groups[g].hidden = !match;
            groups[g].disabled = !match;
        }

        var current = assist.options[assist.selectedIndex];
        if (current && current.disabled) {
            assist.value = '';
        }
    }

    scorer.addEventListener('change', refresh);
    refresh();
})();
