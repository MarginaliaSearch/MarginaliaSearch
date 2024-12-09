@import nu.marginalia.search.model.SearchFilters

@param String query
@param String profile
@param SearchFilters filters


<form class="flex-1 max-w-2xl" action="/search">
    <div class="flex">
        @if (query.isBlank())
        <%-- Add autofocus if the query is blank --%>
            <input type="text"
                   class="shadow-inner flex-1 bg-gray-50 border border-gray-300 text-gray-900 text-sm rounded-sm block w-full p-2.5"
                   value="${query}"
                   autofocus
                   placeholder="Search..."
                   autocomplete="off"
                   name="query"
                   id="searchInput" />
        @else
            <input type="text"
                   class="shadow-inner flex-1 bg-gray-50 border border-gray-300 text-gray-900 text-sm rounded-sm block w-full p-2.5"
                   value="${query}"
                   placeholder="Search..."
                   autocomplete="off"
                   name="query"
                   id="searchInput" />
        @endif

        <div id="searchSuggestions" class="text-sm absolute top-2 mt-10 w-96 bg-white border border-gray-200 rounded-lg shadow-lg hidden"></div>

        <button class="px-4 py-2 bg-margeblue text-white ml-2 rounded whitespace-nowrap ">
            <i class="fas fa-search text-sm sm:mr-3"></i>
            Search
        </button>

    </div>


    <input type="hidden" name="js" value="${filters.removeJsOption.value()}">
    <input type="hidden" name="adtech" value="${filters.reduceAdtechOption.value()}">
    <input type="hidden" name="searchTitle" value="${filters.searchTitleOption.value()}">
    <input type="hidden" name="profile" value="${profile}">
    <input type="hidden" name="recent" value="${filters.showRecentOption.value()}">
</form>
