@import io.netty.handler.codec.http.HttpResponseStatus
@import io.netty.handler.codec.http.HttpStatusClass
@import nu.marginalia.domclassifier.DomSampleClassification
@import nu.marginalia.search.svc.SearchSiteInfoService.*
@import nu.marginalia.search.svc.SearchSiteInfoService.TrafficSample.RequestsForTargetDomain
@import java.time.LocalDateTime
@import java.time.ZoneId
@import java.time.ZonedDateTime
@import java.time.format.DateTimeFormatter
@import java.time.temporal.ChronoUnit
@import java.util.Objects

@param SecurityChangeEvents events

<div class="flex-1 p-4 space-y-4 mx-auto w-full md:w-auto">
    <div class="border border-gray-300 dark:border-gray-600 rounded bg-white dark:bg-gray-800 dark:text-white overflow-hidden mx-2 text-gray-800 text-sm">
        <div class="flex place-items-center space-x-2 p-2 text-md border-b dark:border-gray-600 bg-margeblue text-white">
            <span>Security Posture Change History</span>
        </div>
        <div class="p-4 dark:text-gray-200">
            This is a summary of the security posture changes for <span class="font-mono dark:text-gray-400 text-gray-800">${events.domain()}</span>.
            The search engine gathers this information to keep track of ownership changes and similar significant events in the lifespan of a website.
        </div>
    </div>


@for (var event : events.events())
<div class="border border-gray-300 dark:border-gray-600 rounded bg-white dark:bg-gray-800 dark:text-white overflow-hidden mx-2 dark:text-white text-gray-800 text-sm">
    <div class="gap-3 text-sm p-4">

        @if (event.certFpChange())
            <span class="bg-blue-100 border-blue-200 dark:bg-blue-900 text-blue-800 dark:text-blue-200 rounded p-1 m-1">
                Fingerprint Changed
            </span>
        @endif
        @if (event.certPkChange())
            <span class="bg-blue-100 border-blue-200 dark:bg-blue-900 text-blue-800 dark:text-blue-200 rounded p-1 m-1">Public Key Changed</span>
        @endif
        @if (event.certSanChange())
            <span class="bg-blue-100 border-blue-200 dark:bg-blue-900 text-blue-800 dark:text-blue-200 rounded p-1 m-1">SAN Changed</span>
        @endif
        @if (event.certProfileChange())
            <span class="bg-blue-100 border-blue-200 dark:bg-blue-900 text-blue-800 dark:text-blue-200 rounded p-1 m-1">Profile Changed</span>
        @endif
        @if (event.certSerialChange())
            <span class="bg-blue-100 border-blue-200 dark:bg-blue-900 text-blue-800 dark:text-blue-200 rounded p-1 m-1">Serial Changed</span>
        @endif
        @if (event.certIssuerChange())
            <span class="bg-blue-100 border-blue-200 dark:bg-blue-900 text-blue-800 dark:text-blue-200 rounded p-1 m-1">Issuer Changed</span>
        @endif

        @if (event.ipChange())
            <span  class="bg-red-100 border-red-200 dark:bg-red-900 text-red-800 dark:text-red-200 rounded p-1 m-1">IP Changed</span>
        @endif
        @if (event.asnChange())
            <span class="bg-red-100 border-red-200 dark:bg-red-900 text-red-800 dark:text-red-200 rounded p-1 m-1">ASN Changed</span>
        @endif
        @if (event.secHeaderChange())
            <span class="bg-purple-100 border-purple-200 dark:bg-purple-900 text-purple-800 dark:text-purple-200 rounded p-1 m-1">Security Headers Changed</span>
        @endif
        @if (event.softwareChange())
            <span class="bg-purple-100 border-purple-200 dark:bg-purple-900 text-purple-800 dark:text-purple-200 rounded p-1 m-1">Software Changed</span>
        @endif
        @if (event.schemaChange() != null && !event.schemaChange().isBlank())
            <span  class="bg-green-100 border-green-200 dark:bg-green-900 text-green-800 dark:text-green-200 rounded p-1 m-1">${event.schemaChange()}</span>
        @endif

    </div>
    <div class="mt-3 pt-3 border-t border-gray-100 dark:border-gray-600 p-2">
        <div class="flex items-center gap-2 text-xs">
            <a class="underline" href="?view=secdetails&changeId=${event.changeId()}">Details</a>

            <div class="flex-grow">
            </div>

            <i class="far fa-clock text-gray-400"></i>
            <span>
            ${DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(event.tsChange().atZone(ZoneId.of("UTC")).truncatedTo(ChronoUnit.SECONDS))}
            </span>
        </div>
    </div>
</div>

@endfor

</div>