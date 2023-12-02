const registerCloseButton = () => {
    // Add a button to close the filters for mobile; we do this in js to not pollute the DOM for text-only browsers
    const closeButton = document.createElement('button');
    closeButton.setAttribute('id', 'menu-close');
    closeButton.setAttribute('title', 'Close the menu');
    closeButton.setAttribute('aria-controls', '#filters');
    closeButton.innerHTML = 'X';
    closeButton.onclick = (event) => {
        document.getElementById('filters').style.display = 'none';
        event.stopPropagation();
        return false;
    }
    document.getElementById('filters').getElementsByTagName('h2')[0].append(closeButton);
}

// Add a button to open the filters for mobile; we do this in js to not pollute the DOM for text-only browsers
const filtersButton = document.createElement('button');
filtersButton.setAttribute('id', 'mcfeast');
filtersButton.setAttribute('aria-controls', '#filters');
filtersButton.onclick = (event) => {
    // Defer creation of the close button until the menu is opened.  This is needed because the script for creating
    // the filter button is run early to avoid layout shifts.

    if (document.getElementById('menu-close') === null) {
        registerCloseButton();
    }

    document.getElementById('filters').style.display = 'block';
    event.stopPropagation();
    return false;
}
filtersButton.innerHTML = 'Filters';
document.getElementById('search-box').getElementsByTagName('h1')[0].append(filtersButton);
