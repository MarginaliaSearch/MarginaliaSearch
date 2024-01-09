function hideMenu() {
    document.getElementById('filters').style.display = 'none';
}
function showMenu() {
    document.getElementById('filters').style.display = 'block';

    // Defer creation of the close button until the menu is opened.  This is needed because the script for creating
    // the filter button is run early to avoid layout shifts.

    if (document.getElementById('menu-close') === null) {
        registerCloseButton();
    }

    document.getElementById('filters').style.display = 'block';

    // scroll to the top of the page so the user can see the filters
    window.scrollTo({
        top: 0,
        left: 0,
        behavior: "instant",
    });
}

const registerCloseButton = () => {
    // Add a button to close the filters for mobile; we do this in js to not pollute the DOM for text-only browsers
    const closeButton = document.createElement('button');
    closeButton.setAttribute('id', 'menu-close');
    closeButton.setAttribute('title', 'Close the menu');
    closeButton.setAttribute('aria-controls', '#filters');
    closeButton.innerHTML = 'X';
    closeButton.onclick = (event) => {
        hideMenu();
        event.stopPropagation();
        return false;
    }
    document.getElementById('filters').getElementsByTagName('h2')[0].append(closeButton);
}

// Add a button to open the filters for mobile; we do this in js to not pollute the DOM for text-only browsers
const filtersButton = document.createElement('button');
filtersButton.setAttribute('id', 'mcfeast');
filtersButton.setAttribute('aria-controls', '#filters');
filtersButton.innerHTML = '&Xi;';
filtersButton.setAttribute('title', 'Open the filters menu');
filtersButton.onclick = (event) => {
    showMenu();
    event.stopPropagation();
    return false;
}

document.getElementById('search-box').getElementsByTagName('h1')[0].append(filtersButton);

// swipe affordances for mobile
if (window.matchMedia('(pointer: coarse)').matches) {
    // capture swipes to the left and right to open and close the filters
    let touchStartX = 0;
    let touchEndX = 0;
    let touchStartY = 0;
    let touchEndY = 0;

    const swipeThreshold = 100;
    const maxVerticalDistance = 75;
    document.addEventListener('touchstart', (event) => {
        touchStartX = event.changedTouches[0].screenX;
        touchStartY = event.changedTouches[0].screenY;
    });
    document.addEventListener('touchend', (event) => {
        touchEndX = event.changedTouches[0].screenX;
        touchEndY = event.changedTouches[0].screenY;
        let verticalDistance = Math.abs(touchStartY - touchEndY);

        if (verticalDistance > maxVerticalDistance) {
            return;
        }

        if (touchEndX - touchStartX > swipeThreshold) {
            showMenu();
            event.stopPropagation();
        } else if (touchStartX - touchEndX > swipeThreshold) {
            hideMenu();
            event.stopPropagation();
        }
    });


    // Add a floating panel to the bottom of the page to show a message when the filters are hidden
    const floatingPanel = document.createElement('div');
    floatingPanel.setAttribute('style', 'position: fixed; bottom: 0; left: 0; right: 0; background-color: #fff; padding: 1em; text-align: center; display: block; border-top: 1px solid #ccc; box-shadow: 0 0 -5px #eee;');
    floatingPanel.innerHTML = '&larr; right/left open/close the filters &rarr;';
    document.body.appendChild(floatingPanel);
}