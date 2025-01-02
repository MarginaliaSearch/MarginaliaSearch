@import nu.marginalia.browse.model.BrowseResult
@import nu.marginalia.search.model.NavbarModel
@import nu.marginalia.browse.model.BrowseResultSet

@param NavbarModel navbar
@param BrowseResultSet results

<!DOCTYPE html>
<html lang="en">

@template.part.head(title = "Marginalia Search - Explore")

<body class="min-h-screen bg-slate-100 dark:bg-gray-900 dark:text-white font-sans ">

@template.part.navbar(navbar = navbar)

<header class="border-b border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 shadow-md">
    <div class="max-w-[1400px] mx-auto px-4 py-4">
        <div class="flex items-center  space-x-2">
            <h1 class="text-base md:text-xl mr-2 md:mr-8 font-serif">Explore Websites</h1>
        </div>
    </div>
</header>

<div class="max-w-[1400px] mx-auto flex flex-col gap-1 place-items-center">
    <div class="border dark:border-gray-600 bg-white dark:bg-gray-800 dark:text-gray-100 my-4 p-3 rounded overflow-hidden flex flex-col space-y-4">
        @if (results.hasFocusDomain())
            <div class="flex space-x-1">
                <span>Showing websites similar to <a class="font-mono text-liteblue dark:text-blue-200" href="/site/${results.focusDomain()}"><i class="fas fa-globe"></i> <span class="underline">${results.focusDomain()}</span></a></span>
                <span class="grow w-32"></span>
                <a href="/explore/random" title="Return to random results"><i class="fas fa-close"></i></a>
            </div>
        @else
            Showing a random selection of websites.  Refresh the website to get a new selection.
        @endif
    </div>
    <div class="grid-cols-1 gap-4 sm:grid sm:grid-cols-1 md:grid-cols-3 xl:grid-cols-4 mx-auto sm:p-4">
        @for (BrowseResult result : results.results())
            <div class="bg-white border dark:border-gray-600 dark:bg-gray-800 rounded overflow-hidden">
                <div class="bg-margeblue text-white p-2 flex space-x-4 text-sm">
                    <span class="break-words">${result.displayDomain()}</span>
                    <div class="grow"></div>
                    <a href="/site/${result.displayDomain()}" title="Show website information"><i class="fas fa-info"></i></a>
                    <a href="/explore/${result.displayDomain()}" title="Show results similar to this website"><i class="fas fa-shuffle"></i></a>
                </div>
                <a href="${result.url().toString()}">
                    <img loading="lazy" class="p-2" width="800" height="600" src="/screenshot/${result.domainId()}" alt="Screenshot of ${result.displayDomain()}" />
                </a>
            </div>
        @endfor
    </div>
</div>


@template.part.footerLegal()

</body>
</html>