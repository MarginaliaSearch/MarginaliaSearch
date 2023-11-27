document.getElementsByTagName('body')[0].setAttribute('data-has-js', 'true');

// Add a button to open the filters for mobile; we do this in js to not pollute the DOM for text-only browsers
const button = document.createElement('button');
button.setAttribute('id', 'mcfest');
button.setAttribute('aria-controls', '#filters');
button.onclick = (event) => {
    setDisplay(document.getElementById('filters'), 'block');
    event.stopPropagation();
    return false;
}
button.innerHTML = 'Filters';
document.getElementById('search-box').getElementsByTagName('h1')[0].append(button);

function setDisplay(element, value) {
    element.style.display = value;
}

document.getElementById('menu-close').onclick = (event) => {
    setDisplay(document.getElementById('filters'), 'none');
    event.stopPropagation();
    return false;
}