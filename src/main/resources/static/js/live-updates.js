(function () {
    var live = window.KickoffLive = window.KickoffLive || {connected: false, handlers: []};

    live.subscribe = function (fn) {
        live.handlers.push(fn);
    };

    live.emit = function () {
        live.handlers.forEach(function (fn) {
            try {
                fn();
            } catch (e) {
            }
        });
    };

    live.poll = function (fn, fastMs, slowMs) {
        var last = 0;

        function run() {
            last = Date.now();
            fn();
        }

        run();
        live.subscribe(run);
        setInterval(function () {
            var due = live.connected ? slowMs : fastMs;
            if (Date.now() - last >= due) {
                run();
            }
        }, 1000);
    };
}());
