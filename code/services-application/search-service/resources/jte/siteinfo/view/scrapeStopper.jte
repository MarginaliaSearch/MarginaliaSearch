@import nu.marginalia.db.DbDomainQueries
@import nu.marginalia.search.svc.SearchSiteInfoService
@import nu.marginalia.search.svc.SearchSiteInfoService.*
@import nu.marginalia.search.model.UrlDetails
@import java.time.Duration
@import java.time.Instant
@import java.time.ZoneId
@import java.time.ZonedDateTime
@import java.time.format.DateTimeFormatter
@import java.time.temporal.ChronoUnit
@import java.util.Objects

@param ScrapeStopperModel model
!{
    Duration waitDuration = model.waitTime();   
}
<!-- Main content -->
<main class="flex-1 py-4 p-2 sm:p-4 max-w-3xl space-y-4">
    <div class="border dark:border-gray-600 rounded flex flex-col space-x-4 bg-white dark:bg-gray-800 text-gray-600 dark:text-gray-100 text-sm p-4 items-center">
        <div class="flex flex-row gap-2 place-items-center">
            <i class="fas fa-hourglass text-2xl animate-pulse"></i>
            <div class="px-2">
                The search engine is currently seeing a lot of fairly aggressive bot activity.
                Please wait for <span data-tr="${1+waitDuration.getSeconds()}" id="countdown">${1+waitDuration.getSeconds()}</span> seconds before proceeding.
            </div>
        </div>

        <noscript class="my-2">
            Click <a role="button" href="${model.redirUrl()}" class="underline">here</a> to proceed manually if javascript is disabled.
        </noscript>

        <script>
            window.setInterval(()=>{
                const cd = document.getElementById('countdown');
                var tr = cd.getAttribute('data-tr');
                tr--;
                cd.setAttribute('data-tr', tr);
                cd.innerHTML=tr;
            }, 1000);

            window.setTimeout(()=> { window.location='${model.redirUrl()}'; }, ${waitDuration.toMillis()});
        </script>
    </div>
</main>
