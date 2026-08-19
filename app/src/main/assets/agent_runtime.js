/**
 * Mobile Chromium AI Agent Runtime
 * In-page JavaScript agent bundle providing DOM tree extraction, accessibility labeling,
 * visual badge overlays, synthetic touch/keyboard interactions, console execution, and SPA stability tracking.
 */
(function () {
    if (window.__mobileAgent) {
        console.log("[MobileAgent] Already initialized on", window.location.href);
        return;
    }

    console.log("[MobileAgent] Initializing agent runtime on:", window.location.href);

    const OVERLAY_CONTAINER_ID = '__mobile_agent_badge_container__';
    let elementIndexCounter = 0;
    const elementMap = new Map(); // id -> HTMLElement

    const INTERACTIVE_ROLES = new Set([
        'button', 'link', 'checkbox', 'radio', 'tab', 'menuitem',
        'combobox', 'searchbox', 'switch', 'textbox', 'option',
        'menuitemcheckbox', 'menuitemradio', 'treeitem'
    ]);

    const IGNORED_TAGS = new Set([
        'SCRIPT', 'STYLE', 'NOSCRIPT', 'SVG', 'PATH', 'META',
        'LINK', 'HEAD', 'TITLE', 'TEMPLATE', 'BR', 'WBR'
    ]);

    /**
     * Check if an element is visible in the page / viewport
     */
    function isElementVisible(el, computedStyle) {
        if (!el || el.nodeType !== Node.ELEMENT_NODE) return false;
        
        const style = computedStyle || window.getComputedStyle(el);
        if (style.display === 'none' || style.visibility === 'hidden' || parseFloat(style.opacity) < 0.05) {
            return false;
        }

        if (el.hasAttribute('aria-hidden') && el.getAttribute('aria-hidden') === 'true') {
            return false;
        }

        const rect = el.getBoundingClientRect();
        if (rect.width <= 0 || rect.height <= 0) {
            return false;
        }

        return true;
    }

    /**
     * Determine if an element is interactive for a user on mobile
     */
    function isInteractive(el, computedStyle) {
        const tag = el.tagName.toUpperCase();

        if (['A', 'BUTTON', 'INPUT', 'SELECT', 'TEXTAREA', 'DETAILS', 'SUMMARY'].includes(tag)) {
            return true;
        }

        const role = (el.getAttribute('role') || '').toLowerCase();
        if (INTERACTIVE_ROLES.has(role)) {
            return true;
        }

        if (el.hasAttribute('onclick') || el.hasAttribute('data-action') || el.hasAttribute('ng-click') || el.hasAttribute('v-on:click')) {
            return true;
        }

        const tabIndex = el.getAttribute('tabindex');
        if (tabIndex !== null && parseInt(tabIndex, 10) >= 0) {
            return true;
        }

        if (el.isContentEditable) {
            return true;
        }

        const style = computedStyle || window.getComputedStyle(el);
        if (style.cursor === 'pointer') {
            return true;
        }

        return false;
    }

    /**
     * Compute accessible text name for an element
     */
    function getAccessibleName(el) {
        // 1. aria-label
        const ariaLabel = el.getAttribute('aria-label');
        if (ariaLabel && ariaLabel.trim()) return ariaLabel.trim();

        // 2. aria-labelledby
        const ariaLabelledBy = el.getAttribute('aria-labelledby');
        if (ariaLabelledBy) {
            const labelledEl = document.getElementById(ariaLabelledBy);
            if (labelledEl && labelledEl.textContent.trim()) {
                return labelledEl.textContent.trim();
            }
        }

        // 3. title or placeholder
        const title = el.getAttribute('title');
        if (title && title.trim()) return title.trim();

        const placeholder = el.getAttribute('placeholder');
        if (placeholder && placeholder.trim()) return placeholder.trim();

        // 4. alt (images)
        const alt = el.getAttribute('alt');
        if (alt && alt.trim()) return alt.trim();

        // 5. Text content for buttons, links, labels
        if (['BUTTON', 'A', 'LABEL', 'SUMMARY', 'SPAN', 'P', 'H1', 'H2', 'H3', 'H4', 'H5', 'H6'].includes(el.tagName)) {
            const text = el.innerText || el.textContent || '';
            if (text.trim()) {
                return text.trim().replace(/\s+/g, ' ').slice(0, 120);
            }
        }

        // 6. Input value or select text
        if (el.tagName === 'INPUT' && (el.type === 'submit' || el.type === 'button' || el.type === 'reset')) {
            return el.value || el.placeholder || '';
        }

        return '';
    }

    /**
     * Get or create container for visual badges
     */
    function getOrCreateOverlayContainer() {
        let container = document.getElementById(OVERLAY_CONTAINER_ID);
        if (!container) {
            container = document.createElement('div');
            container.id = OVERLAY_CONTAINER_ID;
            container.style.cssText = `
                position: fixed;
                top: 0;
                left: 0;
                width: 100vw;
                height: 100vh;
                pointer-events: none;
                z-index: 2147483647;
                overflow: hidden;
            `;
            document.documentElement.appendChild(container);
        }
        return container;
    }

    const MobileAgent = {
        /**
         * Extract semantic DOM snapshot and interactive element registry
         */
        getDOMSnapshot: function (options) {
            options = options || {};
            const viewportOnly = options.viewportOnly !== false;
            
            elementIndexCounter = 0;
            elementMap.clear();

            const snapshotElements = [];
            const viewportWidth = window.innerWidth || document.documentElement.clientWidth;
            const viewportHeight = window.innerHeight || document.documentElement.clientHeight;

            const allElements = document.querySelectorAll('*');

            for (let i = 0; i < allElements.length; i++) {
                const el = allElements[i];
                if (IGNORED_TAGS.has(el.tagName)) continue;
                if (el.id === OVERLAY_CONTAINER_ID || el.closest('#' + OVERLAY_CONTAINER_ID)) continue;

                const style = window.getComputedStyle(el);
                if (!isElementVisible(el, style)) continue;

                const isElInteractive = isInteractive(el, style);
                const isHeadingOrText = ['H1', 'H2', 'H3', 'H4', 'P', 'LI'].includes(el.tagName) && el.children.length === 0;

                if (!isElInteractive && !isHeadingOrText) continue;

                const rect = el.getBoundingClientRect();

                // Viewport check
                if (viewportOnly) {
                    if (rect.bottom < 0 || rect.top > viewportHeight || rect.right < 0 || rect.left > viewportWidth) {
                        continue;
                    }
                }

                elementIndexCounter++;
                const id = elementIndexCounter;
                el.setAttribute('data-agent-id', id.toString());
                elementMap.set(id, el);

                const accessibleName = getAccessibleName(el);
                const tag = el.tagName.toLowerCase();
                const role = el.getAttribute('role') || '';
                const type = el.getAttribute('type') || '';
                const isInput = tag === 'input' || tag === 'textarea' || tag === 'select' || el.isContentEditable;

                snapshotElements.push({
                    id: id,
                    tag: tag,
                    role: role,
                    type: type,
                    name: accessibleName,
                    value: isInput && el.value !== undefined ? el.value : '',
                    placeholder: el.getAttribute('placeholder') || '',
                    checked: el.checked || false,
                    disabled: el.disabled || false,
                    isClickable: isElInteractive && !isInput,
                    isInput: isInput,
                    rect: {
                        x: Math.round(rect.left),
                        y: Math.round(rect.top),
                        width: Math.round(rect.width),
                        height: Math.round(rect.height),
                        top: Math.round(rect.top),
                        left: Math.round(rect.left)
                    }
                });
            }

            console.log(`[MobileAgent] Extracted DOM snapshot with ${snapshotElements.length} elements`);

            // Create compressed markdown-like text representation for LLM context
            let textRepresentation = `Page Title: "${document.title}"\nURL: ${window.location.href}\nInteractive & Visible Elements:\n`;
            for (const item of snapshotElements) {
                let descriptor = `[${item.id}] <${item.tag}`;
                if (item.type) descriptor += ` type="${item.type}"`;
                if (item.role) descriptor += ` role="${item.role}"`;
                descriptor += `>`;

                if (item.name) descriptor += ` "${item.name}"`;
                if (item.value) descriptor += ` value="${item.value}"`;
                if (item.placeholder) descriptor += ` placeholder="${item.placeholder}"`;
                if (item.checked) descriptor += ` [checked]`;
                if (item.disabled) descriptor += ` [disabled]`;

                descriptor += ` (pos: ${item.rect.x},${item.rect.y} ${item.rect.width}x${item.rect.height})`;
                textRepresentation += descriptor + '\n';
            }

            return {
                title: document.title,
                url: window.location.href,
                viewport: { width: viewportWidth, height: viewportHeight },
                scroll: { x: window.scrollX, y: window.scrollY },
                count: snapshotElements.length,
                elements: snapshotElements,
                treeText: textRepresentation
            };
        },

        /**
         * Render visual badge markers over interactive elements
         */
        showElementBadges: function () {
            const container = getOrCreateOverlayContainer();
            container.innerHTML = '';

            elementMap.forEach((el, id) => {
                const rect = el.getBoundingClientRect();
                if (rect.width <= 0 || rect.height <= 0) return;

                const badge = document.createElement('div');
                badge.innerText = id.toString();
                badge.style.cssText = `
                    position: absolute;
                    top: ${Math.max(0, rect.top)}px;
                    left: ${Math.max(0, rect.left)}px;
                    background: #2563EB;
                    color: #FFFFFF;
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                    font-size: 11px;
                    font-weight: 700;
                    padding: 1px 4px;
                    border-radius: 4px;
                    box-shadow: 0 1px 3px rgba(0,0,0,0.4);
                    pointer-events: none;
                    line-height: 14px;
                    z-index: 2147483647;
                `;
                container.appendChild(badge);
            });
        },

        /**
         * Clear all visual badge markers
         */
        clearElementBadges: function () {
            const container = document.getElementById(OVERLAY_CONTAINER_ID);
            if (container) {
                container.innerHTML = '';
            }
        },

        /**
         * Perform robust synthetic mobile touch/input interaction on an element
         */
        interact: function (action, params) {
            params = params || {};
            const id = params.id;
            let targetEl = null;

            console.log(`[MobileAgent] Executing action '${action}' on ID #${id}`, params);

            if (id !== undefined && id !== null) {
                targetEl = elementMap.get(parseInt(id, 10)) || document.querySelector(`[data-agent-id="${id}"]`);
            } else if (params.selector) {
                targetEl = document.querySelector(params.selector);
            }

            if (!targetEl) {
                console.error(`[MobileAgent] Target element #${id} not found in DOM!`);
                return { success: false, error: `Element with id ${id} not found` };
            }

            switch (action) {
                case 'click': {
                    // Resolve closest clickable ancestor
                    const clickableTarget = targetEl.closest('a, button, input, select, textarea, [role="button"], [role="link"], [role="checkbox"], [role="tab"], [onclick], [tabindex]') || targetEl;

                    console.log("[MobileAgent] Resolved clickable target:", clickableTarget.tagName, clickableTarget.className, clickableTarget);

                    try {
                        clickableTarget.scrollIntoView({ block: 'center', inline: 'center', behavior: 'instant' });
                    } catch (e) {}

                    const rect = clickableTarget.getBoundingClientRect();
                    const clientX = rect.left + rect.width / 2;
                    const clientY = rect.top + rect.height / 2;

                    // 1. Pointer Events
                    try {
                        if (typeof PointerEvent !== 'undefined') {
                            clickableTarget.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true, cancelable: true, clientX, clientY, pointerType: 'touch', isPrimary: true }));
                        }
                    } catch (e) {}

                    // 2. Safe Touch Events
                    try {
                        if (typeof Touch !== 'undefined' && typeof TouchEvent !== 'undefined') {
                            const touchObj = new Touch({
                                identifier: Date.now(),
                                target: clickableTarget,
                                clientX: clientX,
                                clientY: clientY,
                                pageX: clientX + window.scrollX,
                                pageY: clientY + window.scrollY,
                                radiusX: 5,
                                radiusY: 5,
                                rotationAngle: 0,
                                force: 1
                            });

                            clickableTarget.dispatchEvent(new TouchEvent('touchstart', { bubbles: true, cancelable: true, touches: [touchObj], targetTouches: [touchObj], changedTouches: [touchObj] }));
                            clickableTarget.dispatchEvent(new TouchEvent('touchend', { bubbles: true, cancelable: true, touches: [], targetTouches: [], changedTouches: [touchObj] }));
                        }
                    } catch (e) {}

                    try {
                        if (typeof PointerEvent !== 'undefined') {
                            clickableTarget.dispatchEvent(new PointerEvent('pointerup', { bubbles: true, cancelable: true, clientX, clientY, pointerType: 'touch', isPrimary: true }));
                        }
                    } catch (e) {}

                    // 3. Focus & Mouse Events
                    try {
                        if (typeof clickableTarget.focus === 'function') {
                            clickableTarget.focus();
                        }
                    } catch (e) {}

                    try {
                        clickableTarget.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true, clientX, clientY, view: window, buttons: 1 }));
                        clickableTarget.dispatchEvent(new MouseEvent('mouseup', { bubbles: true, cancelable: true, clientX, clientY, view: window }));
                        clickableTarget.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, clientX, clientY, view: window }));
                    } catch (e) {}

                    // 4. Native .click() invocation
                    try {
                        if (typeof clickableTarget.click === 'function') {
                            clickableTarget.click();
                        } else if (typeof targetEl.click === 'function') {
                            targetEl.click();
                        }
                    } catch (e) {}

                    // 5. Link Navigation Fallback for standard <a> tags
                    if (clickableTarget.tagName === 'A' && clickableTarget.href && !clickableTarget.href.startsWith('javascript:')) {
                        console.log("[MobileAgent] Triggering link navigation to:", clickableTarget.href);
                        setTimeout(() => {
                            window.location.href = clickableTarget.href;
                        }, 50);
                    }

                    // 6. Form submit fallback for submit buttons
                    if (clickableTarget.tagName === 'BUTTON' && clickableTarget.type === 'submit' && clickableTarget.form) {
                        try {
                            if (typeof clickableTarget.form.requestSubmit === 'function') {
                                clickableTarget.form.requestSubmit(clickableTarget);
                            } else {
                                clickableTarget.form.submit();
                            }
                        } catch (e) {}
                    }

                    console.log(`[MobileAgent] Click sequence completed successfully for ID #${id}`);
                    return { success: true, elementId: id, action: 'click', coords: { x: clientX, y: clientY } };
                }

                case 'type': {
                    const inputEl = targetEl.closest('input, textarea, [contenteditable="true"]') || targetEl;

                    console.log("[MobileAgent] Resolved input target:", inputEl.tagName, inputEl);

                    try {
                        inputEl.scrollIntoView({ block: 'center', inline: 'center', behavior: 'instant' });
                        if (typeof inputEl.focus === 'function') inputEl.focus();
                    } catch (e) {}

                    const text = params.text || '';
                    if (params.clearFirst) {
                        inputEl.value = '';
                        inputEl.dispatchEvent(new Event('input', { bubbles: true }));
                    }

                    // Native setter update for React / Vue controlled inputs
                    try {
                        const nativeInputValueSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value')?.set;
                        const nativeTextAreaValueSetter = Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype, 'value')?.set;

                        if (inputEl.tagName === 'INPUT' && nativeInputValueSetter) {
                            nativeInputValueSetter.call(inputEl, (inputEl.value || '') + text);
                        } else if (inputEl.tagName === 'TEXTAREA' && nativeTextAreaValueSetter) {
                            nativeTextAreaValueSetter.call(inputEl, (inputEl.value || '') + text);
                        } else {
                            inputEl.value = (inputEl.value || '') + text;
                        }
                    } catch (e) {
                        inputEl.value = (inputEl.value || '') + text;
                    }

                    try {
                        inputEl.dispatchEvent(new InputEvent('input', { bubbles: true, cancelable: true, inputType: 'insertText', data: text }));
                        inputEl.dispatchEvent(new Event('change', { bubbles: true }));
                    } catch (e) {
                        inputEl.dispatchEvent(new Event('input', { bubbles: true }));
                    }

                    if (params.pressEnter) {
                        console.log("[MobileAgent] Pressing Enter and submitting form");
                        try {
                            inputEl.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true }));
                            inputEl.dispatchEvent(new KeyboardEvent('keypress', { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true }));
                            inputEl.dispatchEvent(new KeyboardEvent('keyup', { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true }));
                        } catch (e) {}

                        // Submit parent form if present
                        if (inputEl.form) {
                            try {
                                if (typeof inputEl.form.requestSubmit === 'function') {
                                    inputEl.form.requestSubmit();
                                } else if (typeof inputEl.form.submit === 'function') {
                                    inputEl.form.submit();
                                }
                            } catch (e) {}
                        }
                    }

                    console.log(`[MobileAgent] Typed text ("${text}") into element #${id}`);
                    return { success: true, elementId: id, action: 'type', length: text.length };
                }

                case 'scroll': {
                    const direction = params.direction || 'down';
                    const amount = params.amount || Math.round(window.innerHeight * 0.7);

                    let dx = 0;
                    let dy = 0;

                    if (direction === 'down') dy = amount;
                    else if (direction === 'up') dy = -amount;
                    else if (direction === 'top') dy = -window.scrollY;
                    else if (direction === 'bottom') dy = document.documentElement.scrollHeight - window.scrollY;

                    window.scrollBy({ top: dy, left: dx, behavior: 'smooth' });

                    console.log(`[MobileAgent] Scrolled ${direction} by dy=${dy}`);
                    return { success: true, action: 'scroll', direction: direction, delta: { dx, dy } };
                }

                case 'select': {
                    const selectEl = targetEl.closest('select') || targetEl;
                    if (selectEl.tagName !== 'SELECT') {
                        return { success: false, error: `Element with id ${id} is not a <select>` };
                    }
                    const value = params.value;
                    selectEl.value = value;
                    selectEl.dispatchEvent(new Event('change', { bubbles: true }));
                    console.log(`[MobileAgent] Selected option value "${value}"`);
                    return { success: true, action: 'select', value: value };
                }

                default:
                    return { success: false, error: `Unsupported interaction action: ${action}` };
            }
        },

        /**
         * Execute arbitrary JavaScript in page context
         */
        executeConsole: function (code) {
            console.log("[MobileAgent] Evaluating console code:", code);
            try {
                const evalResult = window.eval(code);
                let serializedResult;

                if (evalResult === undefined) {
                    serializedResult = "undefined";
                } else if (evalResult === null) {
                    serializedResult = "null";
                } else if (typeof evalResult === 'object') {
                    try {
                        serializedResult = JSON.stringify(evalResult);
                    } catch (e) {
                        serializedResult = String(evalResult);
                    }
                } else {
                    serializedResult = String(evalResult);
                }

                console.log("[MobileAgent] Console evaluation result:", serializedResult);
                return {
                    success: true,
                    result: serializedResult
                };
            } catch (err) {
                console.error("[MobileAgent] Console evaluation error:", err);
                return {
                    success: false,
                    error: err && err.message ? err.message : String(err),
                    stack: err && err.stack ? err.stack : null
                };
            }
        },

        /**
         * Wait for DOM mutations and SPA network requests to settle
         */
        waitForStableDOM: function (timeoutMs, debounceMs) {
            timeoutMs = timeoutMs || 2500;
            debounceMs = debounceMs || 300;

            return new Promise((resolve) => {
                let debounceTimer = null;
                let observer = null;
                let timedOut = false;

                const finish = () => {
                    if (observer) {
                        observer.disconnect();
                        observer = null;
                    }
                    if (debounceTimer) {
                        clearTimeout(debounceTimer);
                        debounceTimer = null;
                    }
                    resolve({ stable: true, timedOut: timedOut });
                };

                const globalTimeout = setTimeout(() => {
                    timedOut = true;
                    finish();
                }, timeoutMs);

                const resetDebounce = () => {
                    if (debounceTimer) clearTimeout(debounceTimer);
                    debounceTimer = setTimeout(() => {
                        clearTimeout(globalTimeout);
                        finish();
                    }, debounceMs);
                };

                observer = new MutationObserver((mutations) => {
                    resetDebounce();
                });

                observer.observe(document.body || document.documentElement, {
                    childList: true,
                    subtree: true,
                    attributes: true,
                    characterData: true
                });

                resetDebounce();
            });
        }
    };

    window.__mobileAgent = MobileAgent;
})();
