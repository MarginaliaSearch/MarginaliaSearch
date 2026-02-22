@import nu.marginalia.search.model.NavbarModel
@import nu.marginalia.search.model.UrlDetails
@import nu.marginalia.model.EdgeDomain
@import nu.marginalia.search.svc.*
@import java.time.Duration

@param SearchCrosstalkService.CrosstalkWait model
@param NavbarModel navbar

<!DOCTYPE html>
<html lang="en">

@template.part.head(title = model.domainA() + "/" + model.domainB() + " - Marginalia Search")

<body class="min-h-screen bg-bgblue dark:bg-gray-900 dark:text-white font-sans " >

@template.part.navbar(navbar = navbar)

<header class="border-b border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 shadow-md">
    <div class="max-w-[1400px] mx-auto p-2 md:p-4">
        <div class="flex flex-col place-items-center md:flex-row md:place-items-baseline space-x-2">
            <div class="text-gray-900 dark:text-white text-md font-mono rounded-sm block p-2.5 break-all">
                <a class="underline" href="/site/${model.domainA()}"> ${model.domainA()}</a>
            </div>

            <div class="hidden md:block">
                <i class="text-margeblue dark:text-slate-200 fa-solid fa-arrows-left-right"></i>
            </div>
            <div class="block md:hidden">
                <i class="text-margeblue dark:text-slate-200 fa-solid fa-up-down"></i>
            </div>

            <div class="text-gray-900 dark:text-white text-md font-mono rounded-sm block p-2.5 break-all">
                <a class="underline" href="/site/${model.domainB()}"> ${model.domainB()}</a>
            </div>
            <span class="grow"></span>
        </div>
    </div>
</header>

!{
    Duration waitDuration = model.waitTime();
}
<div class="max-w-[1000px] mx-auto flex gap-1 flex-col md:flex-row place-items-center md:place-items-start py-4 basis-1/2">
    <div class="border dark:border-gray-600 rounded flex flex-col space-x-4 bg-white dark:bg-gray-800 text-gray-600 dark:text-gray-100 text-sm p-4 items-center">
        <div class="flex flex-row gap-2 place-items-center">
            <i class="fas fa-hourglass text-2xl animate-pulse"></i>
            <div class="px-2">
                The search engine is currently seeing a lot of fairly aggressive bot activity.
                Please wait for <span data-tr="${1+waitDuration.getSeconds()}" id="countdown">${1+waitDuration.getSeconds()}</span> seconds before proceeding.
            </div>
        </div>

        <noscript class="my-2">
            Click <a role="button" href="${model.redirUrl()}" class="underline">here</a> to proceed manually if javascript is disabled.
        </noscript>

        <script>
            window.setInterval(()=>{
                const cd = document.getElementById('countdown');
                var tr = cd.getAttribute('data-tr');
                tr--;
                cd.setAttribute('data-tr', tr);
                cd.innerHTML=tr;
            }, 1000);

            window.setTimeout(()=> { window.location='${model.redirUrl()}'; }, ${waitDuration.toMillis()});
        </script>
    </div>
</div>


@template.part.footerLegal()

</body>
</html>