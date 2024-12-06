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
        <h1 class="text-base md:text-xl mr-2 md:mr-8 font-serif">View Site Information</h1>
    </div>
</header>

<div class="max-w-[1000px] mx-auto flex gap-4 flex-col md:flex-row place-items-center md:place-items-start p-4">


    <div class="border rounded md:my-4 overflow-hidden bg-white flex flex-col space-y-2 flex-1">
        <div class="bg-margeblue text-white p-2 text-sm mb-2">View Site Information</div>

        <p class="mx-4">This utility lets you explore what the search engine knows about the web,
            including the link graph, backlinks, etc.</p>

        <label class="mx-4 pt-2 text-sm text-gray-800" for="domain">Enter a domain name</label>
        <form action="/site" class="items-center space-x-2 flex flex-row pb-4 pr-4 pl-4">
            <input type="text" class="shadow-inner flex-1 bg-gray-50 border border-gray-300 text-gray-900 text-sm rounded-sm block p-2.5"
                   placeholder="www.example.com"
                   name="domain"
                   id="domain" >
            <button class="px-4 py-2 bg-margeblue text-white ml-2 rounded">
                <i class="fas fa-search text-sm mr-3"></i>  Inspect
            </button>
        </form>


        <p class="p-4 text-sm text-gray-800"><i class="fas fa-person-praying text-margeblue text-lg"></i> Please don't attempt to scrape this data.  This is a waste of your resources as well as the
            marginalia search resources,  instead reach out with an email, and we'll work out some way of exporting the data or creating a dedicated API instead.</p>
    </div>

    <div class="border rounded md:my-4 overflow-hidden w-full md:w-auto">
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