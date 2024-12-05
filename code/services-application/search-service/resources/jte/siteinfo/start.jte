@import nu.marginalia.search.model.NavbarModel
@import nu.marginalia.search.svc.*
@import nu.marginalia.search.svc.SearchSiteInfoService.SiteOverviewModel
@import nu.marginalia.search.svc.SearchSiteInfoService.SiteOverviewModel.DiscoveredDomain

@param NavbarModel navbar
@param SiteOverviewModel model
<!DOCTYPE html>
<html lang="en">

@template.part.head(title = "Marginalia Search - Site Viewer")

<body class="min-h-screen bg-slate-100 font-sans" >

@template.part.navbar(navbar = navbar)

<header class="border-gray-300 bg-white  shadow-md">
    <div class="max-w-[1400px] mx-auto px-4 py-4">
        <div class="flex items-center  space-x-2">
            <h1 class="text-base md:text-xl mr-2 md:mr-8 font-serif">Site Information</h1>
            <div id="suggestions-anchor" class="hidden"></div>
            <input type="text" class="shadow-inner max-w-64 flex-1 bg-gray-50 border border-gray-300 text-gray-900 text-sm rounded-sm block w-full p-2.5" value="" placeholder="www.example.com" name="query" id="query" >
            <button class="px-4 py-2 bg-margeblue text-white ml-2 rounded">
                <i class="fas fa-search text-sm mr-3"></i>  Inspect
            </button>
        </div>
    </div>
</header>

<div class="max-w-[1000px] mx-auto flex gap-1 flex-col md:flex-row place-items-center md:place-items-start">
    <div class="border rounded m-4 overflow-hidden">
        <div class="bg-margeblue text-white p-2 text-sm">Recently Discovered Domains</div>
        <div class="bg-white">
            <table class="w-full divide-y divide-gray-200">
                <thead>
                <tr class="bg-gray-50">
                    <th scope="col" class="px-2 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Domain Name</th>
                    <th scope="col" class="px-2 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Discover Time</th>
                </tr>
                </thead>
                <tbody class="bg-white divide-y divide-gray-200 text-xs">
                    @for (DiscoveredDomain domain : model.domains())
                    <tr class="hover:bg-gray-50">
                        <td class="px-3 py-3 whitespace-nowrap">
                            <a class="text-liteblue underline" href="site/${domain.name()}">${domain.name()}</a>
                        </td>
                        <td class="px-3 py-3 whitespace-nowrap">
                            ${domain.timestamp()}
                        </td>
                    </tr>
                    @endfor
                </tbody>
            </table>

        </div>

    </div>
        <div class="border rounded m-4 overflow-hidden">
            <div class="bg-margeblue text-white p-2 text-sm">Recently Discovered Domains</div>
            <div class="bg-white">
                <table class="w-full divide-y divide-gray-200">
                    <thead>
                    <tr class="bg-gray-50">
                        <th scope="col" class="px-2 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Domain Name</th>
                        <th scope="col" class="px-2 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Discover Time</th>
                    </tr>
                    </thead>
                    <tbody class="bg-white divide-y divide-gray-200 text-xs">
                    @for (DiscoveredDomain domain : model.domains())
                        <tr class="hover:bg-gray-50">
                            <td class="px-3 py-3 whitespace-nowrap">
                                <a class="text-liteblue underline" href="site/${domain.name()}">${domain.name()}</a>
                            </td>
                            <td class="px-3 py-3 whitespace-nowrap">
                                ${domain.timestamp()}
                            </td>
                        </tr>
                    @endfor
                    </tbody>
                </table>

            </div>

    </div>
</div>


@template.part.footerLegal()

</body>
</html>