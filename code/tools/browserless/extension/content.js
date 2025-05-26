

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

function finalizeMarginaliaHack() {
    addStylesAsDataAttributes();

    document.body.setAttribute('id', 'marginaliahack');

    MutationObserver = window.MutationObserver || window.WebKitMutationObserver;

    var observer = new MutationObserver(function(mutations, observer) {
        addStylesAsDataAttributes();
    });

    observer.observe(document, {
        subtree: true,
        attributes: true
        //...
    });
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
document.addEventListener("load", setTimeout(finalizeMarginaliaHack, 2000));
