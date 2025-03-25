@import nu.marginalia.search.model.GroupedUrlDetails
@import nu.marginalia.search.model.UrlDetails
@import nu.marginalia.search.model.ResultsPage
@import nu.marginalia.search.svc.SearchSiteInfoService.*

@param Backlinks backlinks

<div class="flex flex-col space-y-4 my-4  w-full">

@if (backlinks.results().isEmpty())
<div class="border border-gray-300 dark:border-gray-600 rounded bg-white dark:bg-gray-800 dark:text-white flex flex-col overflow-hidden p-4 mx-4 text-gray-800 text-sm ">
    The search engine isn't aware of any backlinks to ${backlinks.domain()}!
</div>
@else
<div class="border border-gray-300 dark:border-gray-600 rounded bg-white dark:bg-gray-800 dark:text-white flex flex-col overflow-hidden p-4 mx-4 text-gray-800 text-sm">
    Showing documents linking to ${backlinks.domain()}
</div>
@endif

@for (GroupedUrlDetails group : backlinks.results())
<div class="border dark:border-gray-600 border-gray-300 rounded bg-white dark:bg-gray-800 dark:text-white flex flex-col overflow-hidden mx-4">
    <div class="flex space-x-2 flex-row place-items-baseline bg-margeblue text-white p-2  text-md">
        <span class="fas fa-globe"></span>
        <a href="/site/${group.domain().toString()}">${group.domain().toString()}</a>
        <span class="grow"></span>
        <a rel="nofollow noopener external" href="${group.domain().toRootUrlHttps().toString()}" class="fa-solid fa-arrow-up-right-from-square" ></a>
    </div>
    
    @for (UrlDetails details : group.urlDetails())
        <div class="p-2 font-medium text-sm text-gray-800 dark:text-white mx-2 mt-2">$unsafe{details.displayTitle()}</div>
        <div class="p-2 mx-2 text-gray-700 dark:text-gray-300  text-sm">
            $unsafe{details.displayDescription()}
        </div>
        <div class="p-2 text-sm border-b dark:border-gray-600 pb-6">
            <a rel="external noopener nofollow" href="${details.url.toString()}" class="mx-3 text-liteblue dark:text-blue-200 flex space-x-2 place-items-baseline hyphens-auto">
                <i class="fa fa-link"></i>
                <span class="grow break-all">$unsafe{details.displayUrl()}</span>
            </a>
        </div>
    @endfor
</div>

@endfor

<!-- Pagination -->
@if (backlinks.pages().size() > 1)
    <div class="mt-8 flex justify-center space-x-2 font-mono text-sm">
        @for(ResultsPage page : backlinks.pages())
            @if (page.current())
                <a href="?view=links&page=${page.number()}" class="px-2 py-1 border dark:border-gray-600 border-gray-300 bg-gray-100 dark:bg-gray-900">${page.number()}</a>
            @else
                <a href="?view=links&page=${page.number()}" class="px-2 py-1 bg-white border dark:border-gray-600 border-gray-300 hover:bg-gray-100 dark:bg-gray-800 hover:bg-gray-900">${page.number()}</a>
            @endif
        @endfor
    </div>
@endif
</div>


<!-- -->
