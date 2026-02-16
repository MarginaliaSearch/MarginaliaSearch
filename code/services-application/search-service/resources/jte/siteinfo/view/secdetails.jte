@import com.google.common.collect.Sets
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
@import java.util.*
@import java.util.List
@import java.util.HashSet
@import java.util.Objects
@import java.util.Set

@param SecurityChangeDetails details

<div class="flex-1 p-4 space-y-4 mx-auto w-full md:w-auto">
    <div class="border border-gray-300 dark:border-gray-600 rounded bg-white dark:bg-gray-800 dark:text-white overflow-hidden mx-2 text-gray-800 text-sm">
        <div class="flex place-items-center space-x-2 p-2 text-md border-b dark:border-gray-600 bg-margeblue text-white">
            <span>Security Fingerprint Change Details #${details.changeId()}</span>
        </div>
        <div class="p-4 dark:text-gray-200">
            This is a breakdown of the security fingerprint changes for <span class="font-mono dark:text-gray-400 text-gray-800">${details.domain()}</span>.
        </div>
    </div>

!{
    Set<String> changedKeys = new HashSet<>();
    Set<String> beforeKeys = new HashSet<>(details.before().keySet());
    Set<String> afterKeys = new HashSet<>(details.after().keySet());
    changedKeys.addAll(Sets.symmetricDifference(beforeKeys, afterKeys));

    for (String key: Sets.intersection(beforeKeys, afterKeys)) {
        Object beforeVal = details.before().get(key);
        Object afterVal = details.after().get(key);
        
        if (!Objects.equals(beforeVal, afterVal)) {
            changedKeys.add(key);
        }
    }

    // Don't flag TS change as an update
    changedKeys.remove("tsLastUpdate");

    List<String> sortedKeys = new ArrayList<>(Sets.union(beforeKeys, afterKeys));
    sortedKeys.sort(Comparator.naturalOrder());

    // We don't want these listed
    sortedKeys.remove("domainId");
    sortedKeys.remove("nodeId");
}

<div role="table" class="border border-gray-300 grid grid-cols-2 sm:grid-cols-3 gap-2 sm:gap-y-2 p-1 dark:border-gray-600 rounded bg-white dark:bg-gray-800 dark:text-white overflow-hidden mx-2 dark:text-white text-gray-800 text-sm">
    <div role="rowgroup" class="contents font-bold font-serif">
        <span role="columnheader" class="col-span-2 sm:col-auto mt-2 sm:mt-0 text-lg sm:text-sm  sm:border-r border-gray-400 dark:border-gray-600" aria-role="">Property</span>
        <span role="columnheader" class="overflow-auto text-gray-800 dark:text-gray-200">
            Old Value
        </span>
        <span role="columnheader" class="overflow-auto text-gray-800 dark:text-gray-200">
            New Value
        </span>
        <span role="separator" class="col-span-2 sm:col-span-3 border-b border-gray-500"></span>
    </div>

    @for (String key : sortedKeys)
    <div role="rowgroup" class="contents">
    @if (changedKeys.contains(key))
        <span role="rowheader" class="col-span-2 sm:col-auto mt-2 sm:mt-0 text-lg font-medium sm:text-sm sm:border-r border-gray-400 dark:border-gray-600">${key}
            @if (key.equals("headerContentSecurityPolicyHash"))
                <sup class="text-red-500" title="Buggy prior to 2026-02-17">BUG</sup>
            @endif
        </span>
        <span role="cell" class="font-mono overflow-auto  bg-red-100 border-red-200 dark:bg-red-900 text-red-800 dark:text-red-200">
            ${SecurityChangeDetails.renderValue(key, details.before().get(key))}
        </span>
        <span role="cell" class="font-mono overflow-auto  bg-red-100 border-red-200 dark:bg-red-900 text-red-800 dark:text-red-200">
            ${SecurityChangeDetails.renderValue(key, details.after().get(key))}
        </span>
        <span role="separator" class="col-span-2 border-b border-gray-400 dark:border-gray-600 sm:hidden"></span>
    @else
        <span role="rowheader" class="col-span-2 sm:col-auto mt-2 sm:mt-0 text-lg font-medium sm:text-sm sm:border-r border-gray-400 dark:border-gray-600" aria-role="">${key}</span>
        <span role="cell" class="font-mono overflow-auto  bg-gray-100 border-gray-200 dark:bg-gray-900 text-gray-800 dark:text-gray-200">
            ${SecurityChangeDetails.renderValue(key, details.before().get(key))}
        </span>
        <span role="cell" class="font-mono overflow-auto  bg-gray-100 border-gray-200 dark:bg-gray-900 text-gray-800 dark:text-gray-200">
            ${SecurityChangeDetails.renderValue(key, details.after().get(key))}
        </span>
        <span role="separator" class="col-span-2 border-b border-gray-400 dark:border-gray-600  sm:hidden"></span>
    @endif
    </div>
    @endfor
</div>
</div>