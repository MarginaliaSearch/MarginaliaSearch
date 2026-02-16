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

@param SecurityChangeEvents events

<div class="flex-1 p-4 space-y-4 mx-auto w-full md:w-auto">
    !{
        var domainInformation = events.domainInformation();
    }

    <div class="border flex flex-col gap-2 border-gray-300 dark:border-gray-600 rounded bg-white dark:bg-gray-800 dark:text-white overflow-hidden mx-2 text-gray-800 text-sm">
    <div class="flex place-items-center space-x-2 p-2 text-md border-b dark:border-gray-600 bg-margeblue text-white rounded">
        <i class="fas fa-network-wired"></i>
        <span>Network Details</span>
    </div>

    <div class="grid grid-cols-2 gap-2 mx-6 sm:mx-8 my-2">
        <div>
            <h3 class="text-sm font-medium text-gray-500 dark:text-gray-300">IP Address</h3>
            <p class="text-lg font-mono">${domainInformation.getIp()}</p>
            <p class="text-sm text-gray-600 dark:text-gray-400">${domainInformation.getIpCountry()} ${SearchSiteInfoService.getFlag(domainInformation.getIpCountry())}</p>
        </div>
        <div title="Autonomous system">
            <h3 class="text-sm font-medium text-gray-500 dark:text-gray-300">ASN Details</h3>
            <p class="text-lg font-mono">AS${domainInformation.getAsn()} - ${domainInformation.getAsnOrg()}</p>
            <p class="text-sm text-gray-600 dark:text-gray-400 ">${domainInformation.getAsnCountry()} ${SearchSiteInfoService.getFlag(domainInformation.getAsnCountry())}</p>
        </div>
    </div>

    @if (domainInformation.hasSecurityData())
        !{
                var secData = domainInformation.getSecurityData();
                
                var secNotBefore = Instant.ofEpochMilli(secData.getSslCertNotBefore());
                var secNotAfter = Instant.ofEpochMilli(secData.getSslCertNotAfter());
            }
        <div class="flex place-items-center space-x-2 p-2 text-md border-b dark:border-gray-600 bg-margeblue text-white rounded">
            <i class="fas fa-server"></i>
            <span>Server Information</span>
        </div>
        <div class="grid grid-cols-2 gap-2 mx-6 sm:mx-8 mt-2">
            <div>
                <h3 class="text-sm font-medium text-gray-500 dark:text-gray-300">HTTP Version</h3>
                <p class="text-lg font-mono">${secData.getHttpVersion()}</p>
            </div>
            <div>
                <h3 class="text-sm font-medium text-gray-500 dark:text-gray-300">HTTP Server</h3>
                <p class="text-lg font-mono">${Objects.requireNonNullElse(secData.getHeaderServer(), "-")}</p>
                <p class="text-sm text-gray-600 dark:text-gray-400">${secData.getHeaderPoweredBy()}</p>
            </div>
        </div>
        <div class="grid grid-cols-2 gap-2 mx-6 sm:mx-8">
            <div>
                <h3 class="text-sm font-medium text-gray-500 dark:text-gray-300">TLS</h3>
                <p class="text-lg font-mono">${Objects.requireNonNullElse(secData.getSslProtocol(), "-")}</p>
            </div>
            <div>
                <h3 class="text-sm font-medium text-gray-500 dark:text-gray-300">Compression</h3>
                <p class="text-lg font-mono">${secData.getHttpCompression() ? "YES" : "NO"}</p>
            </div>
        </div>
        <div class="flex place-items-center space-x-2 p-2 text-md border-b dark:border-gray-600 bg-margeblue text-white rounded">
            <i class="fas fa-lock"></i>
            <span>Certificate</span>
        </div>
        <div class="grid grid-cols-1 gap-2 mx-6 sm:mx-8">
            <div>
                <h3 class="text-sm font-medium text-gray-500 dark:text-gray-300">Key Exhange</h3>
                <p class="text-lg font-mono">${Objects.requireNonNullElse(secData.getSslKeyExchange(), "-")}</p>
            </div>
        </div>
        <div class="grid grid-cols-1 gap-2 mx-6 sm:mx-8">
            <div>
                <h3 class="text-sm font-medium text-gray-500 dark:text-gray-300">Cipher Suite</h3>
                <p class="text-lg font-mono overflow-auto">${Objects.requireNonNullElse(secData.getSslCipherSuite(), "-")}</p>
            </div>
        </div>
        <div class="grid grid-cols-1 gap-2 mx-6 sm:mx-8">
            <div>
                <h3 class="text-sm font-medium text-gray-500 dark:text-gray-300">Subject</h3>
                <p class="text-lg font-mono overflow-auto">${Objects.requireNonNullElse(secData.getSslCertSubject(), "-")}</p>
            </div>
        </div>
        <div class="grid grid-cols-1 gap-2 mx-6 sm:mx-8">
            <div>
                <h3 class="text-sm font-medium text-gray-500 dark:text-gray-300">Subject Alternative Name</h3>
                <p class="text-lg font-mono overflow-auto">${Objects.requireNonNullElse(secData.getSslCertSAN(), "-")}</p>
            </div>
        </div>
        <div class="grid grid-cols-2 gap-2 mx-6 sm:mx-8">
            <div>
                <h3 class="text-sm font-medium text-gray-500 dark:text-gray-300">Not Before</h3>
                <p class="text-lg font-mono">${DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(secNotBefore.atZone(ZoneId.systemDefault()))}</p>
            </div>
            <div>
                <h3 class="text-sm font-medium text-gray-500 dark:text-gray-300">Not After</h3>
                <p class="text-lg font-mono">${DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(secNotAfter.atZone(ZoneId.systemDefault()))}</p>
            </div>
        </div>
    @endif
    </div>

    <div class="border border-gray-300 dark:border-gray-600 rounded bg-white dark:bg-gray-800 dark:text-white overflow-hidden mx-2 text-gray-800 text-sm">
        <div class="flex place-items-center space-x-2 p-2 text-md border-b dark:border-gray-600 bg-margeblue text-white">
            <div>Security Fingerprint Change History</div>
        </div>
        <div class="p-4 dark:text-gray-200">
            This is a summary of the security posture changes for <span class="font-mono dark:text-gray-400 text-gray-800">${events.domain()}</span>.
            The search engine gathers this information to keep track of ownership changes and similar significant events in the lifespan of a website.
        </div>
    </div>


