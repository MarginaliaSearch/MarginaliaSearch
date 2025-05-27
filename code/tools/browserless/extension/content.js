// This script runs in the context of web pages loaded by the browser extension

// Listen to messages from the background script
var networkRequests = document.createElement('div')
networkRequests.setAttribute('id', 'marginalia-network-requests');

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
    if (message.type === 'URL_INTERCEPTED') {
        var request = document.createElement('div');
        request.setAttribute('class', 'network-request');
        request.setAttribute('data-url', message.url);
        request.setAttribute('data-method', message.method);
        request.setAttribute('data-timestamp', message.timestamp);
        networkRequests.appendChild(request)
    }
});

// Function to add styles as data attributes based on specified properties

function addStylesAsDataAttributes(propertyToAttrMap = {
    'display': 'data-display',
    'position': 'data-position',
    'visibility': 'data-visibility',
}) {
    const targetedProperties = new Set(Object.keys(propertyToAttrMap).map(prop => prop.toLowerCase()));
    const styleSheets = Array.from(document.styleSheets);

    try {
        styleSheets.forEach(styleSheet => {
            try {
                if (styleSheet.href && new URL(styleSheet.href).origin !== window.location.origin) {
                    console.warn(`Skipping cross-origin stylesheet: ${styleSheet.href}`);
                    return;
                }
                const cssRules = styleSheet.cssRules || styleSheet.rules;
                if (!cssRules) return;
                for (let i = 0; i < cssRules.length; i++) {
                    const rule = cssRules[i];
                    if (rule.type === 1) {
                        try {
                            let containsTargetedProperty = false;
                            for (let j = 0; j < rule.style.length; j++) {
                                const property = rule.style[j].toLowerCase();
                                if (targetedProperties.has(property)) {
                                    containsTargetedProperty = true;
                                    break;
                                }
                            }
                            if (!containsTargetedProperty) continue;
                            const elements = document.querySelectorAll(rule.selectorText);
                            elements.forEach(element => {
                                for (let j = 0; j < rule.style.length; j++) {
                                    const property = rule.style[j].toLowerCase();
                                    if (targetedProperties.has(property)) {
                                        const value = rule.style.getPropertyValue(property);
                                        const dataAttrName = propertyToAttrMap[property];
                                        element.setAttribute(dataAttrName, value);
                                    }
                                }
                            });
                        } catch (selectorError) {
                            console.error(`Error processing selector "${rule.selectorText}": ${selectorError.message}`);
                        }
                    }
                }
            } catch (sheetError) {
                console.error(`Error processing stylesheet: ${sheetError.message}`);
            }
        });
    } catch (error) {
        console.error(`Error adding data attributes: ${error.message}`);
    }
}


class CookieConsentHandler {
    constructor() {
        // Keywords that strongly indicate cookie consent
        this.cookieKeywords = [
            'cookie', 'cookies', 'consent', 'gdpr', 'privacy policy', 'privacy notice',
            'data protection', 'tracking', 'analytics', 'personalization', 'advertising',
            'essential cookies', 'functional cookies', 'performance cookies'
        ];

        // Keywords that indicate newsletter/subscription popups
        this.newsletterKeywords = [
            'newsletter', 'subscribe', 'email', 'signup', 'sign up', 'updates',
            'notifications', 'discount', 'offer', 'deal', 'promo', 'exclusive'
        ];

        // Common button text for accepting cookies
        this.acceptButtonTexts = [
            'accept', 'accept all', 'allow all', 'agree', 'ok', 'got it',
            'i agree', 'continue', 'yes', 'enable', 'allow cookies',
            'accept cookies', 'accept all cookies', 'i understand'
        ];

        // Common button text for rejecting (to avoid clicking these)
        this.rejectButtonTexts = [
            'reject', 'decline', 'deny', 'refuse', 'no thanks', 'no',
            'reject all', 'decline all', 'manage preferences', 'customize',
            'settings', 'options', 'learn more'
        ];

        // Special patterns that strongly indicate cookie consent
        this.acceptButtonStyles = [
            /primary/,
        ];
    }

