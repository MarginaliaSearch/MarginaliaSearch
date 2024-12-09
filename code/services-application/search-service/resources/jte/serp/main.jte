@import nu.marginalia.search.model.DecoratedSearchResults
@import nu.marginalia.search.model.ClusteredUrlDetails
@import nu.marginalia.search.model.NavbarModel
@import nu.marginalia.search.model.ResultsPage

@param DecoratedSearchResults results
@param NavbarModel navbar

<!DOCTYPE html>
<html lang="en">

@template.part.head(title = "Marginalia Search - " +  results.getQuery())

<body class="min-h-screen bg-slate-100 font-sans" >
@template.part.navbar(navbar = navbar)

<div>
<header class="border-b border-gray-300 bg-white sm:static sticky top-0 shadow-md">
    <div class="max-w-[1400px] mx-auto px-4 py-2 sm:py-4">
        <div class="flex items-center">
            <div class="md:w-32 md:mr-16 md:ml-16"><h1 class="text-md sm:text-xl mr-8 font-serif whitespace-nowrap"><a href="/">Marginalia Search</a></h1></div>
            <div class="fixed sm:static bottom-0 left-0 w-full p-5 sm:p-0 border sm:border-none bg-gray-50/90 backdrop-blur-sm  sm:bg-white">
                @template.serp.part.searchform(query = results.getParams().query(), profile = results.getProfile(), filters = results.getFilters())
            </div>
            <div class="grow"></div>
            <div class="sticky top-0">
            <button class="sm:hidden text-sm bg-margeblue text-white p-2 sm:p-3 rounded" id="filter-button">
                <i class="fas fa-filter mr-3"></i>
                Filters
            </button>
            </div>
        </div>
    </div>
</header>

<div class="max-w-[1400px] mx-auto flex gap-6">
    <!-- Sidebar -->
    @template.serp.part.sidebar(filters = results.getFilters())

    <!-- Main content -->
    <main class="flex-1 py-4 p-2 sm:p-4 max-w-3xl space-y-4">

        @if (results.hasFocusDomain())
            <div class="border rounded flex space-x-4 bg-white text-gray-600 text-sm p-4 items-center">
                <div>Showing results from <a class="underline text-liteblue" href="/site/${results.getFocusDomain()}">${results.getFocusDomain()}</a></div>
                <div class="grow"></div>
                <a href="${results.getParams().renderUrlWithoutSiteFocus()}" class="fa fa-remove"></a>
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
                        <a href="${results.getParams().withPage(page.number()).renderUrl()}" class="px-2 py-1 border border-gray-300 bg-gray-100">${page.number()}</a>
                    @else
                        <a href="${results.getParams().withPage(page.number()).renderUrl()}" class="px-2 py-1 bg-white border border-gray-300 hover:bg-gray-100">${page.number()}</a>
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