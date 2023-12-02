
function setupTypeahead() {
    const query = document.getElementById('query');
    query.setAttribute('autocomplete', 'off');
    const queryBox = document.getElementById('suggestions-anchor');
    let timer = null;

    function fetchSuggestions(e) {
        if (timer != null) {
            clearTimeout(timer);
        }
        timer = setTimeout(() => {
            const req = new XMLHttpRequest();

            req.onload = rsp => {
                let items = JSON.parse(req.responseText);

                const old = document.getElementById('suggestions');
                if (old != null) old.remove();


                if (items.length === 0) return;

                console.log(items);

                const suggestions = document.createElement('div');
                suggestions.setAttribute('id', 'suggestions');
                suggestions.setAttribute('class', 'suggestions');


                for (i=0;i<items.length;i++) {
                    item = document.createElement('a');
                    item.innerHTML=items[i];
                    item.setAttribute('href', '#')

                    function suggestionClickHandler(e) {
                        query.value = e.target.text;
                        query.focus();
                        document.getElementById('suggestions').remove();
                        e.preventDefault()
                    }
                    item.addEventListener('click', suggestionClickHandler);

                    item.addEventListener('keydown', e=> {
                        if (e.key === "ArrowDown") {
                            if (e.target.nextElementSibling != null) {
                                e.target.nextElementSibling.focus();
                            }

                            e.preventDefault()
                        }
                        else if (e.key === "ArrowUp") {
                            if (e.target.previousElementSibling != null) {
                                e.target.previousElementSibling.focus();
                            }
                            else {
                                query.focus();
                            }
                            e.preventDefault()
                        }
                        else if (e.key === "Escape") {
                            var suggestions = document.getElementById('suggestions');
                            if (suggestions != null) {
                                suggestions.remove();
                            }
                            query.focus();
                            e.preventDefault();
                        }
                    });
                    item.addEventListener('keypress', e=> {
                        if (e.key === "Enter") {
                            suggestionClickHandler(e);
                        }
                    });
                    suggestions.appendChild(item);
                }
                queryBox.prepend(suggestions);
            }

            req.open("GET", "/suggest/?partial="+encodeURIComponent(query.value));
            req.send();
        }, 250);
    }
    query.addEventListener("input", fetchSuggestions);
    query.addEventListener("click", e=> {
        const suggestions = document.getElementById('suggestions');
        if (suggestions != null) {
            suggestions.remove();
        }
    });
    query.addEventListener("keydown", e => {
        if (e.key === "ArrowDown") {
            const suggestions = document.getElementById('suggestions');
            if (suggestions != null) {
                suggestions.childNodes[0].focus();
            }
            else {
                fetchSuggestions(e);
            }
            e.preventDefault()
        }
        else if (e.key === "Escape") {
            const suggestions = document.getElementById('suggestions');
            if (suggestions != null) {
                suggestions.remove();
            }
            query.focus();
            e.preventDefault();
        }
    });
}

if(!window.matchMedia("(pointer: coarse)").matches) {
    setupTypeahead();
}
