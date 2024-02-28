function refresh(ids) {
    fetch(window.location.href)
        .then(response => response.text())
        .then(html => {
            const parser = new DOMParser();
            const newDocument = parser.parseFromString(html, "text/html");

            ids.forEach(id => {
                const newElement = newDocument.getElementById(id);
                const targetElement = document.getElementById(id);

                if (newElement == null)
                    return;
                if (targetElement == null)
                    return;

                if (!newElement.isEqualNode(targetElement)) {
                    targetElement.replaceWith(document.importNode(newElement, true))
                }
            });
        })
        .catch(error => {
            console.error("Error fetching webpage:", error);
        });
}