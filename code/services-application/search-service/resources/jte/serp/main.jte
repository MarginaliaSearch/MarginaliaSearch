@import nu.marginalia.search.model.DecoratedSearchResults
@import nu.marginalia.search.model.ClusteredUrlDetails
@import nu.marginalia.search.model.NavbarModel
@import nu.marginalia.search.model.ResultsPage

@param DecoratedSearchResults results
@param NavbarModel navbar

<!DOCTYPE html>
<html lang="en">

@template.part.head(title = "Marginalia Search - " +  results.getQuery())

<body class="min-h-screen bg-slate-100 dark:bg-gray-900 dark:text-white font-sans " >
@template.part.navbar(navbar = navbar)

<div>
<header class="border-b border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 shadow-md">
    <div class="max-w-[1400px] mx-auto px-4 py-2 sm:py-4">
        <div class="flex items-center">
            <div class="hidden sm:block md:w-32 md:mr-16 md:ml-16"><h1 class="text-md sm:text-xl mr-8 font-serif whitespace-nowrap"><a href="/">Marginalia Search</a></h1></div>
            <div class="w-full p-2 border-none backdrop-blur-sm">
                @template.serp.part.searchform(query = results.getParams().query(), profile = results.getProfile(), filters = results.getFilters())
            </div>
            <div class="grow"></div>
            <button class="fixed bottom-10 right-5 finepointer:hidden md:hidden text-sm bg-margeblue text-white p-4 rounded-xl active:text-slate-200" id="filter-button">
                <i class="fas fa-filter mr-3"></i>
                Filters
            </button>
        </div>
    </div>
</header>

<div class="max-w-[1400px] mx-auto flex gap-6">
    <!-- Sidebar -->
    @template.serp.part.sidebar(filters = results.getFilters())

    <!-- Main content -->
    <main class="flex-1 py-4 p-2 sm:p-4 max-w-3xl space-y-4">

        @if (results.hasFocusDomain())
            <div class="border dark:border-gray-600 rounded flex space-x-4 bg-white dark:bg-gray-800 text-gray-600 dark:text-gray-100 text-sm p-4 items-center">
                <div>Showing results from <a class="underline text-liteblue dark:text-blue-200" href="/site/${results.getFocusDomain()}">${results.getFocusDomain()}</a></div>
                <div class="grow"></div>
                <a href="${results.getParams().renderUrlWithoutSiteFocus()}" class="fa fa-remove"></a>
            </div>
        @elseif (results.isEmpty())
            <div class="border dark:border-gray-600 rounded flex space-x-4 bg-white dark:bg-gray-800 text-gray-600 dark:text-gray-100 text-sm p-4 items-center">
                No search results found.  Try different search terms, or spelling variations.  The search engine currently
                only supports queries in the English language.
            </div>
        @endif

        <div class="space-y-4 sm:space-y-6">
            <!-- Result items -->
            @for(ClusteredUrlDetails details : results.results)
                @template.serp.part.result(result = details, parameters = results.getParams(), domainSearch = results.hasFocusDomain())
            @endfor
        </div>

        <!-- Pagination -->
        @if (results.getResultPages().size() > 1)
            <div class="mt-8 flex justify-center space-x-2 font-mono text-sm">
                @for(ResultsPage page : results.getResultPages())
                    @if (page.current())
                        <a href="${results.getParams().withPage(page.number()).renderUrl()}" class="px-2 py-1 border dark:border-gray-600 border-gray-300 bg-gray-100 dark:bg-gray-900">${page.number()}</a>
                    @else
                        <a href="${results.getParams().withPage(page.number()).renderUrl()}" class="px-2 py-1 bg-white border dark:border-gray-600 border-gray-300 hover:bg-gray-100 dark:bg-gray-800 hover:bg-gray-900">${page.number()}</a>
                    @endif
                @endfor
            </div>
        @endif
    </main>
</div>

</div>

@template.serp.part.footerHowto()

@template.part.footerLegal()

<%-- Put this last to not bother SR users with double menus --%>
@template.serp.part.mobile-menu(filters = results.getFilters())

</body>
<script lang="javascript" src="js/mobile-button.js"></script>
<script lang="javascript" src="js/typeahead.js"></script>

</html>