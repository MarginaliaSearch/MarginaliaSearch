@import nu.marginalia.language.model.LanguageDefinition
@import nu.marginalia.search.model.*
@import nu.marginalia.search.model.ClusteredUrlDetails
@import nu.marginalia.search.model.NavbarModel
@import nu.marginalia.search.model.ResultsPage
@import nu.marginalia.search.model.SearchFilters
@import nu.marginalia.search.model.SearchParameters
@import java.time.Duration
@import java.util.Map
@import java.util.Optional

@param Duration waitDuration
@param SearchFilters filters
@param SearchParameters parameters
@param NavbarModel navbar
@param String requestMethod
@param String displayUrl
@param Map<String, LanguageDefinition> languageDefinitions

<!DOCTYPE html>
<html lang="en">

@template.part.head(title = parameters.query() + " - Marginalia Search" )

<body class="min-h-screen bg-bgblue dark:bg-gray-900 dark:text-white font-sans " >
@template.part.navbar(navbar = navbar)

<header class="border-b border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 shadow-md">
    <div class="max-w-[1400px] mx-auto px-4 py-2 sm:py-4">
        <div class="flex items-center">
            <div class="hidden sm:block md:w-32 md:mr-16 md:ml-16"><h1 class="text-md sm:text-xl mr-8 font-serif whitespace-nowrap"><a href="/">Marginalia Search</a></h1></div>
            <div class="w-full p-2 border-none backdrop-blur-sm">
                @template.serp.part.searchform(query = parameters.query(),
                                               scrapeStopToken = parameters.scrapeStopperToken(),
                                               profile = parameters.profile().filterId,
                                               filters = filters,
                                               languageIsoCode = parameters.languageIsoCode(),
                                               page = 1,
                                               requestMethod = requestMethod,
                                               displayUrl = displayUrl)
            </div>
            <div class="grow"></div>
            <button class="fixed bottom-10 right-5 finepointer:hidden md:hidden text-sm bg-margeblue text-white p-4 rounded-xl active:text-slate-200" id="filter-button">
                <i class="fas fa-filter mr-3"></i>
                Filters
            </button>
            <button class="fixed bottom-10 left-5 finepointer:hidden md:hidden text-sm bg-margeblue text-white p-4 rounded-xl active:text-slate-200" id="language-button">
                <i class="fas fa-globe mr-3"></i>
                Language
            </button>
        </div>
    </div>
</header>

<div class="max-w-[1400px] mx-auto flex gap-6">
    <!-- Sidebar -->
    @template.serp.part.sidebar(filters = filters)
    @template.serp.part.language-menu(languageDefinitions = languageDefinitions, parameters = parameters)

    <!-- Main content -->
    <main class="flex-1 py-4 p-2 sm:p-4 max-w-3xl space-y-4">
        <div class="border dark:border-gray-600 rounded flex flex-col space-x-4 bg-white dark:bg-gray-800 text-gray-600 dark:text-gray-100 text-sm p-4 items-center">
            <div class="flex flex-row gap-2 place-items-center">
                <i class="fas fa-hourglass text-2xl animate-pulse"></i>
                <div class="px-2">
                The search engine is currently seeing a lot of fairly aggressive bot activity.
                Please wait for <span data-tr="${1+waitDuration.getSeconds()}" id="countdown">${1+waitDuration.getSeconds()}</span> seconds before proceeding.
                </div>
            </div>

            <noscript class="my-2">
                Click <a role="button" href="${parameters.renderUrl()}" class="underline">here</a> to proceed manually if javascript is disabled.
            </noscript>

            <script>
                window.setInterval(()=>{
                    const cd = document.getElementById('countdown');
                    var tr = cd.getAttribute('data-tr');
                    tr--;
                    cd.setAttribute('data-tr', tr);
                    cd.innerHTML=tr;
                }, 1000);

                window.setTimeout(()=> { window.location='${parameters.renderUrl()}'; }, ${waitDuration.toMillis()});
            </script>
        </div>
    </main>
</div>

@template.serp.part.footerHowto()

@template.part.footerLegal()

<%-- Put this last to not bother SR users with double menus --%>
@template.serp.part.mobile-menu(filters = filters)

</body>
<script lang="javascript" src="js/mobile-button.js"></script>
<script lang="javascript" src="js/language-button.js"></script>
<script lang="javascript" src="js/typeahead.js"></script>

</html>