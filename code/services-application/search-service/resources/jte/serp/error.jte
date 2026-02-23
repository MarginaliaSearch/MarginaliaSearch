@import nu.marginalia.search.model.NavbarModel
@import nu.marginalia.search.model.SearchErrorMessageModel

@param SearchErrorMessageModel model
@param NavbarModel navbar

<!DOCTYPE html>
<html lang="en">

@template.part.head(title = "Marginalia Search - Error")

<body class="min-h-screen bg-bgblue dark:bg-gray-900 dark:text-white font-sans " >

@template.part.navbar(navbar = navbar)

<header class="border-b border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 shadow-md">
    <div class="max-w-[1400px] mx-auto px-4 py-4">
        <div class="flex items-center">
            <h1 class="text-md sm:text-xl mr-8 font-serif whitespace-nowrap"><a href="/">Marginalia Search</a></h1>
            @template.serp.part.searchform(query = model.parameters().query(), scrapeStopToken = model.parameters().sst(), profile = model.parameters().profileStr(), filters = model.filters(), languageIsoCode = "en", page = 1, requestMethod = "GET", displayUrl = null)
        </div>
    </div>
</header>

<div class="max-w-[1400px] mx-auto flex gap-6">

    <!-- Main content -->
    <main class="flex-1 p-4 max-w-2xl space-y-4">
        <div class="border border-gray-300 dark:border-gray-600 rounded bg-white text-black dark:bg-gray-800 dark:text-white text-m p-4">
            <div class="flex space-x-3  place-items-baseline">
                <i class="fa fa-circle-exclamation text-red-800"></i>
                <div class="grow">${model.errorTitle()}</div>
            </div>
            @if (!model.errorRest().isBlank())
            <p class="pt-5 text-gray-800 dark:text-gray-200 text-sm">
                ${model.errorRest()}
            </p>
            @endif
        </div>

    </main>

</div>

@template.part.footerLegal()

</body>
</html>