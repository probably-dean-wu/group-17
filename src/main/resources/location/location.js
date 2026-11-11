(() => {
    "use strict";
    const token = window.location.hash.slice(1);
    window.history.replaceState(null, "", window.location.pathname);
    const start = document.getElementById("start");
    const stop = document.getElementById("stop");
    const status = document.getElementById("status");
    const accuracy = document.getElementById("accuracy");
    let watch = null;
    let active = false;
    let finished = false;
    let pending = null;
    let sending = false;

    async function post(path, values, keepalive = false) {
        const response = await fetch(path, {
            method: "POST", credentials: "same-origin", cache: "no-store", keepalive,
            headers: {"Content-Type": "application/x-www-form-urlencoded", "X-AccessGraph-Token": token},
            body: new URLSearchParams(values)
        });
        if (!response.ok) throw new Error("Location session unavailable");
    }

    function end(message) {
        active = false;
        finished = true;
        pending = null;
        if (watch !== null) navigator.geolocation.clearWatch(watch);
        watch = null;
        start.disabled = true;
        stop.disabled = true;
        status.textContent = message;
        accuracy.textContent = "";
        clearInterval(heartbeat);
        clearInterval(recheck);
    }

    async function sendLatest() {
        if (sending || !active) return;
        sending = true;
        try {
            while (pending && active) {
                const position = pending;
                pending = null;
                await post("/position", position);
                if (active) status.textContent = "Sharing live location. Return to AccessGraph to see your map.";
            }
        } catch (_) {
            end("AccessGraph's location session ended. Return to the app and choose Use my location again.");
        } finally {
            sending = false;
        }
    }

    function receivePosition(position) {
        if (!active) return;
        pending = {latitude: position.coords.latitude, longitude: position.coords.longitude,
            accuracy: position.coords.accuracy, timestamp: Math.round(position.timestamp)};
        accuracy.textContent = "Reported accuracy: ±" + Math.round(position.coords.accuracy) + " metres.";
        sendLatest();
    }

    function receiveError(error) {
        if (!active) return;
        const code = error.code === 1 ? "PERMISSION_DENIED" : error.code === 3 ? "TIMEOUT" : "UNAVAILABLE";
        post("/status", {code}).catch(() => {});
        accuracy.textContent = "";
        if (code === "PERMISSION_DENIED") {
            end("Location permission was denied. You can allow it in your browser settings and restart from AccessGraph.");
        } else {
            status.textContent = "A fresh location is unavailable. Waiting for your device to report a position…";
        }
    }

    const options = {enableHighAccuracy: true, maximumAge: 0, timeout: 15000};

    start.addEventListener("click", () => {
        if (active || finished) return;
        if (!navigator.geolocation) {
            post("/status", {code: "UNAVAILABLE"}).catch(() => {});
            end("This browser does not support device location. You can still enter a destination in AccessGraph.");
            return;
        }
        active = true;
        start.disabled = true;
        stop.disabled = false;
        status.textContent = "Waiting for browser permission and a device location…";
        try {
            watch = navigator.geolocation.watchPosition(receivePosition, receiveError, options);
        } catch (_) {
            post("/status", {code: "UNAVAILABLE"}).catch(() => {});
            end("Your browser blocked device location. Check its location settings and restart from AccessGraph.");
        }
    });

    stop.addEventListener("click", () => {
        post("/status", {code: "STOPPED"}).catch(() => {});
        end("Location sharing stopped. You can close this page.");
    });

    window.addEventListener("pagehide", () => {
        if (active) {
            post("/status", {code: "STOPPED"}, true).catch(() => {});
            end("Location sharing stopped.");
        }
    });

    const heartbeat = setInterval(() => {
        if (!finished) post("/session", {}).catch(() => {
            end("AccessGraph's location session ended. You can close this page.");
        });
    }, 3000);

    // A stationary device may not trigger watchPosition; request fresh observations without inventing a timestamp.
    const recheck = setInterval(() => {
        if (active) navigator.geolocation.getCurrentPosition(receivePosition, receiveError, options);
    }, 30000);

    if (!/^[A-Za-z0-9_-]{43}$/.test(token)) {
        end("Open this permission page using Use my location in the AccessGraph desktop app.");
    }
})();