    analyzePopover(element) {
        if (!element || !element.textContent) {
            return { category: 'unknown', action: 'none', reason: 'Invalid element' };
        }

        const textContent = element.textContent.toLowerCase();
        const category = this.categorizePopover(textContent, element);

        let result = {
            category: category,
            action: 'none',
            reason: '',
            element: element
        };

        if (category === 'cookie_consent') {
            const acceptResult = this.tryAcceptCookies(element);
            result.action = acceptResult.action;
            result.reason = acceptResult.reason;
            result.buttonClicked = acceptResult.buttonClicked;
        }

        return result;
    }

    categorizePopover(textContent, element) {
        let cookieScore = 0;
        let newsletterScore = 0;

        // Score based on keyword presence
        this.cookieKeywords.forEach(keyword => {
            if (textContent.includes(keyword)) {
                cookieScore += keyword === 'cookie' || keyword === 'cookies' ? 3 : 1;
            }
        });

        this.newsletterKeywords.forEach(keyword => {
            if (textContent.includes(keyword)) {
                newsletterScore += keyword === 'newsletter' || keyword === 'subscribe' ? 3 : 1;
            }
        });

        // Additional heuristics
        if (this.hasPrivacyPolicyLink(element)) cookieScore += 2;
        if (this.hasManagePreferencesButton(element)) cookieScore += 2;
        if (this.hasEmailInput(element)) newsletterScore += 3;
        if (this.hasDiscountMention(textContent)) newsletterScore += 2;

        // Special patterns that strongly indicate cookie consent
        const strongCookiePatterns = [
            /we use cookies/,
            /this website uses cookies/,
            /by continuing to use/,
            /essential.*cookies/,
            /improve.*experience/,
            /gdpr/,
            /data protection/
        ];

        if (strongCookiePatterns.some(pattern => pattern.test(textContent))) {
            cookieScore += 5;
        }

        // Determine category
        if (cookieScore > newsletterScore && cookieScore >= 2) {
            return 'cookie_consent';
        } else if (newsletterScore > cookieScore && newsletterScore >= 2) {
            return 'newsletter';
        } else {
            return 'other';
        }
    }

    tryAcceptCookies(element) {
        const buttons = this.findButtons(element);

        if (buttons.length === 0) {
            return { action: 'no_buttons_found', reason: 'No clickable buttons found' };
        }

        // First, try to find explicit accept buttons
        const acceptButton = this.findAcceptButton(buttons);
        if (acceptButton) {
            try {
                acceptButton.click();
                return {
                    action: 'clicked_accept',
                    reason: 'Found and clicked accept button',
                    buttonClicked: acceptButton.textContent.trim()
                };
            } catch (error) {
                return {
                    action: 'click_failed',
                    reason: `Failed to click button: ${error.message}`,
                    buttonClicked: acceptButton.textContent.trim()
                };
            }
        }

        // If no explicit accept button, try to find the most likely candidate
        const likelyButton = this.findMostLikelyAcceptButton(buttons);
        if (likelyButton) {
            try {
                likelyButton.click();
                return {
                    action: 'clicked_likely',
                    reason: 'Clicked most likely accept button',
                    buttonClicked: likelyButton.textContent.trim()
                };
            } catch (error) {
                return {
                    action: 'click_failed',
                    reason: `Failed to click button: ${error.message}`,
                    buttonClicked: likelyButton.textContent.trim()
                };
            }
        }

        return {
            action: 'no_accept_button',
            reason: 'Could not identify accept button',
            availableButtons: buttons.map(btn => btn.textContent.trim())
        };
    }

    findButtons(element) {
        const selectors = [
            'button',
            'input[type="button"]',
            'input[type="submit"]',
            '[role="button"]',
            'a[href="#"]',
            '.button',
            '.btn'
        ];

        const buttons = [];
        selectors.forEach(selector => {
            const found = element.querySelectorAll(selector);
            buttons.push(...Array.from(found));
        });

        // Remove duplicates and filter visible buttons
        return [...new Set(buttons)].filter(btn =>
            btn.offsetWidth > 0 && btn.offsetHeight > 0
        );
    }

    findAcceptButton(buttons) {
        var byClass = buttons.find(button => {
            var classes = button.className.toLowerCase();

            if (this.acceptButtonStyles.some(pattern => pattern.test(classes))) {
                return true;
            }
        });

        if (byClass != null) {
            return byClass;
        }

        return buttons.find(button => {
            const text = button.textContent.toLowerCase().trim();

            return this.acceptButtonTexts.some(acceptText =>
                text === acceptText || text.includes(acceptText)
            ) && !this.rejectButtonTexts.some(rejectText =>
                text.includes(rejectText)
            );
        });
    }

