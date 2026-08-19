/**
 * Standalone Zero-Dependency Verification Suite for In-Page Agent Runtime
 * Runs directly on standard Node.js without external npm dependencies.
 */

const fs = require('fs');
const path = require('path');

// Simple DOM environment simulation
class MockNode {
    constructor(tagName, id = '', type = '', role = '') {
        this.nodeType = 1; // ELEMENT_NODE
        this.tagName = tagName.toUpperCase();
        this.id = id;
        this.type = type;
        this.role = role;
        this.attributes = {};
        this.children = [];
        this.parentNode = null;
        this.style = { display: 'block', visibility: 'visible', opacity: '1', cursor: 'default' };
        this.innerText = '';
        this.textContent = '';
        this.value = '';
        this.checked = false;
        this.disabled = false;
        this.eventListeners = {};

        if (id) this.attributes['id'] = id;
        if (type) this.attributes['type'] = type;
        if (role) this.attributes['role'] = role;
    }

    getAttribute(name) {
        return this.attributes[name] || null;
    }

    setAttribute(name, val) {
        this.attributes[name] = String(val);
        if (name === 'id') this.id = String(val);
    }

    hasAttribute(name) {
        return name in this.attributes;
    }

    removeAttribute(name) {
        delete this.attributes[name];
    }

    appendChild(child) {
        child.parentNode = this;
        this.children.push(child);
        return child;
    }

    removeChild(child) {
        const idx = this.children.indexOf(child);
        if (idx !== -1) {
            this.children.splice(idx, 1);
            child.parentNode = null;
        }
        return child;
    }

    get innerHTML() {
        return '';
    }

    set innerHTML(val) {
        this.children = [];
    }

    addEventListener(event, handler) {
        if (!this.eventListeners[event]) this.eventListeners[event] = [];
        this.eventListeners[event].push(handler);
    }

    dispatchEvent(evt) {
        evt.target = this;
        evt.currentTarget = this;
        if (this.eventListeners[evt.type]) {
            for (const h of this.eventListeners[evt.type]) {
                h(evt);
            }
        }
        return true;
    }

    getBoundingClientRect() {
        if (this.style.display === 'none' || this.style.visibility === 'hidden') {
            return { top: 0, left: 0, width: 0, height: 0, bottom: 0, right: 0 };
        }
        return { top: 50, left: 20, width: 100, height: 35, bottom: 85, right: 120 };
    }

    scrollIntoView() {}
    focus() {}
    click() {
        this.dispatchEvent(new MockEvent('click', { bubbles: true }));
    }

    closest(selector) {
        return null;
    }
}

class MockEvent {
    constructor(type, init = {}) {
        this.type = type;
        this.bubbles = init.bubbles || false;
        this.cancelable = init.cancelable || false;
        this.target = null;
        this.currentTarget = null;
        Object.assign(this, init);
    }
}

function findNodeById(root, id) {
    if (root.id === id || root.getAttribute('id') === id) return root;
    for (const c of root.children) {
        const found = findNodeById(c, id);
        if (found) return found;
    }
    return null;
}

