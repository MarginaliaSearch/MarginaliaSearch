@import nu.marginalia.search.svc.SearchSiteInfoService.*
@import nu.marginalia.search.model.UrlDetails
@import nu.marginalia.search.model.ResultsPage
@import nu.marginalia.model.idx.DocumentFlags
@param Docs docs

<!-- -->

<div class="flex flex-col space-y-4 my-4">

@if (docs.results().isEmpty())
    <div class="border dark:border-gray-600 rounded bg-white dark:bg-gray-800 dark:text-white flex flex-col overflow-hidden p-4 mx-4 text-gray-800 text-sm">
        The search engine doesn't index any documents from ${docs.domain()}
    </div>
@else
    <div class="border dark:border-gray-600 rounded bg-white dark:bg-gray-800 dark:text-white flex flex-col overflow-hidden p-4 mx-4 text-gray-800 text-sm">
        Showing documents from ${docs.domain()}
    </div>
@endif

@for (UrlDetails details : docs.results())
<div class="border dark:border-gray-600 rounded bg-white dark:bg-gray-800 dark:text-white flex flex-col overflow-hidden mx-4">
    <div class="flex grow justify-between items-start p-4">
        <div class="flex-1">
            <h2 class="text-xl text-gray-800 dark:text-white font-serif mr-4">
                <a href="${details.url.toString()}" rel="noopener noreferrer">$unsafe{details.displayTitle()}</a>
            </h2>
            <div class="text-sm mt-1 text-slate-800">
                <a class="text-liteblue dark:text-blue-200 underline  break-all" href="${details.url.toString()}"
                   rel="noopener noreferrer" tabindex="-1">$unsafe{details.displayUrl()}</a>
            </div>
        </div>
    </div>

    <p class="mt-2 text-sm text-slate-900 dark:text-gray-200 leading-relaxed mx-4 mb-4">
        $unsafe{details.displayDescription()}
    </p>

    <div class="flex text-xs space-x-2 p-2">
        <div class="grow"></div>
        @if (DocumentFlags.PlainText.isPresent(details.resultItem.encodedDocMetadata))
            <span class="px-1 bg-blue-100 text-blue-700 dark:border dark:border-blue-600 dark:text-blue-400 rounded">Plain text</span>
        @endif
        @if (DocumentFlags.GeneratorForum.isPresent(details.resultItem.encodedDocMetadata))
            <span class="px-1 bg-blue-100 text-blue-700 dark:border dark:border-blue-600 dark:text-blue-400 rounded">Forum</span>
        @endif
        @if (DocumentFlags.GeneratorWiki.isPresent(details.resultItem.encodedDocMetadata))
            <span class="px-1 bg-blue-100 text-blue-700 dark:border dark:border-blue-600 dark:text-blue-400 rounded">Wiki</span>
        @endif

        @if(details.isCookies())
            <span class="px-1 bg-yellow-100 text-yellow-700 dark:border dark:border-yellow-600 dark:text-yellow-400 dark:bg-black rounded" title="Contains Cookies">Cookies</span>
        @endif

        @if(details.isTracking())
            <span class="px-1 bg-yellow-100 text-yellow-700 dark:border dark:border-yellow-600 dark:text-yellow-400 dark:bg-black rounded" title="Uses tracking scripts">Track</span>
        @endif

        @if(details.isScripts())
            <span class="px-1 bg-yellow-100 text-yellow-700 dark:border dark:border-yellow-600 dark:text-yellow-400 dark:bg-black rounded" title="Contains JavaScript">JS</span>
        @endif

        @if(details.isAds())
            <span class="px-1 bg-red-100 text-red-700 dark:border dark:border-red-600 dark:text-red-400 dark:bg-black rounded" title="Contains adtech">Ads</span>
        @endif

        @if(details.isAffiliate())
            <span class="px-1 bg-red-100 text-red-700 dark:border dark:border-red-600 dark:text-red-400 dark:bg-black rounded" title="Contains Affiliate Link">Affiliate</span>
        @endif

    </div>
</div>
@endfor

<!-- Pagination -->
@if (docs.pages().size() > 1)
    <div class="mt-8 flex justify-center space-x-2 font-mono text-sm">
        @for(ResultsPage page : docs.pages())
            @if (page.current())
                <a href="?view=docs&page=${page.number()}" class="px-2 py-1 border dark:border-gray-600 border-gray-300 bg-gray-100 dark:bg-gray-900">${page.number()}</a>
            @else
                <a href="?view=docs&page=${page.number()}" class="px-2 py-1 bg-white dark:bg-gray-800 dark:text-white border dark:border-gray-600 border-gray-300 hover:bg-gray-100 dark:bg-gray-800 hover:bg-gray-900">${page.number()}</a>
            @endif
        @endfor
    </div>
@endif

</div>
