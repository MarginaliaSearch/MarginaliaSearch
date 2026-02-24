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
            <i class="fas fa-clock text-2xl animate-pulse"></i>
            <h1 class="font-bold text-2xl">Wait A Moment</h1>
        </div>

        <div>
            The search engine is currently seeing a lot of fairly aggressive bot activity.
        </div>
        <div>
            Please wait for <span class="font-bold" data-tr="${1+waitDuration.getSeconds()}" id="countdown">${1+waitDuration.getSeconds()}</span> seconds before proceeding.
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

            window.setTimeout(()=> { location.replace('${model.redirUrl()}'); }, ${waitDuration.toMillis()});
        </script>
    </div>
</main>