    findMostLikelyAcceptButton(buttons) {
        if (buttons.length === 1) {
            const text = buttons[0].textContent.toLowerCase();
            // If there's only one button and it's not explicitly a reject button, assume it's accept
            if (!this.rejectButtonTexts.some(rejectText => text.includes(rejectText))) {
                return buttons[0];
            }
        }

        // Look for buttons with positive styling (often green, primary, etc.)
        const positiveButton = buttons.find(button => {
            const classes = button.className.toLowerCase();
            const styles = window.getComputedStyle(button);
            const bgColor = styles.backgroundColor;

            return classes.includes('primary') ||
                classes.includes('accept') ||
                classes.includes('green') ||
                bgColor.includes('rgb(0, 128, 0)') || // green variations
                bgColor.includes('rgb(40, 167, 69)'); // bootstrap success
        });

        return positiveButton || null;
    }

    hasPrivacyPolicyLink(element) {
        const links = element.querySelectorAll('a');
        return Array.from(links).some(link =>
            link.textContent.toLowerCase().includes('privacy') ||
            link.href.toLowerCase().includes('privacy')
        );
    }

    hasManagePreferencesButton(element) {
        const buttons = this.findButtons(element);
        return buttons.some(button => {
            const text = button.textContent.toLowerCase();
            return text.includes('manage') || text.includes('preferences') ||
                text.includes('settings') || text.includes('customize');
        });
    }

    hasEmailInput(element) {
        const inputs = element.querySelectorAll('input[type="email"], input[placeholder*="email" i]');
        return inputs.length > 0;
    }

    hasDiscountMention(textContent) {
        const discountTerms = ['discount', 'off', '%', 'save', 'deal', 'offer'];
        return discountTerms.some(term => textContent.includes(term));
    }
}


var agreedToPopover = false;
// Usage example:
function handlePopover(popoverElement) {
    const handler = new CookieConsentHandler();
    const result = handler.analyzePopover(popoverElement);

    console.log('Popover analysis result:', result);

    switch (result.category) {
        case 'cookie_consent':
            console.log('Detected cookie consent popover');
            if (result.action === 'clicked_accept') {
                console.log('Successfully accepted cookies');
                agreedToPopover = true;
            } else {
                console.log('Could not accept cookies:', result.reason);
            }
            break;
        case 'newsletter':
            console.log('Detected newsletter popover - no action taken');
            break;
        default:
            console.log('Unknown popover type - no action taken');
    }

    return result;
}


function finalizeMarginaliaHack() {
    addStylesAsDataAttributes();

    // find all elements with the data-position attribute set to 'fixed'

    const fixedElements = document.querySelectorAll('[data-position="fixed"]');

    fixedElements.forEach(element => {
        handlePopover(element);
    });

    if (agreedToPopover) {
        var notice = document.createElement('div');
        notice.setAttribute('class', 'marginalia-agreed-cookies');
        networkRequests.appendChild(notice);
    }

    var finalize = () => {
        // Add a container for network requests
        document.body.appendChild(networkRequests);
        document.body.setAttribute('id', 'marginaliahack');
    }

    if (agreedToPopover) {
        // If we agreed to the popover, wait a bit before finalizing to let ad networks load
        setTimeout(finalize, 2500);
    }
    else {
        finalize();
    }
}



class EventSimulator {
    constructor() {
        this.isSimulating = false;
    }

