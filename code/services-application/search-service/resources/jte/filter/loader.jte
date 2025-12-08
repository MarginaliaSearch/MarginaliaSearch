@import nu.marginalia.functions.searchquery.searchfilter.model.SearchFilterSpec
@import nu.marginalia.search.model.NavbarModel

@param NavbarModel navbar
@param SearchFilterSpec filter

<!DOCTYPE html>
<html lang="en">

@template.part.head(title =  "Filter Settings - Marginalia Search")

<script lang="javascript">
(async () => {
    const rsp = await fetch('/filters', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: localStorage.getItem('custom-filter')
    })
    if (rsp.ok) {
        // Anchor tags hate him for this one weird trick

        const text = await rsp.text()
        const parser = new DOMParser();
        const newBody = parser.parseFromString(text, "text/html")

        document.body.replaceWith(newBody.body)

        // Recreate script tags
        document.body.querySelectorAll("script").forEach(oldScript => {
            const newScript = document.createElement('script');

            Array.from(oldScript.attributes).forEach(attr => {
                newScript.setAttribute(attr.name, attr.value);
            });
            if (oldScript.textContent) {
                newScript.textContent = oldScript.textContent;
            }
            oldScript.parentNode.replaceChild(newScript, oldScript);
        });
    }
    else {
        alert('Something went wrong')
        console.log(rsp)
    }
})();
</script>
</html>