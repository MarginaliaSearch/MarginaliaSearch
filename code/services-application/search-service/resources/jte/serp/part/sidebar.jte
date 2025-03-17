@import nu.marginalia.search.model.SearchFilters
@import java.util.List

@param SearchFilters filters

<aside class="md:w-64 py-4 shrink-0 hidden md:block finepointer:block">
    <div class="space-y-6 sticky top-4">
        <div class="bg-white dark:bg-gray-800 p-4 border dark:border-gray-600 border-gray-300">
            <h2 class="font-medium mb-3 flex items-center font-serif hidden md:block">
                <i class="fas fa-filter text-sm mr-2"></i> Filter
            </h2>
            <div class="space-y-2">
                @for (List<SearchFilters.Filter> filterGroup : filters.getFilterGroups())
                    @for (SearchFilters.Filter filter : filterGroup)
                        <label class="flex items-center">
                            <button title="${filter.displayName}" onclick="document.location='$unsafe{filter.url}'" class="flex-1 py-2 pl-2 rounded flex space-x-2 dark:has-[:checked]:bg-gray-950 has-[:checked]:bg-gray-100 has-[:checked]:text-slate-900 dark:has-[:checked]:text-slate-100 hover:bg-gray-50 dark:hover:bg-gray-950 bg-white dark:bg-gray-900 dark:border dark:border-gray-600 text-margeblue dark:text-slate-200 outline-1 active:outline">
                                @if (filter.current)
                                    <input type="checkbox" checked class="sr-only" aria-checked="true" />
                                @else
                                    <input type="checkbox" class="sr-only" aria-checked="false" />
                                @endif


                                <a tabindex="-1" href="$unsafe{filter.url}" class="text-sm" href="#">
                                    <i class="fas ${filter.icon} text-sm mr-3"></i>
                                    <span class="hidden md:inline">${filter.displayName}</span></a>
                            </button>
                        </label>
                @endfor
                    <div class="[&:not(:last-child)]:border-b hidden md:block dark:border-gray-800 dark:pb-2"></div>
                @endfor
            </div>
        </div>
        <div class="bg-white dark:bg-gray-800 p-4 border dark:border-gray-600 border-gray-300">
            <h2 class="font-medium mb-3 flex items-center font-serif hidden md:block">
                <i class="fas fa-cog text-sm mr-2"></i> Advanced Settings
            </h2>
            <div class="space-y-2">
                @for (SearchFilters.SearchOption option : filters.searchOptions())
                    <label class="flex items-center">
                            <button title="${option.name()}" onclick="document.location='$unsafe{option.getUrl()}'" class="flex-1 py-2 pl-2 rounded flex space-x-2 dark:has-[:checked]:bg-gray-950 has-[:checked]:bg-gray-100 has-[:checked]:text-slate-900 dark:has-[:checked]:text-slate-100 hover:bg-gray-50 dark:hover:bg-gray-950 bg-white dark:bg-gray-900 dark:border dark:border-gray-600 text-margeblue dark:text-slate-200 outline-1 active:outline">
                                @if (option.isSet())
                                    <input type="checkbox" checked class="sr-only" aria-checked="true" />
                                @else
                                    <input type="checkbox" class="sr-only" aria-checked="false" />
                                @endif


                                <a href="$unsafe{option.getUrl()}" class="text-sm" href="#">
                                    <i class="fas ${option.icon()} text-sm mr-3"></i>
                                    <span class="hidden md:inline">${option.name()}</span>
                                </a>
                            </button>
                    </label>
                @endfor
            </div>
        </div>
    </div>
</aside>