    // Simulate smooth scrolling down the page
    simulateScrollDown(duration = 2000, distance = null) {
        return new Promise((resolve) => {
            const startTime = Date.now();
            const startScrollY = window.scrollY;
            const maxScroll = document.documentElement.scrollHeight - window.innerHeight;
            const targetDistance = distance || Math.min(window.innerHeight * 3, maxScroll - startScrollY);

            if (targetDistance <= 0) {
                resolve();
                return;
            }

            const animate = () => {
                const elapsed = Date.now() - startTime;
                const progress = Math.min(elapsed / duration, 1);

                // Ease-out function for smooth scrolling
                const easeOut = 1 - Math.pow(1 - progress, 3);
                const currentDistance = targetDistance * easeOut;
                const newScrollY = startScrollY + currentDistance;

                // Dispatch scroll events as we go
                window.scrollTo(0, newScrollY);

                // Fire custom scroll event
                const scrollEvent = new Event('scroll', {
                    bubbles: true,
                    cancelable: true
                });

                // Add custom properties to track simulation
                scrollEvent.simulated = true;
                scrollEvent.scrollY = newScrollY;
                scrollEvent.progress = progress;

                window.dispatchEvent(scrollEvent);
                document.dispatchEvent(scrollEvent);

                if (progress < 1) {
                    requestAnimationFrame(animate);
                } else {
                    resolve();
                }
            };

            requestAnimationFrame(animate);
        });
    }

    // Simulate mouse movement toward URL bar
    simulateMouseToURLBar(duration = 1500) {
        return new Promise((resolve) => {
            const startTime = Date.now();

            // Get current mouse position (or start from center of viewport)
            const startX = window.innerWidth / 2;
            const startY = window.innerHeight / 2;

            // URL bar is typically at the top center of the browser
            // Since we can't access actual browser chrome, we'll simulate movement
            // toward the top of the viewport where the URL bar would be
            const targetX = window.innerWidth / 2; // Center horizontally
            const targetY = -50; // Above the viewport (simulating URL bar position)

        const deltaX = targetX - startX;
        const deltaY = targetY - startY;

        let lastMouseEvent = null;

        const animate = () => {
            const elapsed = Date.now() - startTime;
            const progress = Math.min(elapsed / duration, 1);

            // Ease-in-out function for natural mouse movement
            const easeInOut = progress < 0.5
            ? 2 * progress * progress
            : 1 - Math.pow(-2 * progress + 2, 3) / 2;

            const currentX = startX + (deltaX * easeInOut);
            const currentY = startY + (deltaY * easeInOut);

            // Create mouse move event
            const mouseMoveEvent = new MouseEvent('mousemove', {
                bubbles: true,
                cancelable: true,
                clientX: currentX,
                clientY: currentY,
                screenX: currentX,
                screenY: currentY,
                movementX: lastMouseEvent ? currentX - lastMouseEvent.clientX : 0,
                movementY: lastMouseEvent ? currentY - lastMouseEvent.clientY : 0,
                buttons: 0,
                button: -1
            });

            // Add custom properties to track simulation
            mouseMoveEvent.simulated = true;
            mouseMoveEvent.progress = progress;
            mouseMoveEvent.targetType = 'urlbar';

            // Find element under mouse and dispatch event
            const elementUnderMouse = document.elementFromPoint(currentX, currentY);
            if (elementUnderMouse) {
                elementUnderMouse.dispatchEvent(mouseMoveEvent);

                // Also fire mouseenter/mouseleave events if element changed
                if (lastMouseEvent) {
                    const lastElement = document.elementFromPoint(
                        lastMouseEvent.clientX,
                        lastMouseEvent.clientY
                    );

                    if (lastElement && lastElement !== elementUnderMouse) {
                        // Mouse left previous element
                        const mouseLeaveEvent = new MouseEvent('mouseleave', {
                            bubbles: false, // mouseleave doesn't bubble
                            cancelable: true,
                            clientX: currentX,
                            clientY: currentY,
                            relatedTarget: elementUnderMouse
                        });
                        mouseLeaveEvent.simulated = true;
                        lastElement.dispatchEvent(mouseLeaveEvent);

                        // Mouse entered new element
                        const mouseEnterEvent = new MouseEvent('mouseenter', {
                            bubbles: false, // mouseenter doesn't bubble
                            cancelable: true,
                            clientX: currentX,
                            clientY: currentY,
                            relatedTarget: lastElement
                        });
                        mouseEnterEvent.simulated = true;
                        elementUnderMouse.dispatchEvent(mouseEnterEvent);
                    }
                }
            }

            // Also dispatch on document and window
            document.dispatchEvent(mouseMoveEvent);
            window.dispatchEvent(mouseMoveEvent);

            lastMouseEvent = mouseMoveEvent;

            if (progress < 1) {
                requestAnimationFrame(animate);
            } else {
                resolve();
            }
        };

        requestAnimationFrame(animate);
        });
    }

