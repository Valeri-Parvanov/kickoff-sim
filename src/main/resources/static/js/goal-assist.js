(function () {
    var scorer = document.getElementById('scorerId');
    var assist = document.getElementById('assistantId');
    var ownGoalCb = document.getElementById('ownGoal');
    var penaltyCb = document.getElementById('penalty');
    var assistWrap = document.getElementById('assistWrap');
    if (!scorer || !assist) {
        return;
    }

    function selectedTeam(select) {
        var opt = select.options[select.selectedIndex];
        var team = opt ? opt.getAttribute('data-team') : null;
        return team || '';
    }

    function refresh() {
        var isOwnGoal = ownGoalCb && ownGoalCb.checked;
        var isPenalty = penaltyCb && penaltyCb.checked;
        if (isOwnGoal || isPenalty) {
            if (assistWrap) assistWrap.style.display = 'none';
            assist.value = '';
            return;
        }
        if (assistWrap) assistWrap.style.display = '';

        var team = selectedTeam(scorer);
        for (var i = 0; i < assist.options.length; i++) {
            var opt = assist.options[i];
            var optTeam = opt.getAttribute('data-team');
            if (optTeam === null) {
                continue;
            }
            var visible = team !== '' && optTeam === team && opt.value !== scorer.value;
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
    if (ownGoalCb) ownGoalCb.addEventListener('change', refresh);
    if (penaltyCb) penaltyCb.addEventListener('change', refresh);
    refresh();
})();
