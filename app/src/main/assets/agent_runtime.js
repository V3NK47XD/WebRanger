/**
 * Mobile Chromium AI Agent Runtime
 * In-page JavaScript agent bundle providing non-destructive DOM tree extraction, accessibility labeling,
 * synthetic touch/keyboard interactions, console execution, and SPA stability tracking.
 */
(function () {
    if (window.__mobileAgent) {
        return;
    }


    let elementIndexCounter = 0;
    const elementMap = new Map(); // id -> HTMLElement (In-memory, zero DOM pollution)

    const INTERACTIVE_ROLES = new Set([
        'button', 'link', 'checkbox', 'radio', 'combobox', 'textbox',
        'searchbox', 'tab', 'menuitem', 'option', 'switch', 'slider'
    ]);

    const IGNORED_TAGS = new Set([
        'SCRIPT', 'STYLE', 'META', 'LINK', 'NOSCRIPT', 'IFRAME', 'SVG', 'PATH', 'HEAD'
    ]);

    /**
     * Check if an element is visible in the page / viewport
     */
    function isElementVisible(el, computedStyle) {
        if (!el || !computedStyle) return false;
        if (computedStyle.display === 'none' || computedStyle.visibility === 'hidden' || parseFloat(computedStyle.opacity) === 0) {
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
        const tagName = el.tagName.toUpperCase();
        if (['A', 'BUTTON', 'INPUT', 'SELECT', 'TEXTAREA'].includes(tagName)) {
            return true;
        }

        if (el.hasAttribute('onclick') || el.hasAttribute('tabindex') || el.isContentEditable) {
            return true;
        }

        const role = el.getAttribute('role');
        if (role && INTERACTIVE_ROLES.has(role.toLowerCase())) {
            return true;
        }

        if (computedStyle && (computedStyle.cursor === 'pointer' || computedStyle.touchAction === 'manipulation')) {
            return true;
        }

        if (el.hasAttribute('data-action') || el.hasAttribute('data-clickable') || el.hasAttribute('aria-expanded')) {
            return true;
        }

        return false;
    }

    /**
     * Compute accessible text name for an element
     */
    function getAccessibleName(el) {
        const ariaLabel = el.getAttribute('aria-label');
        if (ariaLabel && ariaLabel.trim()) return ariaLabel.trim();

        const ariaLabelledBy = el.getAttribute('aria-labelledby');
        if (ariaLabelledBy) {
            const labelledEl = document.getElementById(ariaLabelledBy);
            if (labelledEl && labelledEl.innerText && labelledEl.innerText.trim()) {
                return labelledEl.innerText.trim();
            }
        }

        const alt = el.getAttribute('alt');
        if (alt && alt.trim()) return alt.trim();

        const placeholder = el.getAttribute('placeholder');
        if (placeholder && placeholder.trim()) return placeholder.trim();

        const title = el.getAttribute('title');
        if (title && title.trim()) return title.trim();

        // Visible text content
        let text = (el.innerText || el.textContent || '').trim();
        if (text) {
            if (text.length > 100) {
                text = text.substring(0, 97) + '...';
            }
            return text.replace(/\s+/g, ' ');
        }

        const value = el.value;
        if (value && typeof value === 'string' && value.trim()) {
            return value.trim();
        }

        return '';
    }

    const MobileAgent = {
        /**
         * Extract semantic DOM snapshot and interactive element registry without mutating DOM nodes
         */
        getDOMSnapshot: function (options) {
            options = options || {};
            const viewportOnly = options.viewportOnly !== false;
            
            elementIndexCounter = 0;
            elementMap.clear();

            const snapshotElements = [];
            const viewportWidth = window.innerWidth || document.documentElement.clientWidth || 360;
            const viewportHeight = window.innerHeight || document.documentElement.clientHeight || 640;

            const allElements = document.querySelectorAll('*');

            for (let i = 0; i < allElements.length; i++) {
                const el = allElements[i];
                if (IGNORED_TAGS.has(el.tagName)) continue;

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
                // Pure in-memory reference: DO NOT mutate el.setAttribute which crashes Virtual DOM roots (React/Vue/Next)
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

        showElementBadges: function () {
            // No-op in-page DOM injection: Compose renders highlights natively to prevent React/Vue hydration crashes
        },

        clearElementBadges: function () {
            // No-op
        },

        /**
         * Perform robust synthetic mobile touch/input interaction on an element
         */
        interact: function (action, params) {
            params = params || {};
            const id = params.id;
            let targetEl = null;


            if (id !== undefined && id !== null) {
                targetEl = elementMap.get(parseInt(id, 10));
            } else if (params.selector) {
                targetEl = document.querySelector(params.selector);
            }

            if (!targetEl) {
                return { success: false, error: `Element with id ${id} not found` };
            }

            switch (action) {
                case 'click': {
                    const clickableTarget = targetEl.closest('a, button, input, select, textarea, [role="button"], [role="link"], [role="checkbox"], [role="tab"], [onclick], [tabindex]') || targetEl;

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

                    // 2. Touch Events
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

                    // 5. Form submit fallback for submit buttons
                    if (clickableTarget.tagName === 'BUTTON' && clickableTarget.type === 'submit' && clickableTarget.form) {
                        try {
                            if (typeof clickableTarget.form.requestSubmit === 'function') {
                                clickableTarget.form.requestSubmit(clickableTarget);
                            } else {
                                clickableTarget.form.submit();
                            }
                        } catch (e) {}
                    }

                    return { success: true, elementId: id, action: 'click', coords: { x: clientX, y: clientY } };
                }

                case 'type': {
                    const inputEl = targetEl.closest('input, textarea, [contenteditable="true"]') || targetEl;

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
                        try {
                            inputEl.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true }));
                            inputEl.dispatchEvent(new KeyboardEvent('keypress', { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true }));
                            inputEl.dispatchEvent(new KeyboardEvent('keyup', { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true }));
                        } catch (e) {}

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

                    window.scrollBy({ left: dx, top: dy, behavior: 'smooth' });
                    return { success: true, action: 'scroll', direction: direction, amount: amount };
                }

                case 'select': {
                    const selectEl = targetEl.closest('select') || targetEl;
                    if (selectEl.tagName === 'SELECT') {
                        const val = params.value;
                        if (val !== undefined) {
                            selectEl.value = val;
                            selectEl.dispatchEvent(new Event('change', { bubbles: true }));
                            return { success: true, elementId: id, action: 'select', value: val };
                        }
                    }
                    return { success: false, error: 'Element is not a <select>' };
                }

                default:
                    return { success: false, error: `Unknown interaction action '${action}'` };
            }
        },

        /**
         * Evaluate JavaScript code securely in window context
         */
        executeConsole: function (code) {
            try {
                const evalResult = window.eval(code);
                if (evalResult === undefined) return "undefined";
                if (evalResult === null) return "null";
                if (typeof evalResult === 'object') {
                    try {
                        return JSON.stringify(evalResult);
                    } catch (e) {
                        return String(evalResult);
                    }
                }
                return String(evalResult);
            } catch (err) {
                return `Error: ${err.name} - ${err.message}`;
            }
        },

        /**
         * Wait for DOM mutations or network quiet period to settle
         */
        waitForStableDOM: function (timeoutMs, debounceMs) {
            timeoutMs = timeoutMs || 2500;
            debounceMs = debounceMs || 300;

            return new Promise((resolve) => {
                let timer = null;
                let isResolved = false;

                function onStable() {
                    if (!isResolved) {
                        isResolved = true;
                        observer.disconnect();
                        resolve('stable');
                    }
                }

                const observer = new MutationObserver(() => {
                    clearTimeout(timer);
                    timer = setTimeout(onStable, debounceMs);
                });

                observer.observe(document.body || document.documentElement, {
                    childList: true,
                    subtree: true,
                    attributes: true,
                    characterData: true
                });

                timer = setTimeout(onStable, debounceMs);
                setTimeout(() => {
                    onStable();
                }, timeoutMs);
            });
        }
    };

    try {
        Object.defineProperty(window, '__mobileAgent', {
            value: MobileAgent,
            writable: true,
            configurable: true,
            enumerable: false
        });
    } catch (_) {
        window.__mobileAgent = MobileAgent;
    }
})();
