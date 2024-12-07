@import nu.marginalia.model.idx.DocumentFlags
@import nu.marginalia.search.command.SearchParameters
@import nu.marginalia.search.model.ClusteredUrlDetails
@import nu.marginalia.search.model.UrlDetails
@param ClusteredUrlDetails result
@param SearchParameters parameters
@param boolean domainSearch

<div class="bg-white p-4 border border-gray-300 flex rounded">
    <div class="flex flex-col grow">
        <div class="flex">
            <div class="flex flex-col grow" >
                <div class="flex flex-row space-x-2 place-items-center">
                    <div class="flex-0">
                        @template.serp.part.matchogram(mask = result.first.positionsMask)
                    </div>
                    <div class="flex grow justify-between items-start">
                        <div class="flex-1">
                            <h2 class="text-md sm:text-xl ${result.colorScheme.textColor} font-serif mr-4 break-words hyphens-auto">
                                <a href="${result.first.url.toString()}" rel="noopener noreferrer">$unsafe{result.first.titleHyphenHints()}</a>
                            </h2>

                            <div class="text-sm mt-1 text-gray-600">
                                <a class="text-liteblue underline break-all" href="${result.first.url.toString()}"
                                   rel="noopener noreferrer" tabindex="-1">$unsafe{result.first.urlHyphenHints()}</a>
                            </div>
                        </div>
                    </div>
                </div>


                <div class="overflow-auto flex-1">
                <p class="mt-2 text-sm text-black leading-relaxed break-words">
                    $unsafe{result.first.descriptionHyphenHints()}
                </p>
                </div>

            </div>
            @if (!domainSearch)
                <div class="flex flex-col ml-5 content-center items-center space-y-2">
                    <a href="/site/${result.getDomain().toString()}" class="p-1.5 text-gray-600 hover:text-gray-900 rounded" title="About this domain">
                        <i class="fas fa-info text-sm"></i>
                    </a>
                    <a href="https://web.archive.org/web/*/${result.first.url.toString()}"
                       class="p-1.5 text-gray-600 hover:text-gray-900 rounded"
                       title="Wayback Machine">
                        <i class="fas fa-clock-rotate-left text-sm"></i>
                    </a>
                </div>
            @endif
        </div>
        @if (result.hasMultiple() &&  !domainSearch)
            <div class="flex mt-2 text-sm flex flex-col space-y-2">
                <p class="text-black ${result.colorScheme.backgroundColor}  p-1 rounded break-words hyphens-auto">Also from ${result.getDomain().toString()}:</p>

                <ul class="pl-2 mt-2 underline text-liteblue">
                    @for(UrlDetails item : result.rest)
                        <li class="-indent-4 pl-4 mb-1 break-words hyphens-auto">
                            <a href="${item.url.toString()}" rel="noopener noreferrer">$unsafe{item.titleHyphenHints()}</a>
                        </li>
                    @endfor
                </ul>
            </div>
        @endif

        <span class="flex space-x-1 flex-row text-xs mt-4">
            @if (!domainSearch && result.remainingCount() > 0)
                <span>
                <a class="text-liteblue underline" href="${parameters.renderUrlWithSiteFocus(result.getDomain())}">${result.remainingCount()} more</a>
                </span>
            @endif

            <div class="grow"></div>
            @if (DocumentFlags.PlainText.isPresent(result.getFirst().resultItem.encodedDocMetadata))
                <span class="px-1 bg-blue-100 text-blue-700 rounded">Plain text</span>
            @endif
            @if (DocumentFlags.GeneratorForum.isPresent(result.getFirst().resultItem.encodedDocMetadata))
                <span class="px-1 bg-blue-100 text-blue-700 rounded">Forum</span>
            @endif
            @if (DocumentFlags.GeneratorWiki.isPresent(result.getFirst().resultItem.encodedDocMetadata))
                <span class="px-1 bg-blue-100 text-blue-700 rounded">Wiki</span>
            @endif

            @if(result.getFirst().isCookies())
                <span class="px-1 bg-yellow-100 text-yellow-700 rounded" title="Contains Cookies">Cookies</span>
            @endif

            @if(result.getFirst().isTracking())
                <span class="px-1 bg-yellow-100 text-yellow-700 rounded" title="Uses tracking scripts">Track</span>
            @endif

            @if(result.getFirst().isScripts())
                <span class="px-1 bg-yellow-100 text-yellow-700 rounded" title="Contains JavaScript">JS</span>
            @endif

            @if(result.getFirst().isAds())
                <span class="px-1 bg-red-100 text-red-700 rounded" title="Contains adtech">Ads</span>
            @endif

            @if(result.getFirst().isAffiliate())
                <span class="px-1 bg-red-100 text-red-700 rounded" title="Contains Affiliate Link">Affiliate</span>
            @endif

        </span>
    </div>

</div>