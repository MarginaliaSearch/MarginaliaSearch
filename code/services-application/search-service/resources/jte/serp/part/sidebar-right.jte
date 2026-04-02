@import nu.marginalia.search.model.SearchFilters
@import java.util.List

@param SearchFilters filters

<aside class="peer-checked:md:w-64 py-4 lg:mx-4 shrink-0 hidden md:block finepointer:block">
    <input id="toggle-settings" name="toggle-settings" tabindex="-1" type="checkbox" aria-checked="true" class="peer hidden" />
    <label title="Toggle Advanced Settings" for="toggle-settings" class="lg:hidden peer-checked:hidden bg-white dark:bg-gray-800 flex-1 py-2 pl-2 rounded flex space-x-2 dark:peer-has-[:checked]:bg-gray-950 peer-has-[:checked]:bg-gray-300 peer-has-[:checked]:text-slate-900 dark:peer-has-[:checked]:text-slate-100 hover:bg-gray-50 dark:hover:bg-gray-950 dark:border dark:border-gray-600 text-margeblue dark:text-slate-200 outline-1 active:outline">
        <i class="fas fa-cog mr-3"></i>
    </label>

    <div class="space-y-6 sticky top-4 hidden peer-checked:block lg:block">
        <div class="bg-white dark:bg-gray-800 p-2 border dark:border-gray-600 border-gray-300">

            <h2 class="font-medium mb-3 flex items-center font-serif hidden md:block gap-1">
                <i class="fas fa-cog text-sm mr-2"></i>
                Advanced Settings
            </h2>
            <div class="space-y-2">
                @for (SearchFilters.SearchOption option : filters.searchOptions())
                    <label class="flex items-center">
                            <button title="${option.name()}" onclick="document.location='$unsafe{option.getUrl()}'" class="flex-1 py-2 rounded flex space-x-2 dark:has-[:checked]:bg-gray-950 has-[:checked]:bg-gray-300 has-[:checked]:text-slate-900 dark:has-[:checked]:text-slate-100 hover:bg-gray-50 dark:hover:bg-gray-950 bg-white dark:bg-gray-900 dark:border dark:border-gray-600 text-margeblue dark:text-slate-200 outline-1 active:outline">
                                @if (option.isSet())
                                    <input tabindex="-1" type="checkbox" checked class="hidden" aria-checked="true" />
                                @else
                                    <input tabindex="-1" type="checkbox" class="hidden" aria-checked="false" />
                                @endif


                                <a href="$unsafe{option.getUrl()}" class="flex place-items-baseline text-sm md:text-xs lg:text-sm" href="#">
                                    @if (option.isSet())
                                        <i class="fas fa-check-circle mr-3"></i>
                                    @else
                                        <i class="far fa-circle mr-3"></i>
                                    @endif

                                    <span class="hidden md:inline">${option.name()}</span>
                                </a>
                            </button>
                    </label>
                @endfor
                <div class="[&:not(:last-child)]:border-b hidden md:block dark:border-gray-800 dark:pb-2"></div>
                <label class="flex items-center">
                    <button title="Language" id="language-button-sidebar"  class="flex-1 py-2 pl-2 rounded flex space-x-2 dark:has-[:checked]:bg-gray-950 has-[:checked]:bg-gray-300 has-[:checked]:text-slate-900 dark:has-[:checked]:text-slate-100 hover:bg-gray-50 dark:hover:bg-gray-950 bg-white dark:bg-gray-900 dark:border dark:border-gray-600 text-margeblue dark:text-slate-200 outline-1 active:outline">
                        <span></span>
                        <span class="text-sm md:text-xs lg:text-sm">
                            <i class="fas fa-globe-europe mr-3"></i>
                            <span class="hidden md:inline">Language</span>
                        </span>
                    </button>
                </label>
                <div class="[&:not(:last-child)]:border-b hidden md:block dark:border-gray-800 dark:pb-2"></div>
                <label class="flex items-center lg:hidden">
                    <label title="Close" for="toggle-settings" class="flex-1 py-2 pl-2 rounded flex space-x-2 dark:has-[:checked]:bg-gray-950 has-[:checked]:bg-gray-300 has-[:checked]:text-slate-900 dark:has-[:checked]:text-slate-100 hover:bg-gray-50 dark:hover:bg-gray-950 bg-white dark:bg-gray-900 dark:border dark:border-gray-600 text-margeblue dark:text-slate-200 outline-1 active:outline">
                        <span></span>
                        <span class="text-sm md:text-xs lg:text-sm">
                            <i class="fas fa-close mr-3"></i>
                            <span class="hidden md:inline">Close</span>
                        </span>
                    </label>
                </label>
            </div>
        </div>
    </div>
</aside>
