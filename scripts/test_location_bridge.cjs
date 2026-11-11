// Node's built-in runner; device coordinates and browser permissions are simulated.
const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const script = fs.readFileSync(path.join(__dirname, '../src/main/resources/location/location.js'), 'utf8');

function page({token = 'A'.repeat(43), supported = true} = {}) {
    const elements = Object.fromEntries(['start', 'stop', 'status', 'accuracy'].map(id => [id, {
        textContent: '', disabled: id === 'stop', events: {},
        addEventListener(name, callback) { this.events[name] = callback; }
    }]));
    const state = {elements, requests: [], cleared: [], windowEvents: {}, failSession: false};
    const geolocation = {
        watchPosition(success, failure, options) { Object.assign(state, {success, failure, options}); return 7; },
        getCurrentPosition(success, failure, options) { Object.assign(state, {refreshSuccess: success, refreshOptions: options}); },
        clearWatch(id) { state.cleared.push(id); }
    };
    vm.runInNewContext(script, {
        window: {location: {hash: '#' + token, pathname: '/'}, history: {replaceState() {}},
            addEventListener(name, callback) { state.windowEvents[name] = callback; }},
        document: {getElementById: id => elements[id]}, navigator: supported ? {geolocation} : {},
        URLSearchParams, Math,
        fetch: async (url, options) => {
            state.requests.push({url, options});
            return {ok: !(state.failSession && url === '/session')};
        },
        setInterval(callback, delay) { state[delay === 3000 ? 'heartbeat' : 'recheck'] = callback; return delay; },
        clearInterval() { state.heartbeatCleared = true; }
    });
    return state;
}

const flush = () => new Promise(resolve => setImmediate(resolve));

test('location starts only after an explicit click and sends accuracy and observation time', async () => {
    const state = page();
    assert.equal(state.success, undefined);
    assert.equal(state.requests.length, 0);
    state.elements.start.events.click();
    assert.equal(state.options.maximumAge, 0);
    assert.equal(state.options.enableHighAccuracy, true);
    state.success({coords: {latitude: 43.65, longitude: -79.38, accuracy: 18}, timestamp: 123456});
    await flush();
    const request = state.requests.find(request => request.url === '/position');
    assert.equal(request.options.body.get('accuracy'), '18');
    assert.equal(request.options.body.get('timestamp'), '123456');
    assert.equal(request.options.headers['X-AccessGraph-Token'], 'A'.repeat(43));
    assert.match(state.elements.status.textContent, /Sharing live location/);
});

test('permission denial stops the watch and never fabricates a position', async () => {
    const state = page();
    state.elements.start.events.click();
    state.failure({code: 1});
    await flush();
    assert.match(state.elements.status.textContent, /denied/);
    assert.deepEqual(state.cleared, [7]);
    assert.equal(state.elements.stop.disabled, true);
    assert.equal(state.requests.some(request => request.url === '/position'), false);
    assert.equal(state.requests.find(request => request.url === '/status').options.body.get('code'), 'PERMISSION_DENIED');
});

test('stop sharing clears the watch, heartbeat, and later position callbacks', async () => {
    const state = page();
    state.elements.start.events.click();
    state.elements.stop.events.click();
    state.success({coords: {latitude: 43, longitude: -79, accuracy: 20}, timestamp: 123});
    await flush();
    assert.deepEqual(state.cleared, [7]);
    assert.equal(state.heartbeatCleared, true);
    assert.equal(state.requests.some(request => request.url === '/position'), false);
    assert.match(state.elements.status.textContent, /sharing stopped/);
});

test('closing the helper page notifies Swing and clears the device watcher', async () => {
    const state = page();
    state.elements.start.events.click();
    state.windowEvents.pagehide();
    await flush();
    assert.equal(state.requests.find(request => request.url === '/status').options.keepalive, true);
    assert.deepEqual(state.cleared, [7]);
});

test('ending the desktop session stops browser sharing on the next heartbeat', async () => {
    const state = page();
    state.elements.start.events.click();
    state.failSession = true;
    state.heartbeat();
    await flush();
    assert.deepEqual(state.cleared, [7]);
    assert.match(state.elements.status.textContent, /session ended/);
});

test('invalid sessions and unsupported geolocation give actionable errors', async () => {
    const invalid = page({token: ''});
    assert.equal(invalid.elements.start.disabled, true);
    assert.match(invalid.elements.status.textContent, /Use my location/);
    const unsupported = page({supported: false});
    unsupported.elements.start.events.click();
    await flush();
    assert.match(unsupported.elements.status.textContent, /does not support/);
    assert.equal(unsupported.requests[0].options.body.get('code'), 'UNAVAILABLE');
});

test('stationary devices request a fresh observation rather than reusing an old timestamp', async () => {
    const state = page();
    state.recheck();
    assert.equal(state.refreshSuccess, undefined, 'No observations may be requested before consent.');
    state.elements.start.events.click();
    state.recheck();
    assert.equal(state.refreshOptions.maximumAge, 0);
    state.refreshSuccess({coords: {latitude: 43.65, longitude: -79.38, accuracy: 12}, timestamp: 234567});
    await flush();
    assert.equal(state.requests.find(request => request.url === '/position').options.body.get('timestamp'), '234567');
});
