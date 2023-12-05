// This sets the data-has-js attribute on the body tag to true, so we can style the page with the assumption that
// the browser supports JS. This is a progressive enhancement, so the page will still work without JS.
document.getElementsByTagName('body')[0].setAttribute('data-has-js', 'true');

// To prevent the filter menu from being opened when the user hits enter on the search box, we need to add a keydown
// handler to the search box that stops the event from propagating.  Janky hack, but it works.
document.getElementById('query').addEventListener('keydown', e=> {
    if (e.key === "Enter") {
        const form = document.getElementById('search-form');
        form.submit();
        e.preventDefault();
    }
});