function setupMockWindow() {
    const root = new MockNode('HTML');
    const head = new MockNode('HEAD');
    const body = new MockNode('BODY');
    root.appendChild(head);
    root.appendChild(body);

    const searchInput = new MockNode('INPUT', 'searchInput', 'text');
    searchInput.setAttribute('placeholder', 'Search Wikipedia');
    searchInput.setAttribute('aria-label', 'Search Wikipedia');

    const searchButton = new MockNode('BUTTON', 'searchButton', 'submit');
    searchButton.innerText = 'Search';

    const featuredLink = new MockNode('A', 'featuredLink');
    featuredLink.innerText = 'Featured Article: Chromium';
    featuredLink.setAttribute('href', 'https://en.wikipedia.org/wiki/Chromium_(web_browser)');

    const hiddenButton = new MockNode('BUTTON', 'hiddenButton');
    hiddenButton.style.display = 'none';

    body.appendChild(searchInput);
    body.appendChild(searchButton);
    body.appendChild(featuredLink);
    body.appendChild(hiddenButton);

    const mockWindow = {
        Node: { ELEMENT_NODE: 1 },
        document: {
            title: 'Wikipedia, the free encyclopedia',
            documentElement: root,
            body: body,
            getElementById: (id) => findNodeById(root, id),
            createElement: (tag) => new MockNode(tag),
            querySelectorAll: (sel) => {
                const results = [];
                function collect(node) {
                    results.push(node);
                    for (const c of node.children) collect(c);
                }
                collect(body);
                return results;
            },
            querySelector: (sel) => {
                if (sel.startsWith('#')) return findNodeById(root, sel.substring(1));
                if (sel.includes('data-agent-id="')) {
                    const match = sel.match(/data-agent-id="(\d+)"/);
                    if (match) {
                        const all = mockWindow.document.querySelectorAll('*');
                        return all.find(e => e.getAttribute('data-agent-id') === match[1]) || null;
                    }
                }
                return null;
            }
        },
        innerWidth: 412,
        innerHeight: 915,
        scrollX: 0,
        scrollY: 0,
        scrollBy: (opts) => {},
        location: { href: 'https://en.wikipedia.org' },
        getComputedStyle: (el) => el.style,
        Touch: function(init) { Object.assign(this, init); },
        TouchEvent: function(type, init) { return new MockEvent(type, init); },
        PointerEvent: function(type, init) { return new MockEvent(type, init); },
        MouseEvent: function(type, init) { return new MockEvent(type, init); },
        InputEvent: function(type, init) { return new MockEvent(type, init); },
        KeyboardEvent: function(type, init) { return new MockEvent(type, init); },
        Event: function(type, init) { return new MockEvent(type, init); },
        CustomEvent: function(type, init) { return new MockEvent(type, init); },
        MutationObserver: function(callback) {
            this.observe = () => {};
            this.disconnect = () => {};
        },
        HTMLInputElement: { prototype: { value: '' } },
        HTMLTextAreaElement: { prototype: { value: '' } },
        eval: (code) => eval(code)
    };

    return { mockWindow, searchInput, searchButton, featuredLink, hiddenButton };
}

