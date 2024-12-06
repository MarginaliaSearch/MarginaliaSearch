@import nu.marginalia.WebsiteUrl
@import nu.marginalia.search.model.NavbarModel
@import nu.marginalia.search.model.SearchFilters
@import nu.marginalia.search.model.SearchProfile

@param NavbarModel navbar
@param WebsiteUrl websiteUrl
<!DOCTYPE html>
<html lang="en">

@template.part.head(title = "Marginalia Search", allowIndexing = true)

<body class="min-h-screen bg-slate-100 font-sans" >

@template.part.navbar(navbar = navbar)

<header class="border-b border-gray-300 bg-white">
    <div class="max-w-[1400px] mx-auto px-4 py-2 sm:py-4">
        <div class="flex items-center">
            <div class="hidden sm:block md:w-32 md:mr-16 md:ml-16"><h1 class="text-md sm:text-xl mr-8 font-serif whitespace-nowrap"><a href="/">Marginalia Search</a></h1></div>
            <div class="static bottom-0 left-0 w-full p-5 sm:p-0 border-none backdrop-blur-sm bg-white">
            @template.serp.part.searchform(query = "", profile = SearchProfile.NO_FILTER.filterId, filters = new SearchFilters(websiteUrl))
            </div>
        </div>
    </div>
</header>

<div class="max-w-1000 mx-auto flex flex-col space-y-4 fill-w">
    <div class="border bg-white rounded p-2 m-4">
        <div class="text-slate-700 text-sm p-4">
        You've stumbled onto a beta version of the redesign of the search engine's UI.  Feel free to explore, but
        beware that things may not quite be polished.
        </div>
    </div>
    <div class="mx-auto flex flex-col sm:flex-row my-4 sm:space-x-2 space-y-2 sm:space-y-0 w-full md:w-auto px-2">
    <div class="flex flex-col border rounded overflow-hidden bg-white p-6 space-y-3">
        <div><i class="fas fa-sailboat mx-2 text-margeblue"></i>Explore the Web</div>
        <ul class="list-disc ml-6 text-slate-700 text-xs leading-5">
            <li>Prioritizes non-commercial content</li>
            <li>Tools for both search and discovery</li>
            <li>Find lost old websites</li>
        </ul>
    </div>

    <div class="flex flex-col border rounded overflow-hidden bg-white p-6  space-y-3 ">
        <div><i class="fas fa-hand-holding-hand mx-2 text-margeblue"></i>Open Source</div>
        <ul class="list-disc ml-6 text-slate-700 text-xs leading-5">
           <li>AGPL license</li>
           <li>Custom index software</li>
           <li>Custom crawler software</li>
        </ul>
        <div class="text-xs text-liteblue pt-4">
            <i class="fas fa-link"></i>
            <a href="https://git.marginalia.nu/" class="underline">Git Repository</a>
        </div>
    </div>

    <div class="flex flex-col border rounded overflow-hidden bg-white p-6  space-y-3 ">
        <div><i class="fas fa-lock mx-2 text-margeblue"></i> Privacy by default</div>
        <ul class="list-disc ml-6 text-slate-700 text-xs leading-5">
            <li>Filter out tracking and adtech</li>
            <li>No user or search data shared with 3rd parties</li>
            <li>No long-term retention of queries or IP addresses</li>
        </ul>
        <div class="text-xs text-liteblue pt-4">
            <i class="fas fa-link"></i>
            <a href="#" class="underline">Privacy Statement</a> <!-- TODO -->
        </div>
    </div>
    </div>

</div>

@template.part.footerLegal()
<script lang="javascript" src="js/typeahead.js"></script>
</body>
</html>