    // Simulate progressive page scroll with multiple scroll events
    simulateProgressiveScroll(scrollSteps = 10, stepDelay = 100) {
        return new Promise(async (resolve) => {
            const totalScrollHeight = document.documentElement.scrollHeight - window.innerHeight;
            const scrollPerStep = totalScrollHeight / scrollSteps;

            for (let i = 0; i < scrollSteps; i++) {
                const targetScroll = scrollPerStep * (i + 1);

                // Scroll to position
                window.scrollTo(0, targetScroll);

                // Create and dispatch wheel event (as if user is scrolling)
                const wheelEvent = new WheelEvent('wheel', {
                    deltaY: scrollPerStep,
                    deltaMode: WheelEvent.DOM_DELTA_PIXEL,
                    bubbles: true,
                    cancelable: true,
                    clientX: window.innerWidth / 2,
                    clientY: window.innerHeight / 2
                });
                wheelEvent.simulated = true;

                document.dispatchEvent(wheelEvent);

                // Create and dispatch scroll event
                const scrollEvent = new Event('scroll', {
                    bubbles: true,
                    cancelable: true
                });
                scrollEvent.simulated = true;
                scrollEvent.step = i + 1;
                scrollEvent.totalSteps = scrollSteps;

                window.dispatchEvent(scrollEvent);
                document.dispatchEvent(scrollEvent);

                // Wait before next step
                await new Promise(resolve => setTimeout(resolve, stepDelay));
            }

            resolve();
        });
    }

    // Simulate realistic mouse movement with slight randomness
    simulateNaturalMouseMovement(targetX, targetY, duration = 1000) {
        return new Promise((resolve) => {
            const startTime = Date.now();
            const startX = window.innerWidth / 2;
            const startY = window.innerHeight / 2;

            const basePathX = targetX - startX;
            const basePathY = targetY - startY;

            const animate = () => {
                const elapsed = Date.now() - startTime;
                const progress = Math.min(elapsed / duration, 1);

                // Add some randomness to make movement more natural
                const randomOffsetX = (Math.random() - 0.5) * 10 * (1 - progress);
                const randomOffsetY = (Math.random() - 0.5) * 10 * (1 - progress);

                // Bezier curve for more natural movement
                const t = progress;
                const bezierProgress = t * t * (3.0 - 2.0 * t);

                const currentX = startX + (basePathX * bezierProgress) + randomOffsetX;
                const currentY = startY + (basePathY * bezierProgress) + randomOffsetY;

                const mouseMoveEvent = new MouseEvent('mousemove', {
                    bubbles: true,
                    cancelable: true,
                    clientX: currentX,
                    clientY: currentY,
                    screenX: currentX,
                    screenY: currentY
                });

                mouseMoveEvent.simulated = true;
                mouseMoveEvent.natural = true;

                document.dispatchEvent(mouseMoveEvent);

                if (progress < 1) {
                    requestAnimationFrame(animate);
                } else {
                    resolve();
                }
            };

            requestAnimationFrame(animate);
        });
    }

    // Combined simulation: scroll down while moving mouse toward URL bar
    async simulateBrowsingBehavior() {
        this.isSimulating = true;

        try {
            // Start both animations simultaneously
            const scrollPromise = this.simulateScrollDown(300);
            const mousePromise = this.simulateMouseToURLBar(200);

            // Wait for both to complete
            await Promise.all([scrollPromise, mousePromise]);

            // Add a small pause
            await new Promise(resolve => setTimeout(resolve, 100));

            // Simulate some additional natural mouse movement
            await this.simulateNaturalMouseMovement(
                window.innerWidth * 0.3,
                window.innerHeight * 0.1,
                100
            );

            console.log('Browsing behavior simulation completed');
        } finally {
            this.isSimulating = false;
        }
    }
}

// Usage examples:
const simulator = new EventSimulator();

function simulateUserBehavior() {
    simulator.simulateBrowsingBehavior().then(() => {
        console.log('User behavior simulation finished');
    });
}

setTimeout(simulateUserBehavior, 500);
window.addEventListener("load", (e) => setTimeout(finalizeMarginaliaHack, 2000));
