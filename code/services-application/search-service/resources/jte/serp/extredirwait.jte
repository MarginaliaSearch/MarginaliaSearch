@import nu.marginalia.search.model.NavbarModel
@import nu.marginalia.search.model.UrlDetails
@import nu.marginalia.model.EdgeDomain
@import nu.marginalia.search.svc.*
@import java.time.Duration

@param NavbarModel navbar
@param Duration waitDuration
@param String redirUrl

<!DOCTYPE html>
<html lang="en">

@template.part.head(title = "One Moment - Marginalia Search")

<body class="min-h-screen bg-bgblue dark:bg-gray-900 dark:text-white font-sans " >

<a class="sr-only" href="skipnav">Skip To Content</a>
@template.part.navbar(navbar = navbar)

<div id="skipnav"></div>


<div class="max-w-[1000px] mx-auto flex gap-1 flex-col md:flex-row place-items-center md:place-items-start py-4 basis-1/2">
    <div class="border dark:border-gray-600 rounded flex flex-col space-x-4 bg-white dark:bg-gray-800 text-gray-600 dark:text-gray-100 text-sm p-4 items-center">

        <div class="flex flex-row gap-2 place-items-center">
            <i class="fas fa-clock text-2xl animate-pulse"></i>
            <h1 class="font-bold text-2xl">Wait A Moment</h1>
        </div>

        <div>
            You've run into the Marginalia Search anti-scraper measures.  Wait a few seconds before you're redirected
            to the search result.  This is a regrettable necessity for operating the search engine on a single server.
            Unless there's a bug or you're being very naughty, you should never see this screen.
        </div>
        <div>
            Please wait for <span class="font-bold" data-tr="${1+waitDuration.getSeconds()}" id="countdown">${1+waitDuration.getSeconds()}</span> seconds before proceeding.
        </div>

        <noscript class="my-2">
            Click <a role="button" href="${redirUrl}" class="underline">here</a> to proceed manually if javascript is disabled.
        </noscript>

        <script>
            window.setInterval(()=>{
                const cd = document.getElementById('countdown');
                var tr = cd.getAttribute('data-tr');
                tr--;
                cd.setAttribute('data-tr', tr);
                cd.innerHTML=tr;
            }, 1000);

            window.setTimeout(()=> { location.replace('${redirUrl}'); }, ${waitDuration.toMillis()});
        </script>
    </div>
</div>


@template.part.footerLegal()

</body>
</html>