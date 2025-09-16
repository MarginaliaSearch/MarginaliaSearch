@import nu.marginalia.api.math.model.DictionaryEntry
@import nu.marginalia.language.model.LanguageDefinition
@import nu.marginalia.search.model.SearchParameters
@import nu.marginalia.search.model.NavbarModel
@import nu.marginalia.search.model.SearchFilters
@import nu.marginalia.api.math.model.DictionaryResponse
@import java.util.List
@import java.util.Map

@param SearchParameters parameters
@param DictionaryResponse result
@param NavbarModel navbar
@param Map<String, LanguageDefinition> languageDefinitions

!{SearchFilters filters = new SearchFilters(parameters);}

<!DOCTYPE html>
<html lang="en">

@template.part.head(title = "Marginalia Search - " +  parameters.query())

<body class="min-h-screen bg-bgblue dark:bg-gray-900 dark:text-white font-sans " >
@template.part.navbar(navbar = navbar)

<div>
<header class="border-b border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 shadow-md">
    <div class="max-w-[1400px] mx-auto px-4 py-2 sm:py-4">
        <div class="flex items-center">
            <div class="hidden sm:block md:w-32 md:mr-16 md:ml-16"><h1 class="text-md sm:text-xl mr-8 font-serif whitespace-nowrap"><a href="/">Marginalia Search</a></h1></div>
            <div class="w-full p-2 border-none backdrop-blur-sm">
                @template.serp.part.searchform(query = parameters.query(), profile = parameters.profileStr(), languageIsoCode = "en", filters = filters)
            </div>
            <div class="grow"></div>
            <button class="fixed bottom-10 right-5 sm:hidden text-sm bg-margeblue text-white p-4 rounded-xl active:text-slate-200" id="filter-button">
                <i class="fas fa-filter mr-3"></i>
                Filters
            </button>
            <button class="fixed bottom-30 right-5 sm:hidden text-sm bg-margeblue text-white p-4 rounded-xl active:text-slate-200" id="language-button">
                <i class="fas fa-filter mr-3"></i>
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
        <div class="border dark:border-gray-600 rounded p-4 bg-white dark:bg-gray-800">
            <div class="mb-4">
                <i class="fas fa-book text-margeblue dark:text-slate-200 mx-2"></i> ${result.word()}
            </div>

            @if (result.hasEntries())
                <table class="min-w-full divide-y divide-gray-200 dark:divide-gray-600">
                <thead>
                <tr class="bg-gray-700">
                    <th scope="col" class="px-2 py-1 text-left text-xs font-medium text-gray-500 dark:text-gray-100 uppercase tracking-wider">Type</th>
                    <th scope="col" class="px-2 py-2 text-left text-xs font-medium text-gray-500 dark:text-gray-100 uppercase tracking-wider">Definition</th>
                </tr>
                </thead>
                <tbody class="bg-white dark:bg-gray-800 divide-y divide-gray-200 dark:divide-gray-600 text-xs">
                @for (DictionaryEntry entry : result.entries())
                    <tr>
                        <td class="px-3 py-3">${entry.type()}</td>
                        <td class="px-3 py-3">${entry.definition()}</td>
                    </tr>
                @endfor
                </tbody>
            </table>
            @else
                <div>No definition found.</div>
            @endif
        </div>
    </main>
</div>

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