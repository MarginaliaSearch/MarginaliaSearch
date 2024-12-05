@import nu.marginalia.search.model.NavbarModel
@import nu.marginalia.search.model.SearchErrorMessageModel

@param SearchErrorMessageModel model
@param NavbarModel navbar

<!DOCTYPE html>
<html lang="en">

@template.part.head(title = "Marginalia Search - Error")

<body class="min-h-screen bg-gray-50 font-sans" >

@template.part.navbar(navbar = navbar)

<header class="border-b border-gray-300 bg-white">
    <div class="max-w-[1400px] mx-auto px-4 py-4">
        <div class="flex items-center">
            <h1 class="text-xl mr-8 font-serif">Marginalia Search</h1>
            @template.serp.part.searchform(query = model.parameters().query(), profile = model.parameters().profileStr(), filters = model.filters())
        </div>
    </div>
</header>

<div class="max-w-[1400px] mx-auto flex gap-6">

    <!-- Main content -->
    <main class="flex-1 p-4 max-w-2xl space-y-4">
        <div class="border rounded bg-white text-black text-m p-4">
            <div class="flex space-x-3  place-items-baseline">
                <i class="fa fa-circle-exclamation text-red-800"></i>
                <div class="grow">${model.errorTitle()}</div>
            </div>
            @if (!model.errorRest().isBlank())
            <p class="pt-5 text-gray-800 text-sm">
                ${model.errorRest()}
            </p>
            @endif
        </div>

    </main>

</div>

@template.part.footerLegal()

</body>
</html>