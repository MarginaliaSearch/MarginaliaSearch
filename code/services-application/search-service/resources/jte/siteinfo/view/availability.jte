@import io.netty.handler.codec.http.HttpResponseStatus
@import io.netty.handler.codec.http.HttpStatusClass
@import nu.marginalia.domclassifier.DomSampleClassification
@import nu.marginalia.search.svc.SearchSiteInfoService
@import nu.marginalia.search.svc.SearchSiteInfoService.*
@import nu.marginalia.search.svc.SearchSiteInfoService.TrafficSample.RequestsForTargetDomain
@import java.time.Instant
@import java.time.LocalDateTime
@import java.time.ZoneId
@import java.time.ZonedDateTime
@import java.time.format.DateTimeFormatter
@import java.time.temporal.ChronoUnit
@import java.util.Objects

@param DomainAvailabilityEvents events
!{
    var domainInformation = events.domainInformation();
    }
<div class="flex-1 p-4 space-y-4 mx-auto w-full md:w-auto">

    @if (domainInformation.hasPingData())
        !{ 
                var pingData = domainInformation.getPingData();
                var longTimeAgo = ZonedDateTime.now().minus(30, ChronoUnit.DAYS);

                var utc = ZoneId.of("UTC");
                var pingLastTs = ZonedDateTime.ofInstant(Instant.ofEpochMilli(pingData.getTsLast()), utc);
                var pingLastAvailable = ZonedDateTime.ofInstant(Instant.ofEpochMilli(pingData.getTsLastAvailable()), utc);
                var pingErrorTs = ZonedDateTime.ofInstant(Instant.ofEpochMilli(pingData.getTsLastError()), utc);
            }

        <div class="border flex flex-col gap-2 border-gray-300 dark:border-gray-600 rounded bg-white dark:bg-gray-800 dark:text-white overflow-hidden mx-2 text-gray-800 text-sm">

        <div class="flex place-items-center space-x-2 p-2 text-md border-b dark:border-gray-600 bg-margeblue text-white rounded">
            @if (pingData.getServerAvailable())
                <i class="fas fa-circle dark:text-green-300 text-green-700"></i>
                <span>Availability</span>
            @else
                <i class="fas fa-circle dark:text-red-300 text-green-700"></i>
                <span>Availability </span>
            @endif
        </div>

        <div class="py-2">
        <div class="grid grid-cols-2 gap-2 mx-6 sm:mx-8">
            <div>
                @if (pingData.getServerAvailable())
                    <h3 class="text-sm font-medium text-gray-500 dark:text-gray-300">Server Status </h3>
                    <p class="text-lg font-mono">OK</p>
                    <p class="text-sm text-gray-600 dark:text-gray-400 ">${pingData.getResponseTimeMs()}ms response time</p>
                @else
                    <h3 class="text-sm font-medium text-gray-500 dark:text-gray-300">Server Status </h3>
                    <p class="text-lg font-mono" title="${pingData.getErrorDesc()}">${pingData.getErrorClassification()}</p>
                @endif
            </div>
            <div>
                <h3 class="text-sm font-medium text-gray-500 dark:text-gray-300">Last Available</h3>
                <p class="text-lg font-mono" title="${DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(pingLastAvailable)}">
                    ${SearchSiteInfoService.renderRelativeTime(pingLastAvailable)}
                </p>
            </div>
            <div>
                <h3 class="text-sm font-medium text-gray-500 dark:text-gray-300">Last Error</h3>
                <p class="text-lg font-mono" title="${DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(pingErrorTs)}">
                    ${SearchSiteInfoService.renderRelativeTime(pingErrorTs)}
                </p>
                @if (!pingData.getServerAvailable())
                    <p class="text-sm text-gray-600 dark:text-gray-400 ">${pingData.getConsecutiveFailures()} consecutive errors</p>
                @endif
            </div>
            <div>
                <h3 class="text-sm font-medium text-gray-500 dark:text-gray-300">Last Ping Attempt</h3>
                <p class="text-lg font-mono" title="${DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(pingLastTs)}">
                    ${SearchSiteInfoService.renderRelativeTime(pingLastTs)}
                </p>
            </div>
        </div>
        </div>
    </div>

    @endif
    <div class="border border-gray-300 dark:border-gray-600 rounded bg-white dark:bg-gray-800 dark:text-white overflow-hidden mx-2 text-gray-800 text-sm">
        <div class="flex place-items-center space-x-2 p-2 text-md bg-margeblue text-white">
            Historical Data
        </div>
        <p class="m-2">
            Historical availability data for <span class="font-mono">${domainInformation.getDomain()}</span> available below.
            Keep in mind that these events reflect both outages at the remote server, as well as network issues
            client side.
        </p>
    </div>
@for (var event : events.events())
<div class="border border-gray-300 dark:border-gray-600 rounded bg-white dark:bg-gray-800 dark:text-white overflow-hidden mx-2 dark:text-white text-gray-800 text-sm">
    <div class="grid grid-cols-2 gap-3 text-sm p-4">
        @if (!event.available())
        <div>
            <div class="text-xs dark:text-gray-300 mb-1">Error Type</div>
            <div class="flex sm:flex-row flex-col gap-1 place-items-baseline">
            <span class="text-lg font-mono">
                ${event.outageType()}
            </span>
            <div class="dark:text-gray-400 text-xs">
            ${Objects.requireNonNullElse(event.errorMessage(), "-")}
            </div>
            </div>
        </div>
        @endif
        @if (event.httpStatusCode() != null && event.httpStatusCode() > 0)
            <div>
                <div class="text-xs text-gray-800 dark:text-gray-300 mb-1">HTTP Code</div>
                <div class="flex sm:flex-row flex-col gap-1 place-items-baseline">
                    @if (event.httpStatusCode() < 400)
                        <span class="text-lg font-mono dark:text-green-500 text-green-600">${event.httpStatusCode()}</span>
                        <span class="text-xs dark:text-gray-200">${HttpResponseStatus.valueOf(event.httpStatusCode()).reasonPhrase()}</span>
                    @else
                        <span class="text-lg font-mono text-red-500 text-green-600">${event.httpStatusCode()}</span>
                        <span class="text-xs dark:text-gray-200">${HttpResponseStatus.valueOf(event.httpStatusCode()).reasonPhrase()}</span>
                    @endif

                </div>

            </div>
        @endif
    </div>

    <div class="mt-3 pt-3 border-t border-gray-100 dark:border-gray-600 p-2">
        <div class="flex items-center gap-2 text-xs">
            @if (event.available())
                <i class="fas fa-arrow-up text-green-500 dark:text-green-400 text-lg"></i>
                <span class="text-sm font-medium">Available</span>
            @else
                <i class="fas fa-arrow-down text-red-500 dark:text-red-400 text-lg"></i>
                <span class="text-sm font-medium">Unavailable</span>
            @endif
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