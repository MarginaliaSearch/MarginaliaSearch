@import nu.marginalia.search.model.NavbarModel
@import nu.marginalia.search.svc.*

@param SearchSiteInfoService.SiteInfoModel model
@param NavbarModel navbar

<!DOCTYPE html>
<html lang="en">

@template.part.head(title = "Marginalia Search - " + model.domain())

<body class="min-h-screen bg-slate-100 font-sans" >

@template.part.navbar(navbar = navbar)

<header class="border-gray-300 bg-white  shadow-md">
    <div class="max-w-[1400px] mx-auto px-4 py-4">
        <form action="/site" class="flex items-center  space-x-2">
            <h1 class="text-base md:text-xl mr-2 md:mr-8 font-serif">Site Information</h1>
            <div id="suggestions-anchor" class="hidden"></div>
                <input type="text" class="shadow-inner max-w-64 flex-1 bg-gray-50 border border-gray-300 text-gray-900 text-sm rounded-sm block w-full p-2.5"
                       value="${model.domain()}"
                       placeholder="www.example.com"
                       name="domain"
                       id="domain" >
                <button class="px-4 py-2 bg-margeblue text-white ml-2 rounded">
                    <i class="fas fa-search text-sm mr-3"></i>  Inspect
                </button>
        </form>
    </div>
    <div class="mx-auto md:px-4 border bg-slate-50">
        <div class="flex md:space-x-2 max-w-[1000px] mx-auto">
            <div class="has-[:checked]:bg-slate-200 py-1 px-2">
                <a href="?view=info" class="text-sm whitespace-nowrap place-items-baseline space-x-1 text-gray-700 text-xs hover:text-gray-900">
                    @if (model instanceof SearchSiteInfoService.SiteInfoWithContext)
                    <input type="checkbox" class="sr-only hidden absolute" checked readonly />
                    @else
                        <span></span>
                    @endif

                    <i class="fas fa-circle-info"></i>

                    <span>Summary</span>
                </a>
            </div>

            <div class="has-[:checked]:bg-slate-200 py-1 px-2">
                <a href="?view=docs" class="text-sm whitespace-nowrap place-items-baseline space-x-1 text-gray-700 text-xs hover:text-gray-900">
                    @if (model instanceof SearchSiteInfoService.Docs)
                        <input type="checkbox" class="sr-only hidden absolute" checked readonly />
                    @else
                        <span></span>
                    @endif

                    <i class="fa-regular fa-file"></i>

                    <span>Documents</span>
                </a>
            </div>

            <div class="has-[:checked]:bg-slate-200 py-1 px-2">
                <a href="?view=links" class="text-sm whitespace-nowrap place-items-baseline space-x-1 text-gray-700 text-xs hover:text-gray-900">
                    @if (model instanceof SearchSiteInfoService.Backlinks)
                        <input type="checkbox" class="sr-only hidden absolute" checked readonly />
                    @else
                        <span></span>
                    @endif

                    <i class="fas fa-link"></i>

                    <span>Backlinks</span>
                </a>
            </div>
            <div class="grow"></div>
            <div class="has-[:checked]:bg-slate-200 py-1 px-2">
                <a href="?view=report" class="text-sm whitespace-nowrap place-items-baseline space-x-1 text-red-800 text-xs hover:text-red-600">
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
    @elseif (model instanceof SearchSiteInfoService.ReportDomain reportDomain)
        @template.siteinfo.view.reportDomain(reportDomain = reportDomain)
    @elseif (model instanceof SearchSiteInfoService.Docs docs)
        @template.siteinfo.view.docs(docs = docs)
    @endif

</div>


@template.part.footerLegal()

</body>
</html>