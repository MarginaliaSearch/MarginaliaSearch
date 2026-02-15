@import nu.marginalia.search.model.NavbarModel
@import nu.marginalia.search.svc.*

@param SearchSiteInfoService.SiteInfoModel model
@param NavbarModel navbar

<!DOCTYPE html>
<html lang="en">

@template.part.head(title = model.domain() + " - Marginalia Search")

<body class="min-h-screen bg-bgblue dark:bg-gray-900 dark:text-white font-sans " >

@template.part.navbar(navbar = navbar)

<header class="border-b border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 shadow-md">
    <div class="max-w-[1400px] mx-auto p-4">
        <div class="flex place-items-baseline space-x-2">
            <span class="text-gray-900 dark:text-white break-none text-sm sm:text-md font-mono rounded-sm block p-2.5">
                ${model.domain()}
            </span>
            <span class="grow"></span>
            @if (model instanceof SearchSiteInfoService.SiteInfoWithContext siteInfo)
                @if (siteInfo.domainInformation().getPagesIndexed() > 0)
                    <span
                            class="bg-green-50 text-green-900 border-green-200 dark:bg-black dark:text-green-100 border p-1 font-sm rounded"
                            title="The search engine indexes this domain">
                        Indexed
                    </span>
                @elseif (siteInfo.domainInformation().getBlacklisted())
                    <span
                            class="bg-red-50 text-red-900 border-red-200 dark:bg-black dark:text-red-100 border  p-1 font-sm rounded"
                            title="This domain is blacklisted and will not be crawled or indexed">
                        Blacklisted
                    </span>
                @elseif (siteInfo.domainInformation().getUnknownDomain())
                    <span
                            class="bg-purple-50 text-purple-900 border-purple-200 dark:bg-black dark:text-purple-100 border  p-1 font-sm rounded"
                            title="The search engine is not aware of this domain name">
                        Unknown
                    </span>
                @elseif (siteInfo.domainInformation().getNodeAffinity() == 0)
                    <span
                            class="bg-blue-50 text-blue-900 border-blue-200 dark:bg-black dark:text-blue-100 border p-1 font-sm rounded"
                            title="This domain will be crawled by the search engine">
                        In Crawler Queue
                    </span>
                @elseif (!siteInfo.domainInformation().getUnknownDomain())
                    <span
                            class="bg-yellow-50 text-yellow-900 border-yellow-200 dark:bg-black dark:text-yellow-100 border p-1 font-sm rounded"
                            title="The search engine is aware of this domain, but it's not slated for crawling">
                        Known
                    </span>
                @endif
            @endif
        </div>
    </div>
    <div class="mx-auto md:px-4 border dark:border-gray-600 bg-slate-50 dark:bg-gray-600">
        <div class="flex md:space-x-2 max-w-[1000px] mx-auto">
            <div class="has-[:checked]:bg-slate-200 dark:has-[:checked]:bg-slate-800 py-1 px-2">
                <a href="?view=info" class="whitespace-nowrap place-items-baseline space-x-1 text-gray-700 dark:text-white  text-xs sm:text-sm hover:text-gray-900 dark:hover:text-gray-200">
                    @if (model instanceof SearchSiteInfoService.SiteInfoWithContext)
                    <input type="checkbox" class="sr-only hidden " checked readonly />
                    @else
                        <span></span>
                    @endif

                    <i class="fas fa-circle-info"></i>

                    <span>Summary</span>
                </a>
            </div>

            <div class="has-[:checked]:bg-slate-200 dark:has-[:checked]:bg-slate-800 py-1 px-2">
                <a href="?view=docs" class="whitespace-nowrap place-items-baseline space-x-1 text-gray-700 dark:text-white  text-xs sm:text-sm hover:text-gray-900 dark:hover:text-gray-200">
                    @if (model instanceof SearchSiteInfoService.Docs)
                        <input type="checkbox" class="sr-only hidden absolute" checked readonly />
                    @else
                        <span></span>
                    @endif

                    <i class="fa-regular fa-file"></i>

                    <span class="hidden sm:inline">Documents</span>
                    <span class="inline sm:hidden">Docs</span>
                </a>
            </div>

            <div class="has-[:checked]:bg-slate-200 dark:has-[:checked]:bg-slate-800 py-1 px-2">
                <a href="?view=links" class="whitespace-nowrap place-items-baseline space-x-1 text-gray-700 dark:text-white  text-xs sm:text-sm hover:text-gray-900 dark:hover:text-gray-200">
                    @if (model instanceof SearchSiteInfoService.Backlinks)
                        <input type="checkbox" class="sr-only hidden absolute" checked readonly />
                    @else
                        <span></span>
                    @endif

                    <i class="fas fa-link"></i>

                    <span class="hidden sm:inline">Backlinks</span>
                    <span class="inline sm:hidden">Links</span>
                </a>
            </div>
            <div class="has-[:checked]:bg-slate-200 dark:has-[:checked]:bg-slate-800 py-1 px-2">
                <a href="?view=traffic" class="whitespace-nowrap place-items-baseline space-x-1 text-gray-700 dark:text-white text-xs sm:text-sm hover:text-gray-900 dark:hover:text-gray-200">
                    @if (model instanceof SearchSiteInfoService.TrafficSample)
                        <input type="checkbox" class="sr-only hidden absolute" checked readonly />
                    @else
                        <span></span>
                    @endif

                    <i class="fas fa-crosshairs"></i>

                    <span class="hidden sm:inline">Requests</span>
                    <span class="inline sm:hidden">Reqs</span>
                </a>
            </div>
            <div class="grow"></div>
            <div class="has-[:checked]:bg-slate-200 dark:has-[:checked]:bg-slate-800 py-1 px-2">
                <a href="?view=report" class="text-sm whitespace-nowrap place-items-baseline space-x-1 text-red-800 dark:text-red-200  text-xs sm:text-sm hover:text-red-600 dark:hover:text-red-300">
                    @if (model instanceof SearchSiteInfoService.ReportDomain)
                        <input type="checkbox" class="sr-only hidden absolute" checked readonly />
                    @else
                        <span></span>
                    @endif

                    <i class="fa fa-ban"></i>

                    <span>Report</span>
                </a>
            </div>
        </div>
    </div>
</header>

<div class="max-w-[1000px] mx-auto flex gap-1 flex-col md:flex-row place-items-center md:place-items-start">

    @if (model instanceof SearchSiteInfoService.SiteInfoWithContext siteInfo)
        @template.siteinfo.view.overview(siteInfo = siteInfo)
    @elseif (model instanceof SearchSiteInfoService.ReportDomain reportDomain)
        @template.siteinfo.view.reportDomain(reportDomain = reportDomain)
    @elseif (model instanceof SearchSiteInfoService.Backlinks backlinks)
        @template.siteinfo.view.backlinks(backlinks = backlinks)
    @elseif (model instanceof SearchSiteInfoService.Docs docs)
        @template.siteinfo.view.docs(docs = docs)
    @elseif (model instanceof SearchSiteInfoService.DomainAvailabilityEvents events)
        @template.siteinfo.view.events(events = events)
    @elseif (model instanceof SearchSiteInfoService.TrafficSample report)
        @template.siteinfo.view.traffic(report = report)
    @endif

</div>


@template.part.footerLegal()

</body>
<script lang="javascript" src="/js/mobile-button.js"></script>
</html>