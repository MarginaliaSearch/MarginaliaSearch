@import nu.marginalia.WebsiteUrl
@import nu.marginalia.search.model.NavbarModel
@import nu.marginalia.search.model.SearchFilters
@import nu.marginalia.search.model.SearchProfile
@import nu.marginalia.search.svc.SearchFrontPageService.IndexModel
@import nu.marginalia.search.svc.SearchFrontPageService.NewsItem
@import nu.marginalia.search.svc.SearchFrontPageService.NewsItemCluster

@param NavbarModel navbar
@param WebsiteUrl websiteUrl
@param IndexModel model

<!DOCTYPE html>
<html lang="en">

@template.part.head(title = "Marginalia Search", allowIndexing = true)

<body class="min-h-screen bg-bgblue dark:bg-gray-900 dark:text-white font-sans " >

@template.part.navbar(navbar = navbar)

<header class="border-b border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 shadow-md">
    <div class="max-w-[1400px] mx-auto px-4 py-2 sm:py-4">
        <div class="flex items-center">
            <div class="hidden sm:block md:w-32 md:mr-16 md:ml-16"><h1 class="text-md sm:text-xl mr-8 font-serif whitespace-nowrap"><a href="/">Marginalia Search</a></h1></div>
            <div class="w-full p-2 border-none backdrop-blur-sm">
                @template.serp.part.searchform(query = "", profile = SearchProfile.NO_FILTER.filterId, filters = new SearchFilters(websiteUrl))
            </div>
        </div>
    </div>
</header>

@if (model.news().isEmpty())
<div class="max-w-7xl mx-auto flex flex-col space-y-4 fill-w">
    <div class="border border-gray-300 border-gray-100 dark:border-gray-600 dark:bg-gray-800 bg-white rounded p-2 m-4 ">
        <div class="text-slate-700 dark:text-white text-sm p-4">
        The old version of Marginalia Search remains available
        <a href="https://old-search.marginalia.nu/" class="underline text-liteblue dark:text-blue-200">here</a>.
        </div>
    </div>
    <div class="mx-auto px-8 flex flex-col sm:flex-row my-4 sm:space-x-2 space-y-2 sm:space-y-0 w-full md:w-auto items-center sm:items-stretch">
    <div class="flex flex-col items-center border border-gray-300 dark:border-gray-600 rounded overflow-hidden dark:bg-gray-800 bg-white p-8 sm:p-4 space-y-3 w-[300px] sm:w-64">
        <div><i class="fas fa-sailboat mx-2 text-margeblue dark:text-slate-200"></i>Explore the Web</div>
        <ul class="list-disc ml-8 sm:ml-6  text-slate-700 dark:text-white text-xs leading-5">
            <li>Prioritizes non-commercial content</li>
            <li>Tools for both search and discovery</li>
            <li>Find lost old websites</li>
        </ul>
    </div>

    <div class="flex flex-col items-center border border-gray-300 dark:border-gray-600 rounded overflow-hidden dark:bg-gray-800 bg-white p-8 sm:p-4 space-y-3 w-[300px] sm:w-64">
        <div><i class="fas fa-hand-holding-hand mx-2 text-margeblue dark:text-slate-200"></i>Open Source</div>
        <ul class="list-disc ml-8 sm:ml-6  text-slate-700 dark:text-white text-xs leading-5">
           <li>Custom index and crawler software</li>
           <li>Simple technology, no AI</li>
           <li>AGPL license</li>
        </ul>
        <div class="flex pt-4 gap-2 flex-col md:flex-row">
        <div class="text-xs text-liteblue dark:text-blue-200">
            <i class="fa-brands fa-github"></i>
            <a href="https://git.marginalia.nu/" class="underline">Git Repository</a>
        </div>
        <div class="text-xs text-liteblue dark:text-blue-200">
            <i class="fa-brands fa-discord"></i>
            <a href="https://discord.gg/GgpkrVbF" class="underline">Project Discord</a>
        </div>
        </div>
    </div>

    <div class="flex flex-col items-center border border-gray-300 dark:border-gray-600 rounded overflow-hidden dark:bg-gray-800 bg-white p-8 sm:p-4 space-y-3 w-[300px] sm:w-64">
        <div><i class="fas fa-lock mx-2 text-margeblue dark:text-slate-200"></i> Privacy by default</div>
        <ul class="list-disc ml-8 sm:ml-6 text-slate-700 dark:text-white text-xs leading-5">
            <li>Filter out tracking </li>
            <li>No data shared with 3rd parties</li>
            <li>No long-term retention of IPs</li>
        </ul>
        <div class="text-xs text-liteblue dark:text-blue-200 pt-4">
            <i class="fas fa-link"></i>
            <a href="https://about.marginalia-search.com/article/privacy/" class="underline">Privacy Statement</a>
        </div>
    </div>

    </div>

</div>
@else
<div class="max-w-7xl mx-auto flex flex-col space-y-4 fill-w m-4">
    <div class="my-4 text-black dark:text-white font-serif text-xl mx-8 place-items-baseline flex">
        Subscriptions
        <div class="grow"></div>
        <a class="text-sm font-sans" href="/export-opml">
            <i class="fas fa-download mr-2"></i>
            Export as OPML
        </a>
    </div>
    @for (NewsItemCluster cluster : model.news())
        !{NewsItem item = cluster.first();}

        <div class="border dark:border-gray-600 rounded bg-white dark:bg-gray-800 flex flex-col overflow-hidden mx-4 space-y-2">
            <a class="flex space-x-2 flex-row place-items-baseline bg-margeblue text-white p-2 text-md" href="/site/${item.domain()}">
                <i class="fas fa-globe mr-2"></i>
                <span>${item.domain()}</span>
            </a>

            <div class="text-black dark:text-white flex place-items-middle  mx-2">
                <div class="flex flex-col space-y-1">
                    <a class="text-l text-liteblue dark:text-blue-200 visited:text-purple-800 dark:visited:text-purple-200" href="${item.url()}" rel="ugc external nofollow">${item.title()}</a>
                </div>
                <div class="grow"></div>
                <div class="flex text-xs whitespace-nowrap">
                    ${item.date().substring(0, 10)}
                </div>
            </div>
            <div class="text-sm text-gray-800 dark:text-gray-100 mx-2">$unsafe{item.description()}</div>

            @for (var remainder : cluster.rest())
                <div class="flex flex-col space-y-1 dark:border-gray-600 border-t pt-2 mx-2">
                    <div class="text-black dark:text-white flex place-items-middle">
                        <a class="text-sm text-liteblue dark:text-blue-200 visited:text-purple-800 dark:visited:text-purple-200" href="${remainder.url()}" rel="ugc external nofollow">${remainder.title()}</a>
                        <div class="grow"></div>
                        <div class="flex text-xs whitespace-nowrap">
                            ${remainder.date().substring(0, 10)}
                        </div>
                    </div>
                </div>
            @endfor
            <div class="pt-2"></div>
        </div>
    @endfor
    <div class="mx-4 text-sm text-gray-800 dark:text-gray-200">Last updated: ${model.refreshDate()}</div>
</div>
@endif

@template.part.footerLegal()
<script lang="javascript" src="js/typeahead.js"></script>
</body>
</html>