async function runVerification() {
    console.log('=== RUNNING CHROME MOBILE AGENT VERIFICATION ===\n');

    const agentRuntimeJs = fs.readFileSync(path.join(__dirname, '../app/src/main/assets/agent_runtime.js'), 'utf8');
    const { mockWindow, searchInput, searchButton, featuredLink, hiddenButton } = setupMockWindow();

    // Attach globals to global context for runtime evaluation
    global.window = mockWindow;
    global.document = mockWindow.document;
    global.Node = mockWindow.Node;
    global.Touch = mockWindow.Touch;
    global.TouchEvent = mockWindow.TouchEvent;
    global.PointerEvent = mockWindow.PointerEvent;
    global.MouseEvent = mockWindow.MouseEvent;
    global.InputEvent = mockWindow.InputEvent;
    global.KeyboardEvent = mockWindow.KeyboardEvent;
    global.Event = mockWindow.Event;
    global.CustomEvent = mockWindow.CustomEvent;
    global.MutationObserver = mockWindow.MutationObserver;

    // Evaluate Agent Runtime in simulated window context
    const runtimeFn = new Function('window', 'document', 'Node', agentRuntimeJs);
    runtimeFn(mockWindow, mockWindow.document, mockWindow.Node);

    if (!mockWindow.__mobileAgent) {
        throw new Error('FAILED: mockWindow.__mobileAgent was not initialized');
    }
    console.log('✓ In-page Agent Runtime initialized successfully');

    // TEST 1: DOM Snapshot Extraction
    console.log('\n--- TEST 1: DOM & Accessibility Snapshot Extraction ---');
    const snapshot = mockWindow.__mobileAgent.getDOMSnapshot({ viewportOnly: false });
    console.log(`Extracted ${snapshot.count} elements. Page Title: "${snapshot.title}"`);
    console.log('Formatted Tree Text Output:\n' + snapshot.treeText.trim());

    if (snapshot.count !== 3) {
        throw new Error(`FAILED: Expected 3 visible interactive elements (hidden filtered out), got ${snapshot.count}`);
    }

    const foundSearchInput = snapshot.elements.find(e => e.tag === 'input');
    const foundSearchButton = snapshot.elements.find(e => e.tag === 'button');
    const foundFeaturedLink = snapshot.elements.find(e => e.tag === 'a');

    if (!foundSearchInput || !foundSearchButton || !foundFeaturedLink) {
        throw new Error('FAILED: Missing core interactive elements in DOM snapshot');
    }
    console.log(`✓ Search Input identified with ID #${foundSearchInput.id}`);
    console.log(`✓ Search Button identified with ID #${foundSearchButton.id}`);
    console.log(`✓ Featured Link identified with ID #${foundFeaturedLink.id}`);

    // TEST 2: Visual Badges
    console.log('\n--- TEST 2: Visual Element Badges ---');
    mockWindow.__mobileAgent.showElementBadges();
    const badgeContainer = mockWindow.document.getElementById('__mobile_agent_badge_container__');
    if (!badgeContainer || badgeContainer.children.length === 0) {
        throw new Error('FAILED: Visual badge container is empty');
    }
    console.log(`✓ Generated ${badgeContainer.children.length} visual badges on page`);

    mockWindow.__mobileAgent.clearElementBadges();
    if (badgeContainer.children.length !== 0) {
        throw new Error('FAILED: Clear badges did not remove elements');
    }
    console.log('✓ Cleared visual badges successfully');

    // TEST 3: Mobile Touch & Click Pipeline
    console.log('\n--- TEST 3: Mobile Synthetic Touch & Click Pipeline ---');
    const eventLog = [];
    searchButton.addEventListener('touchstart', () => eventLog.push('touchstart'));
    searchButton.addEventListener('touchend', () => eventLog.push('touchend'));
    searchButton.addEventListener('click', () => eventLog.push('click'));

    const clickResult = mockWindow.__mobileAgent.interact('click', { id: foundSearchButton.id });
    console.log('Interaction Result:', clickResult);
    console.log('Dispatched Event Log:', eventLog);

    if (!clickResult.success) {
        throw new Error(`FAILED: Click interaction returned false: ${clickResult.error}`);
    }
    if (!eventLog.includes('touchstart') || !eventLog.includes('touchend') || !eventLog.includes('click')) {
        throw new Error('FAILED: Mobile event pipeline did not fire touchstart/touchend/click sequence');
    }
    console.log('✓ Full mobile touch & click event pipeline verified');

    // TEST 4: Typing & Input Emulation
    console.log('\n--- TEST 4: Typing & Input Event Emulation ---');
    const typeResult = mockWindow.__mobileAgent.interact('type', {
        id: foundSearchInput.id,
        text: 'Chromium Mobile Browser',
        clearFirst: true,
        pressEnter: true
    });
    console.log('Type Result:', typeResult);

    if (searchInput.value !== 'Chromium Mobile Browser') {
        throw new Error(`FAILED: Input value mismatch: expected 'Chromium Mobile Browser', got '${searchInput.value}'`);
    }
    console.log(`✓ Input value successfully updated to: "${searchInput.value}"`);

    // TEST 5: Console Command Execution
    console.log('\n--- TEST 5: Console Command Execution ---');
    const consoleResult = mockWindow.__mobileAgent.executeConsole('document.title = "Updated Title"; 40 + 2');
    console.log('Console Result:', consoleResult);

    if (!consoleResult.success || consoleResult.result !== '42') {
        throw new Error(`FAILED: Console execution failed: ${JSON.stringify(consoleResult)}`);
    }
    if (mockWindow.document.title !== 'Updated Title') {
        throw new Error('FAILED: DOM state was not mutated by console command');
    }
    console.log('✓ Console script execution verified (Result: 42, Title: "Updated Title")');

    // TEST 6: SPA Stability & Mutation Observer Waiter
    console.log('\n--- TEST 6: SPA Stability & Mutation Observer ---');
    const stabilityResult = await mockWindow.__mobileAgent.waitForStableDOM(500, 100);
    console.log('Stability Result:', stabilityResult);
    if (!stabilityResult.stable) {
        throw new Error('FAILED: Stability waiter failed');
    }
    console.log('✓ MutationObserver stability waiter verified');

    console.log('\n========================================');
    console.log('ALL VERIFICATION TESTS PASSED SUCCESSFULLY! ✓');
    console.log('========================================\n');
}

runVerification().catch(err => {
    console.error('\n❌ VERIFICATION ERROR:', err);
    process.exit(1);
});
