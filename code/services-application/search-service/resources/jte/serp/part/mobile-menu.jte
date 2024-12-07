@import nu.marginalia.search.model.SearchFilters
@import java.util.List

@param SearchFilters filters


<aside id="mobile-menu" class="fixed inset-0 z-50 flex justify-center bg-white/90 backdrop-blur-sm hidden">
    <button id="hide-filter-button" class="fixed top-5 right-5 bg-margeblue text-white p-3 rounded text-sm">
        <i class="fas fa-close"></i>
    </button>
    <div class="flex mx-auto flex-col my-10 px-4">
        <div class="text-xl my-4"><i class="fas fa-filter mr-2 text-margeblue"></i>Filters</div>
        <div class="grid grid-cols-3 gap-4">
            @for (List<SearchFilters.Filter> filterGroup : filters.getFilterGroups())
                @for (SearchFilters.Filter filter : filterGroup)
                    <label class="flex items-center">
                        <button onclick="document.location='$unsafe{filter.url}'" class="flex-1 py-2 rounded flex flex-col place-items-center has-[:checked]:bg-gray-100 has-[:checked]:text-slate-900 hover:bg-gray-50 text-margeblue">
                            @if (filter.current)
                                <input type="checkbox" checked class="sr-only" aria-checked="true" />
                            @else
                                <input type="checkbox" class="sr-only" aria-checked="false" />
                            @endif
                            <i class="fas ${filter.icon} text-xl"></i>
                            <a tabindex="-1" href="$unsafe{filter.url}" class="text-sm" href="#">${filter.displayName}</a>
                            <div class="grow"></div>
                        </button>
                    </label>
                @endfor
            @endfor
        </div>

        <div class="text-xl my-4"><i class="fas fa-cog mr-2 text-margeblue"></i>Advanced Options</div>
        <div class="grid grid-cols-3 gap-4">
            @for (SearchFilters.SearchOption option : filters.searchOptions())
                <label class="flex items-center">
                    <button onclick="document.location='$unsafe{option.getUrl()}'" class="flex-1 py-2 rounded flex flex-col place-items-center has-[:checked]:bg-gray-100 has-[:checked]:text-slate-900 hover:bg-gray-50 text-margeblue">
                        @if (option.isSet())
                            <input type="checkbox" checked class="sr-only" aria-checked="true" />
                        @else
                            <input type="checkbox" class="sr-only" aria-checked="false" />
                        @endif
                        <i class="fas ${option.icon()} text-xl"></i>
                        <a tabindex="-1" href="$unsafe{option.getUrl()}" class="text-sm" href="#">${option.name()}</a>
                        <div class="grow"></div>
                    </button>
                </label>
            @endfor
        </div>
    </div>
</aside>