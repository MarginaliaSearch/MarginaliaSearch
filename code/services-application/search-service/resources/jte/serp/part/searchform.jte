@import nu.marginalia.search.model.SearchFilters

@param String query
@param String profile
@param String languageIsoCode
@param SearchFilters filters


<form class="flex-1 max-w-2xl" action="/search">
    <div class="flex">
        @if (query != null && query.isBlank())
        <%-- Add autofocus if the query is blank --%>
            <input type="text"
                   class="shadow-inner flex-1 dark:bg-black dark:text-gray-100 bg-gray-50 border dark:border-gray-600 border-gray-300 text-gray-900 text-sm rounded-sm block w-full p-2.5"
                   value="${query}"
                   autofocus
                   placeholder="Search the web!"
                   autocomplete="off"
                   name="query"
                   id="searchInput" />
        @else
            <input type="text"
                   class="shadow-inner flex-1 dark:bg-black dark:text-gray-100 bg-gray-50 border dark:border-gray-600 border-gray-300 text-gray-900 text-sm rounded-sm block w-full p-2.5"
                   value="${query}"
                   placeholder="Search the web!"
                   autocomplete="off"
                   name="query"
                   id="searchInput" />
        @endif

        <div aria-hidden="true" id="searchSuggestions" class="text-sm absolute top-3 mt-10 w-96 bg-white dark:bg-black border dark:border-gray-600 border-gray-300 rounded-lg shadow-lg hidden"></div>

        <button class="px-4 py-2 bg-margeblue text-white ml-2 rounded whitespace-nowrap active:text-slate-200">
            <i class="fas fa-search text-sm sm:mr-3"></i>
            Search
        </button>

    </div>

    @if (filters.showRecentOption.isSet()) <input type="hidden" name="js" value="${filters.removeJsOption.value()}"> @endif
    @if (filters.reduceAdtechOption.isSet()) <input type="hidden" name="adtech" value="${filters.reduceAdtechOption.value()}"> @endif
    @if (filters.searchTitleOption.isSet()) <input type="hidden" name="searchTitle" value="${filters.searchTitleOption.value()}"> @endif
    @if (filters.showRecentOption.isSet()) <input type="hidden" name="recent" value="${filters.showRecentOption.value()}"> @endif
    @if (languageIsoCode != null && !"en".equalsIgnoreCase(languageIsoCode)) <input type="hidden" name="lang" value="${languageIsoCode}"> @endif

    <input type="hidden" name="profile" value="${profile}">

</form>
