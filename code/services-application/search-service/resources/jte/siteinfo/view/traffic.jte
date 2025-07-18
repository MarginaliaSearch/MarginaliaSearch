@import nu.marginalia.domclassifier.DomSampleClassifier
@import nu.marginalia.search.svc.SearchSiteInfoService.*
@import nu.marginalia.search.svc.SearchSiteInfoService.SiteGeneratedRequestsReport.OutgoingRequestsForDomain
@import java.util.List
@import java.util.Objects

@param SiteGeneratedRequestsReport report

<!-- Main content -->

<div class="flex flex-col space-y-2 w-[800px]">
<div class="flex flex-col space-y-4 my-4">

    @if (!report.serviceAvailable())
        <div class="border border-gray-300  dark:border-gray-600 rounded bg-white dark:bg-gray-800 dark:text-white flex flex-col overflow-hidden p-4 mx-2 text-gray-800 text-sm">
            This service is currently being relentlessly scraped by bots and is disabled until they give up.
        </div>
    @elseif (!report.hasData())
        <div class="border border-gray-300  dark:border-gray-600 rounded bg-white dark:bg-gray-800 dark:text-white flex flex-col overflow-hidden p-4 mx-2 text-gray-800 text-sm">
            The search engine doesn't yet have any information about ${report.domain()}.
        </div>
    @else
        <div class="border border-gray-300 dark:border-gray-600 rounded bg-white dark:bg-gray-800 dark:text-white overflow-hidden p-4 mx-2 text-gray-800 text-sm">
            To better understand what <span class="inline font-mono text-pink-800 dark:text-pink-200">${report.domain()}</span> is doing, the search engine records which servers
            it talks to when visiting the website, and produces a report which can be inspected below.
            <p class="mt-2"></p>
            To help make sense of the network traffic, the report is decorated with supplemental information from
            <a href="https://github.com/duckduckgo/tracker-radar/" class="text-blue-800 dark:text-blue-200 underline" rel="external">DuckDuckGo's Tracker Radar</a>,
            subject to the CC BY-NC-SA 4.0 license.
        </div>
    @endif

</div>

<div class="mx-2">

    <!-- Summary Stats -->
    <div class="grid grid-cols-4 gap-4 mb-8">
        <div class="bg-white rounded p-4 shadow-sm border dark:bg-gray-800 dark:border-gray-600 place-items-center">
            <div class="text-2xl font-bold text-red-600 dark:text-red-400">${report.requestSummary().getOrDefault(DomSampleClassifier.DomSampleClassification.ADS, 0)}</div>
            <div class="text-sm text-gray-600 dark:text-gray-400">Advertisement</div>
        </div>
        <div class="bg-white rounded p-4 shadow-sm border dark:bg-gray-800 dark:border-gray-600 place-items-center">
            <div class="text-2xl font-bold text-purple-600 dark:text-purple-400">${report.requestSummary().getOrDefault(DomSampleClassifier.DomSampleClassification.TRACKING, 0)}</div>
            <div class="text-sm text-gray-600 dark:text-gray-400">Tracking</div>
        </div>
        <div class="bg-white rounded p-4 shadow-sm border dark:bg-gray-800 dark:border-gray-600 place-items-center">
            <div class="text-2xl font-bold text-orange-600 dark:text-orange-400">${report.requestSummary().getOrDefault(DomSampleClassifier.DomSampleClassification.CONSENT, 0)}</div>
            <div class="text-sm text-gray-600 dark:text-gray-400">Consent</div>
        </div>
        <div class="bg-white rounded p-4 shadow-sm border dark:bg-gray-800 dark:border-gray-600 place-items-center">
            <div class="text-2xl font-bold text-gray-600 dark:text-gray-400">${report.requestSummary().getOrDefault(DomSampleClassifier.DomSampleClassification.UNCLASSIFIED, 0)}</div>
            <div class="text-sm text-gray-600 dark:text-gray-400">Unclassified</div>
        </div>
    </div>
</div>


    <!-- Domain Groups -->
    <div class="space-y-4 mx-2">

        @for (OutgoingRequestsForDomain request : report.requests())
        <!-- Google Analytics Domain -->
        <div class="bg-white rounded shadow-sm border border-gray-200  dark:bg-gray-800 dark:border-gray-600">
            <div class="p-6 border-b border-gray-100 dark:border-gray-600">
                <div class="flex items-start justify-between">
                    <div class="flex-1">

                        <h3 class="text-lg font-semibold dark:text-gray-100 text-gray-900 font-mono">${request.domain().toString()}</h3>

                        @if (request.ownerName() != null)
                        <p class="text-sm text-gray-600 dark:text-gray-400 mt-1">${request.ownerName()}</p>
                        @endif
                        <div class="flex items-center gap-4 mt-3">
                            @if (request.ownerUrl() != null)
                            <a href="${request.ownerUrl()}" rel="external" class="text-blue-600 dark:text-blue-200 text-sm flex flex-row place-items-baseline gap-1">
                                <i class="fas fa-external-link-alt text-xs"></i> Visit Site
                            </a>
                            @endif
                            @if (request.ownerPolicy() != null)
                            <a href="${request.ownerPolicy()}" rel="external"  class="text-blue-600 dark:text-blue-200 text-sm flex flex-row place-items-baseline gap-1">
                                <i class="fas fa-shield-alt text-xs"></i> Privacy Policy
                            </a>
                            @endif
                        </div>
                    </div>
                    <div class="flex flex-wrap justify-end gap-2 ml-2">
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
                        @if (req.classification() != DomSampleClassifier.DomSampleClassification.UNCLASSIFIED)
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
</div>