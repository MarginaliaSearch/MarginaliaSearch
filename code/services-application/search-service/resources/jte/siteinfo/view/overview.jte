@import nu.marginalia.db.DbDomainQueries
@import nu.marginalia.search.svc.SearchSiteInfoService
@import nu.marginalia.search.svc.SearchSiteInfoService.*
@import nu.marginalia.search.model.UrlDetails

@param SiteInfoWithContext siteInfo

<!-- Main content -->

<div class="flex-1 p-4 space-y-4 mx-auto w-full md:w-auto">
    <div class="flex border border-gray-300  dark:border-gray-600 rounded bg-white dark:bg-gray-800 flex-col space-y-4 pb-4 overflow-hidden md:max-w-lg" >
        <div class="flex place-items-center space-x-2 p-2 text-md border-b dark:border-gray-600 bg-margeblue text-white">
            <img src="/site/${siteInfo.domain()}/favicon" style="width: 16px; height: 16px; vertical-align: center">
            <span>${siteInfo.domain()}</span>
            <div class="grow">
            </div>
            <a href="https://web.archive.org/web/*/${siteInfo.domain()}"
               class="p-1.5 text-white px-4"
               title="Wayback Machine">
                <i class="fas fa-clock-rotate-left text-sm"></i>
            </a>
            <a title="Visit ${siteInfo.domain()}" rel="nofollow noopener external" href="${siteInfo.siteUrl()}" class="fa-solid fa-arrow-up-right-from-square" ></a>
        </div>

        @if (siteInfo.hasScreenshot())
            <a class="mx-3 " tabindex="-1" rel="nofollow noopener external" href="${siteInfo.siteUrl()}">
                <img class="border dark:border-gray-600 shadow-inner" src="/screenshot/${siteInfo.domainId()}" alt="Screenshot of ${siteInfo.domain()}">
            </a>
        @endif

        @if (siteInfo.hasFeed())
            <div class="mx-3 flex place-items-baseline space-x-2 p-2 bg-gray-100 dark:bg-gray-600 rounded">
                <i class="fas fa-rss text-orange-500"></i>
                <span class="grow">Feed</span>

                <form method="post" action="/site/${siteInfo.domain()}/subscribe">
                @if (siteInfo.isSubscribed())
                    <button type="submit">
                    <i class="fa-solid fa-bookmark mr-2 text-blue-600 hover:text-blue-800 dark:text-blue-100 dark:hover:text-blue-400 cursor-pointer"
                        title="Unsubscribe from the front page"></i>
                    </button>
                @else
                    <button type="submit">
                        <i class="fa-regular fa-bookmark mr-2 text-gray-600 hover:text-blue-800 dark:text-gray-100 dark:hover:text-gray-400 cursor-pointer"
                           title="Add content to this feed to the front page"></i>
                    </button>
                @endif
                </form>
            </div>

            <dl class="mx-3 text-gray-800 dark:text-white">
                @for (SearchSiteInfoService.FeedItem item : siteInfo.feed().items())
                    <dt class="ml-2 flex space-x-4">
                        <a class="grow underline text-liteblue dark:text-blue-200 text-sm" rel="noopener nofollow external ugc" href="${item.url()}">
                            ${item.title()} @if (item.title().isBlank()) [No Title] @endif
                        </a>
                        <span class="whitespace-nowrap">${item.pubDay()}</span>
                    </dt>
                    <dd class="ml-6 text-sm mb-4">${item.description()}</dd>
                @endfor
            </dl>

        @endif

        @if (siteInfo.hasSamples() && !siteInfo.hasFeed())

            <div class="mx-3 flex place-items-baseline space-x-2 p-2 bg-gray-100 dark:bg-gray-600 rounded">
                <i class="fas fa-magnifying-glass"></i>
                <span>Sample</span>
            </div>

            <dl class="mx-3 text-gray-800 dark:text-white">
                @for (UrlDetails item : siteInfo.samples())
                    <dt class="ml-2">
                        <a class="underline text-liteblue dark:text-blue-200 text-sm" rel="noopener nofollow external ugc" href="${item.url.toString()}">${item.title}</a>
                    </dt>
                    <dd class="ml-6 text-sm mb-4">${item.description}</dd>
                @endfor
            </dl>

        @endif

        @if (siteInfo.domainInformation().isUnknownDomain())
            <div class="mx-3 flex place-items-baseline space-x-2 p-2 bg-gray-100 dark:bg-gray-600 rounded">
                <i class="fa-regular fa-circle-question"></i>
                <span>Unknown Domain</span>
            </div>

            <div class="mx-5 flex flex-col space-y-2">
                <p>This website is not known to the search engine.</p>

                <p>To submit the website for crawling, follow <a class="text-liteblue dark:text-blue-200 underline"
                                                                 rel="noopener noreferrer"
                                                                 target="_blank"
                                                                 href="https://github.com/MarginaliaSearch/submit-site-to-marginalia-search">these instructions</a>.</p>
            </div>

        @endif

        @if (siteInfo.domainInformation().isBlacklisted())
            <div class="mx-3 flex place-items-baseline space-x-2 p-2 bg-gray-100 dark:bg-gray-600 rounded">
                <i class="fas fa-ban"></i>
                <span>Blacklisted</span>
            </div>
            <div class="mx-5 flex flex-col space-y-2">
                <p>This website is <em>blacklisted</em>. This excludes it from crawling and indexing.</p>

                <p>This is usually because of some form of misbehavior on the webmaster's end,
                    either annoying search engine spam, or tasteless content bad faith content.</p>

                <p>Sometimes this is in error.  Blacklisting can be appealed using
                    the <a class="underline text-liteblue dark:text-blue-200" href="?view=report">Report Form</a>.
                </p>
                <p>In the interest of transparency, the full list of blacklisted domains is periodically
                    published at
                    <a class="underline text-liteblue dark:text-blue-200" href="https://downloads.marginalia.nu/exports/">https://downloads.marginalia.nu/exports/</a>.
                </p>
            </div>
        @endif

        @if (siteInfo.domainInformation().isSuggestForCrawling() && siteInfo.domainInformation().getNodeAffinity() < 0)
            <form method="POST"
                  action="/site/suggest/">
                <div class="max-w-md mx-auto p-6 bg-white shadow border dark:border-gray-600 border-gray-200  mx-1 flex space-y-4 flex-col">
                    <div class="text-sm text-gray-600 mb-4">
                        This website is not queued for crawling. If you would like it to be crawled,
                        use the checkbox and button below.
                    </div>

                    <input type="hidden" name="id" value="${siteInfo.domainId()}" />

                    <div class="flex items-center space-x-2">
                        <input type="checkbox"
                               id="nomisclick"
                               name="nomisclick"
                               class="h-4 w-4 rounded border-gray-300 text-margeblue dark:text-slate-200 focus:margeblue" />
                        <label for="nomisclick" class="text-sm text-gray-700">
                            This is not a mis-click
                        </label>
                    </div>

                    <button type="submit"
                            class="place-items-baseline space-x-4 w-full flex py-2 px-4 border dark:border-gray-600 border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-margeblue focus:outline-none focus:ring-2 focus:ring-offset-2 focus:margeblue">
                        <i class="fas fa-plus"></i>
                        <span>Add ${siteInfo.domain()} to queue</span>
                    </button>
                </div>
            </form>
        @endif


        @if (!siteInfo.siblingDomains().isEmpty())
            <div class="mx-3 flex place-items-baseline space-x-2 p-2 bg-gray-100 dark:bg-gray-600 rounded">
                <i class="fas fa-globe"></i>
                <span>Related Subdomains</span>
            </div>

            <table class="min-w-full divide-y divide-gray-200 dark:divide-gray-600 mx-4">
                <thead>
                <tr class="bg-gray-50 dark:bg-gray-700">
                    <th scope="col" class="px-2 py-2 text-left text-xs font-medium text-gray-500 dark:text-gray-100 uppercase tracking-wider">Domain Name</th>
                </tr>
                </thead>
                <tbody class="bg-white dark:bg-gray-800 divide-y divide-gray-200 dark:divide-gray-600 text-xs">
                @for (DbDomainQueries.DomainWithNode sibling : siteInfo.siblingDomains())
                    <tr>
                        <td class="px-3 py-6 md:py-3 whitespace-nowrap">
                            <a class="text-liteblue dark:text-blue-200" href="/site/${sibling.domain().toString()}">${sibling.domain().toString()}</a>

                            @if (!sibling.isIndexed())
                                <i class="ml-1 fa-regular fa-question-circle text-gray-400 dark:text-gray-600 text-xs" title="Not indexed"></i>
                            @endif
                        </td>
                    </tr>
                @endfor
                </tbody>
            </table>
        @endif


        @if (siteInfo.isKnown())
            <div class="mx-3 flex place-items-baseline space-x-2 p-2 bg-gray-100 dark:bg-gray-600 rounded">
                <i class="fas fa-chart-simple"></i>
                <span>Crawl Statistics</span>
            </div>

            <div class="grid grid-cols-2 md:grid-cols-3 gap-4 mx-6 sm:mx-8">
                <div>
                    <h3 class="text-sm text-gray-500 dark:text-gray-300">Pages Known</h3>
                    <p class="text-xl font-medium">${siteInfo.domainInformation().getPagesKnown()}</p>
                </div>
                <div>
                    <h3 class="text-sm text-gray-500 dark:text-gray-300">Pages Fetched</h3>
                    <p class="text-xl font-medium">${siteInfo.domainInformation().getPagesFetched()}</p>
                </div>
                <div>
                    <h3 class="text-sm text-gray-500 dark:text-gray-300">Pages Indexed</h3>
                    <p class="text-xl font-medium">${siteInfo.domainInformation().getPagesIndexed()}</p>
                </div>
                <div>
                    <h3 class="text-sm text-gray-500 dark:text-gray-300">Incoming Links</h3>
                    <p class="text-xl font-medium">${siteInfo.domainInformation().getIncomingLinks()}</p>
                </div>
                <div>
                    <h3 class="text-sm text-gray-500 dark:text-gray-300">Outbound Links</h3>
                    <p class="text-xl font-medium ">${siteInfo.domainInformation().getOutboundLinks()}</p>
                </div>
                <div title="Which index partition the domain is indexed by">
                    <h3 class="text-sm text-gray-500 dark:text-gray-300">Node Affinity</h3>
                    <p class="text-xl font-medium">${siteInfo.domainInformation().getNodeAffinity()}</p>
                </div>
            </div>

            @if (siteInfo.domainInformation().getPagesKnown() >= 5_000_000)
            <div class="mx-5 text-xs text-gray-600">
                This website is very large, and the system can not accurately report the number of crawled
                and indexed documents without affecting performance.
            </div>
            @endif


            <div class="mx-3 flex place-items-baseline space-x-2 p-2 bg-gray-100 dark:bg-gray-600 rounded">
                <i class="fas fa-network-wired"></i>
                <span>Network Details</span>
            </div>

            <div class="grid grid-cols-2 grid-cols-2 gap-2 mx-6 sm:mx-8">
                <div>
                    <h3 class="text-sm font-medium text-gray-500 dark:text-gray-300">IP Address</h3>
                    <p class="text-lg font-mono">${siteInfo.domainInformation().getIp()}</p>
                    <p class="text-sm text-gray-600 dark:text-gray-400">${siteInfo.domainInformation().getIpCountry()} ${siteInfo.domainInformation().getIpFlag()}</p>
                </div>
                <div title="Autonomous system">
                    <h3 class="text-sm font-medium text-gray-500 dark:text-gray-300">ASN Details</h3>
                    <p class="text-lg font-mono">AS${siteInfo.domainInformation().getAsn()} - ${siteInfo.domainInformation().getAsnOrg()}</p>
                    <p class="text-sm text-gray-600 dark:text-gray-400 ">${siteInfo.domainInformation().getAsnCountry()} ${siteInfo.domainInformation().getAsnFlag()}</p>
                </div>
            </div>
        @endif
    </div>
</div>

@if (!siteInfo.similar().isEmpty() || !siteInfo.linking().isEmpty())
    <div class="mx-auto md:py-4 px-4 flex-1 space-y-4 content md:block w-full md:w-auto hidden sm:block">
        @template.siteinfo.part.linkedDomains("Similar Domains", siteInfo.domain(), siteInfo.similar())
        @template.siteinfo.part.linkedDomains("Linked Domains", siteInfo.domain(), siteInfo.linking())
    </div>
    <aside id="mobile-menu" class="fixed inset-0 z-50 flex justify-center bg-white/90 dark:bg-black/90 backdrop-blur-sm hidden p-2 h-screen overflow-scroll">
        <div class="w-full">
        @template.siteinfo.part.linkedDomains("Similar Domains", siteInfo.domain(), siteInfo.similar())
        @template.siteinfo.part.linkedDomains("Linked Domains", siteInfo.domain(), siteInfo.linking())
        </div>
    </aside>
    <button class="fixed bottom-10 right-5 sm:hidden text-sm bg-margeblue text-white p-4 rounded-xl active:text-slate-200" id="filter-button">
        <i class="fas fa-filter mr-3"></i>
        Similar
    </button>
@endif

