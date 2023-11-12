document.getElementsByTagName('body')[0].setAttribute('data-has-js', 'true');

function setDisplay(element, value) {
    element.style.display = value;
}

document.getElementById('mcfeast').onclick = (event) => {
    setDisplay(document.getElementById('filters'), 'block');
    event.stopPropagation();
    return false;
}

document.getElementById('menu-close').onclick = (event) => {
    setDisplay(document.getElementById('filters'), 'none');
    event.stopPropagation();
    return false;
}