@for (var event : events.events())
<div class="border border-gray-300 dark:border-gray-600 rounded bg-white dark:bg-gray-800 dark:text-white overflow-hidden mx-2 dark:text-white text-gray-800 text-sm">
    <div class="grid grid-cols-3 md:grid-cols-4 gap-3 text-sm p-4">

        @if (event.certFpChange())
            <div class="bg-blue-100 border-blue-200 dark:bg-blue-900 text-blue-800 dark:text-blue-200 rounded p-1 m-1">
                Fingerprint Changed
            </div>
        @endif
        @if (event.certPkChange())
            <div class="bg-blue-100 border-blue-200 dark:bg-blue-900 text-blue-800 dark:text-blue-200 rounded p-1 m-1">Public Key Changed</div>
        @endif
        @if (event.certSanChange())
            <div class="bg-blue-100 border-blue-200 dark:bg-blue-900 text-blue-800 dark:text-blue-200 rounded p-1 m-1">SAN Changed</div>
        @endif
        @if (event.certProfileChange())
            <div class="bg-blue-100 border-blue-200 dark:bg-blue-900 text-blue-800 dark:text-blue-200 rounded p-1 m-1">Profile Changed</div>
        @endif
        @if (event.certSerialChange())
            <div class="bg-blue-100 border-blue-200 dark:bg-blue-900 text-blue-800 dark:text-blue-200 rounded p-1 m-1">Serial Changed</div>
        @endif
        @if (event.certIssuerChange())
            <div class="bg-blue-100 border-blue-200 dark:bg-blue-900 text-blue-800 dark:text-blue-200 rounded p-1 m-1">Issuer Changed</div>
        @endif

        @if (event.ipChange())
            <div  class="bg-red-100 border-red-200 dark:bg-red-900 text-red-800 dark:text-red-200 rounded p-1 m-1">IP Changed</div>
        @endif
        @if (event.asnChange())
            <div class="bg-red-100 border-red-200 dark:bg-red-900 text-red-800 dark:text-red-200 rounded p-1 m-1">ASN Changed</div>
        @endif
        @if (event.secHeaderChange())
            <div class="bg-purple-100 border-purple-200 dark:bg-purple-900 text-purple-800 dark:text-purple-200 rounded p-1 m-1">Security Headers Changed</div>
        @endif
        @if (event.softwareChange())
            <div class="bg-purple-100 border-purple-200 dark:bg-purple-900 text-purple-800 dark:text-purple-200 rounded p-1 m-1">Software Changed</div>
        @endif
        @if (event.schemaChange() != null && !event.schemaChange().isBlank() && !"NONE".equals(event.schemaChange()))
            <div  class="bg-green-100 border-green-200 dark:bg-green-900 text-green-800 dark:text-green-200 rounded p-1 m-1">${event.schemaChange()}</div>
        @endif

    </div>
    <div class="mt-3 pt-3 border-t border-gray-100 dark:border-gray-600 p-2">
        <div class="flex items-center gap-2 text-xs">
            <a class="underline" href="?view=secdetails&changeId=${event.changeId()}">Details</a>

            <div class="flex-grow">
            </div>

            <i class="far fa-clock text-gray-400"></i>
            <div>
            ${DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(event.tsChange().atZone(ZoneId.of("UTC")).truncatedTo(ChronoUnit.SECONDS))}
            </div>
        </div>
    </div>
</div>

@endfor

</div>