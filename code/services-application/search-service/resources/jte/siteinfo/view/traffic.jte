@import nu.marginalia.domclassifier.DomSampleClassification
@import nu.marginalia.search.svc.SearchSiteInfoService.*
@import nu.marginalia.search.svc.SearchSiteInfoService.SiteGeneratedRequestsReport.OutgoingRequestsForDomain

@param SiteGeneratedRequestsReport report

<!-- Main content -->

<div class="flex flex-col space-y-2 w-full">
    <div class="flex flex-col space-y-4 my-4">
        @if (!report.serviceAvailable())
            <div class="border border-gray-300  dark:border-gray-600 rounded bg-white dark:bg-gray-800 dark:text-white flex flex-col overflow-hidden p-4 mx-2 text-gray-800 text-sm">
                <div class="flex place-items-center space-x-2 p-2 text-md border-b dark:border-gray-600 bg-margeblue text-white">
                    <span>Third-Party Requests</span>
                </div>
                <div class="p-4">
                    This service is currently being relentlessly scraped by bots and is disabled until they give up.
                </div>
            </div>
        @elseif (!report.hasData())
            <div class="border border-gray-300  dark:border-gray-600 rounded bg-white dark:bg-gray-800 dark:text-white flex flex-col overflow-hidden p-4 mx-2 text-gray-800 text-sm">
                <div class="flex place-items-center space-x-2 p-2 text-md border-b dark:border-gray-600 bg-margeblue text-white">
                    <span>Third-Party Requests</span>
                </div>
                <div class="p-4">
                    The database of third party requests is still being assembled, and the
                    search engine doesn't yet have any information about ${report.domain()}.
                </div>
            </div>
        @else
            <div class="border border-gray-300 dark:border-gray-600 rounded bg-white dark:bg-gray-800 dark:text-white overflow-hidden mx-2 text-gray-800 text-sm">
                <div class="flex place-items-center space-x-2 p-2 text-md border-b dark:border-gray-600 bg-margeblue text-white">
                    <span>Third-Party Requests</span>
                </div>
                <div class="p-4">
                    To better understand what websites like <span class="inline font-mono text-pink-800 dark:text-pink-200">${report.domain()}</span> are doing,
                    the search engine records which third-party servers the websites communicate with when visited, and produces a report which can be inspected below.
                    <p class="mt-2"></p>
                    To help make sense of the recorded network traffic, the report is supplemented with information from
                    <a href="https://github.com/duckduckgo/tracker-radar/" class="text-blue-800 dark:text-blue-200 underline" rel="external">DuckDuckGo's Tracker Radar</a>,
                    subject to the CC BY-NC-SA 4.0 license.
                    <details class="mt-2">
                    <summary>
                        Learn More
                    </summary>
                    <p class="mt-2">
                    The search engine classifies third party requests into four buckets, based on their apparent purpose.
                    </p>
                    <p class="mt-2">
                        <span class="text-red-600 dark:text-red-400"><i class="fa fa-ad"></i> Advertisement</span> requests are involved in the bidding or display of advertisements, or the tracking
                        of ad impressions.  They do not guarantee ads will be present on the website, as the advertisement
                        broker may decide it's not economic to place an ad for any particular visitor, but it is on the other hand virtually
                        impossible for ads to be present if this type of activity is not found.
                    </p>
                    <p class="mt-2">
                        <span class="text-purple-600 dark:text-purple-400"><i class="fa fa-crosshairs"></i> Tracking</span> requests analyze user behavior on the web, sometimes with the purpose of building a profile
                        for advertisement using cookies or browser fingerprinting technologies, other times the traffic exists only to help understand what visitors are doing on a website
                        for the benefit of the webmasters.
                    </p>
                    <p class="mt-2">
                        <span class="text-orange-600 dark:text-orange-400"><i class="fa fa-shield-alt"></i>  Consent</span> requests manage GDPR or cookie consent popups, and similar nuisances.
                        In general, tracking and advertisement scripts are not run until a consent popup is dismissed.  The system will try to automatically
                        agree to tracking consent popups when it can identify them in order to also capture these deferred requests, but this is not always successful,
                        so the presence of consent requests alone is a weak indicator a website may intend to load tracking or advertisement scripts.
                    </p>
                    <p class="mt-2">
                        <span class="text-gray-600 dark:text-gray-400"><i class="fa fa-question-circle"></i> Unclassified</span> requests are requests the system doesn't know what they are.  Often these are
                        requests to content-delivery networks intended to reduce the network traffic to the server hosting the website and speed up page loads.
                    </p>

                    <p class="mt-2"></p>
                    This data is continuously updated, but updates are fairly
                    slow so the information may not be fully up to date.
                </details>
                </div>
            </div>
        @endif

    </div>

    @if (report.hasData())
    <div class="mx-2">
        <div class="flex place-items-center space-x-2 p-2 text-md border-b dark:border-gray-600 bg-margeblue text-white rounded border mb-4">
            <span>Summary</span>
        </div>
        <!-- Summary Stats -->
        <div class="grid grid-cols-4 gap-4 mb-8">
            <div class="bg-white rounded p-4 shadow-sm border dark:bg-gray-800 dark:border-gray-600 place-items-center">
                <div class="text-2xl font-bold text-red-600 dark:text-red-400">${report.requestSummary().getOrDefault(DomSampleClassification.ADS, 0)}</div>
                <div class="text-sm text-gray-600 dark:text-gray-400">Ads</div>
            </div>
            <div class="bg-white rounded p-4 shadow-sm border dark:bg-gray-800 dark:border-gray-600 place-items-center">
                <div class="text-2xl font-bold text-purple-600 dark:text-purple-400">${report.requestSummary().getOrDefault(DomSampleClassification.TRACKING, 0)}</div>
                <div class="text-sm text-gray-600 dark:text-gray-400">Tracking</div>
            </div>
            <div class="bg-white rounded p-4 shadow-sm border dark:bg-gray-800 dark:border-gray-600 place-items-center">
                <div class="text-2xl font-bold text-orange-600 dark:text-orange-400">${report.requestSummary().getOrDefault(DomSampleClassification.CONSENT, 0)}</div>
                <div class="text-sm text-gray-600 dark:text-gray-400">Consent</div>
            </div>
            <div class="bg-white rounded p-4 shadow-sm border dark:bg-gray-800 dark:border-gray-600 place-items-center">
                <div class="text-2xl font-bold text-gray-600 dark:text-gray-400">${report.requestSummary().getOrDefault(DomSampleClassification.UNCLASSIFIED, 0)}</div>
                <div class="text-sm text-gray-600 dark:text-gray-400">Other</div>
            </div>
        </div>
    </div>



    <!-- Domain Groups -->
    <div class="space-y-4 mx-2">
        <div class="flex place-items-center space-x-2 p-2 text-md border-b dark:border-gray-600 bg-margeblue text-white rounded border">
            <span>Breakdown</span>
        </div>
        @if (report.requests().isEmpty())
            <div class="border border-gray-300  dark:border-gray-600 rounded bg-white dark:bg-gray-800 dark:text-white flex flex-col overflow-hidden p-4 mx-2 text-gray-800 text-sm">
                No third-party requests were made!
            </div>
        @endif
        @for (OutgoingRequestsForDomain request : report.requests())
        <!-- Google Analytics Domain -->
        <div class="bg-white rounded shadow-sm border border-gray-200  dark:bg-gray-800 dark:border-gray-600">
            <div class="p-2 md:p-6 border-b border-gray-100 dark:border-gray-600">
                <div class="flex items-start justify-between flex-col md:flex-row gap-2">
                    <div class="flex-1">

                        <h3 class="text-lg font-semibold dark:text-gray-100 text-gray-900 font-mono">${request.domain().toString()}</h3>

                        @if (request.ownerDisplayName() != null)
                        <p class="text-sm text-gray-600 dark:text-gray-400 mt-1">${request.ownerDisplayName()}</p>
                        @elseif (request.ownerName() != null)
                            <p class="text-sm text-gray-600 dark:text-gray-400 mt-1">${request.ownerName()}</p>
                        @endif
                        <div class="flex items-center gap-4 mt-3">
                            @if (request.ownerUrl() != null)
                            <a href="${request.ownerUrl()}" rel="external nofollow" class="text-blue-600 dark:text-blue-200 text-sm flex flex-row place-items-baseline gap-1">
                                <i class="fas fa-external-link-alt text-xs"></i> Visit Site
                            </a>
                            @endif
                            @if (request.ownerPolicy() != null)
                            <a href="${request.ownerPolicy()}" rel="external nofollow"  class="text-blue-600 dark:text-blue-200 text-sm flex flex-row place-items-baseline gap-1">
                                <i class="fas fa-shield-alt text-xs"></i> Privacy Policy
                            </a>
                            @endif
                        </div>
                    </div>
                    <div class="flex flex-wrap justify-end gap-2 md:ml-2">
                        @for (String tag : request.ownerCategories())
                            <span class="px-2 py-1 ${SiteGeneratedRequestsReport.categoryColor(tag)} text-xs rounded">${tag}</span>
                        @endfor
                    </div>
                </div>
            </div>

            <div class="p-4">
                <div class="space-y-3">
                    @for (var req : request.endpoints())
                    <div class="flex items-center justify-between py-2 px-3 bg-gray-100 dark:bg-gray-600 rounded-lg">
                        <div class="flex items-center gap-3">
                            <div class="text-xs text-gray-500 dark:text-gray-100 font-mono">${req.method()}</div>
                            <span class="text-sm text-gray-600 dark:text-white font-mono break-all">${req.path()}</span>
                        </div>
                        @if (req.classification() != DomSampleClassification.UNCLASSIFIED)
                        <span class="px-2 py-1 bg-orange-100 text-orange-800 text-xs rounded flex flex-row place-items-baseline gap-1 ${SiteGeneratedRequestsReport.classificationColor(req.classification())}">
                            <i class="fa ${SiteGeneratedRequestsReport.classificationIcon(req.classification())}"></i> ${req.classification().name()}</span>
                        @endif
                    </div>
                    @endfor
                </div>
            </div>
        </div>
        @endfor
    </div>
    @endif
</div>
