var PAGE_LOAD_TIME = Date.now();

function elapsedMinutesNow(lm) {
    var baseSec = (lm.elapsedSec != null) ? lm.elapsedSec : lm.elapsedMin * 60;
    return (baseSec + (Date.now() - PAGE_LOAD_TIME) / 1000) / 60;
}

function formatClock(totalSec) {
    var s = Math.max(0, Math.floor(totalSec));
    var m = Math.floor(s / 60);
    var sec = s % 60;
    return m + ':' + (sec < 10 ? '0' : '') + sec;
}

function getLiveState(realMinFloat, goals) {
    if (realMinFloat < 0) return { phase: 'PRE', displayMin: '', homeScore: 0, awayScore: 0, maxMin: 0 };
    var elapsedSec = realMinFloat * 60;
    var phase, maxMin;
    if (elapsedSec < 1200)      { phase = 'FIRST';  maxMin = Math.floor(elapsedSec / 60) + 1; }
    else if (elapsedSec < 1500) { phase = 'HT';     maxMin = 20; }
    else if (elapsedSec < 2700) { phase = 'SECOND'; maxMin = Math.floor((elapsedSec - 1500) / 60) + 1; }
    else                        { phase = 'FT';     maxMin = 20; }
    var hs = 0, as = 0;
    for (var i = 0; i < goals.length; i++) {
        var g = goals[i];
        if (g.half === 'FIRST') {
            if ((phase === 'FIRST' && g.minute <= maxMin) ||
                    phase === 'HT' || phase === 'SECOND' || phase === 'FT') {
                hs = g.rh; as = g.ra;
            }
        } else if (g.half === 'SECOND') {
            var secMax = phase === 'SECOND' ? maxMin : (phase === 'FT' ? 20 : -1);
            if (secMax >= 0 && g.minute <= secMax) { hs = g.rh; as = g.ra; }
        }
    }
    var d;
    if (phase === 'FIRST')        d = formatClock(elapsedSec);
    else if (phase === 'HT')      d = 'HT';
    else if (phase === 'SECOND')  d = formatClock(1200 + (elapsedSec - 1500));
    else                          d = 'FT';
    return { phase: phase, displayMin: d, homeScore: hs, awayScore: as, maxMin: maxMin };
}

var FT_HIDE_DELAY_MS = 45000;

function scheduleFtRemoval(card) {
    if (!card || card.dataset.ftScheduled) return;
    card.dataset.ftScheduled = '1';
    setTimeout(function() {
        card.style.transition = 'opacity 0.6s ease';
        card.style.opacity = '0';
        setTimeout(function() { card.style.display = 'none'; }, 650);
    }, FT_HIDE_DELAY_MS);
}

function updateLiveMinutes() {
    var liveById = {};
    (window.LIVE_MATCHES || []).forEach(function(m) { liveById[m.id] = m; });
    document.querySelectorAll('[data-elapsed]').forEach(function(el) {
        var elapsed = parseInt(el.getAttribute('data-elapsed') || '0');
        var mid = el.getAttribute('data-match-id');
        var minEl = el.querySelector('.live-minute');
        if (!minEl) return;
        var lm = mid && liveById[mid];
        if (lm) {
            var realMinFloat = elapsedMinutesNow(lm);
            var state = getLiveState(realMinFloat, lm.goals);
            if (minEl.textContent !== state.displayMin) minEl.textContent = state.displayMin;
            var hsEl = el.querySelector('.ls-h');
            var asEl = el.querySelector('.ls-a');
            if (hsEl && hsEl.textContent != state.homeScore) hsEl.textContent = state.homeScore;
            if (asEl && asEl.textContent != state.awayScore) asEl.textContent = state.awayScore;

            var details = el.closest('details');
            if (details) {
                if (state.phase === 'FT') scheduleFtRemoval(details);
                var anyFirstVis = false, anySecondVis = false;
                details.querySelectorAll('.timeline-row').forEach(function(r) {
                    var rHalf = r.getAttribute('data-half');
                    var rMin  = parseInt(r.getAttribute('data-minute') || '0');
                    var show;
                    if      (state.phase === 'FT')     show = true;
                    else if (state.phase === 'HT')     show = rHalf === 'FIRST';
                    else if (state.phase === 'SECOND') show = rHalf === 'FIRST' || (rHalf === 'SECOND' && rMin <= state.maxMin);
                    else                               show = rHalf === 'FIRST' && rMin <= state.maxMin;
                    r.style.display = show ? '' : 'none';
                    if (show && rHalf === 'FIRST')  anyFirstVis  = true;
                    if (show && rHalf === 'SECOND') anySecondVis = true;
                });
                details.querySelectorAll('.half-label').forEach(function(hl) {
                    var halfName = hl.getAttribute('data-half');
                    hl.style.display = (halfName === 'FIRST' ? anyFirstVis : anySecondVis) ? '' : 'none';
                    var scoreSpan = hl.querySelector('.half-score');
                    if (scoreSpan) {
                        var halfDone = (halfName === 'FIRST' && (state.phase === 'HT' || state.phase === 'SECOND' || state.phase === 'FT')) ||
                                       (halfName === 'SECOND' && state.phase === 'FT');
                        scoreSpan.style.display = halfDone ? '' : 'none';
                    }
                });
                var inProgress = details.querySelector('.match-in-progress');
                if (inProgress) {
                    var anyVisible = anyFirstVis || anySecondVis;
                    inProgress.style.display = anyVisible ? 'none' : '';
                }
            }
        } else {
            var elapsedSec = elapsed * 60 + (Date.now() - PAGE_LOAD_TIME) / 1000;
            var d;
            if (elapsedSec < 1200)      d = formatClock(elapsedSec);
            else if (elapsedSec < 1500) d = 'HT';
            else if (elapsedSec < 2700) d = formatClock(1200 + (elapsedSec - 1500));
            else                        d = 'FT';
            minEl.textContent = d;
        }
    });
}

function setupMatchPager(listId, pagerId, pageSize) {
    var list = document.getElementById(listId);
    var pager = document.getElementById(pagerId);
    if (!list || !pager) return;

    var items = Array.from(list.children).filter(function(el) {
        return el.classList && el.classList.contains('match-card');
    });
    if (items.length <= pageSize) {
        pager.style.display = 'none';
        return;
    }

    var totalPages = Math.ceil(items.length / pageSize);
    var current = 0;

    function render() {
        items.forEach(function(el, i) {
            var onPage = i >= current * pageSize && i < (current + 1) * pageSize;
            el.style.display = onPage ? '' : 'none';
        });

        pager.innerHTML = '';

        var prev = document.createElement(current > 0 ? 'button' : 'span');
        prev.className = 'notif-page-btn' + (current > 0 ? '' : ' disabled');
        prev.textContent = '‹ Newer';
        if (current > 0) {
            prev.type = 'button';
            prev.addEventListener('click', function() { current--; render(); });
        }

        var info = document.createElement('span');
        info.className = 'notif-page-info';
        info.textContent = 'Page ' + (current + 1) + ' of ' + totalPages + ' · ' + items.length + ' total';

        var next = document.createElement(current + 1 < totalPages ? 'button' : 'span');
        next.className = 'notif-page-btn' + (current + 1 < totalPages ? '' : ' disabled');
        next.textContent = 'Older ›';
        if (current + 1 < totalPages) {
            next.type = 'button';
            next.addEventListener('click', function() { current++; render(); });
        }

        pager.appendChild(prev);
        pager.appendChild(info);
        pager.appendChild(next);
    }

    render();
}

updateLiveMinutes();
setInterval(updateLiveMinutes, 1000);
