function refresh(ids) {
    fetch(window.location.href)
        .then(response => response.text())
        .then(html => {
            const parser = new DOMParser();
            const newDocument = parser.parseFromString(html, "text/html");
            console.log(newDocument);

            ids.forEach(id => {
                const newElement = newDocument.getElementById(id);
                document.getElementById(id).innerHTML = newDocument.getElementById(id).innerHTML;
            });
        })
        .catch(error => {
            console.error("Error fetching webpage:", error);
        });
}