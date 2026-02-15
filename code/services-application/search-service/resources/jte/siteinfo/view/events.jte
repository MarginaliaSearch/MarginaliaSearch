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

@param DomainAvailabilityEvents events

<div class="flex-1 p-4 space-y-4 mx-auto w-full md:w-auto">
    <div class="border border-gray-300 dark:border-gray-600 rounded bg-white dark:bg-gray-800 dark:text-white overflow-hidden mx-2 text-gray-800 text-sm">
        <div class="flex place-items-center space-x-2 p-2 text-md border-b dark:border-gray-600 bg-margeblue text-white">
            <span>Historical Availability Information</span>
        </div>
        <div class="p-4 dark:text-gray-200">
            This is a summary of the avalilability events for <span class="font-mono dark:text-gray-400 text-gray-800">${events.domain()}</span>.  Keep in mind the
            system can not know where an error occurs, an outage may easily be a network error on the Marginalia side of the connection.
        </div>
        <div class="px-4 pb-4 dark:text-gray-200">
            Availability events are generated when the reachability state of a server changes.  If a server has always (or never) been reachable,
            then no events will be recorded.
        </div>
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