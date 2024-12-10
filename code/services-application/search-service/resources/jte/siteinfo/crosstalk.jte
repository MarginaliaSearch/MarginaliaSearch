@import nu.marginalia.search.model.NavbarModel
@import nu.marginalia.search.model.UrlDetails
@import nu.marginalia.model.EdgeDomain
@import nu.marginalia.search.svc.*

@param SearchCrosstalkService.CrosstalkResult model
@param NavbarModel navbar

<!DOCTYPE html>
<html lang="en">

@template.part.head(title = "Marginalia Search - " + model.domainA() + "/" + model.domainB())

<body class="min-h-screen bg-slate-100 font-sans" >

@template.part.navbar(navbar = navbar)

<header class="border-gray-300 bg-white  shadow-md">
    <div class="max-w-[1400px] mx-auto p-2 md:p-4">
        <div class="flex flex-col place-items-center md:flex-row md:place-items-baseline space-x-2">
            <div class="text-gray-900 text-md font-mono rounded-sm block p-2.5 break-all">
                <a class="underline" href="/site/${model.domainA()}"> ${model.domainA()}</a>
            </div>

            @if (model.hasBoth())
                <div class="hidden md:block">
                    <i class="text-margeblue fa-solid fa-arrows-left-right"></i>
                </div>
                <div class="block md:hidden">
                    <i class="text-margeblue fa-solid fa-up-down"></i>
                </div>
            @elseif (model.hasA())
                <div class="hidden md:block">
                    <i class="text-margeblue fa-solid fa-arrow-right"></i>
                </div>
                <div class="block md:hidden">
                    <i class="text-margeblue fa-solid fa-arrow-down"></i>
                </div>
            @else
                <div class="hidden md:block">
                    <i class="text-margeblue fa-solid fa-arrow-left"></i>
                </div>
                <div class="block md:hidden">
                    <i class="text-margeblue fa-solid fa-arrow-up"></i>
                </div>
            @endif

            <div class="text-gray-900 text-md font-mono rounded-sm block p-2.5 break-all">
                <a class="underline" href="/site/${model.domainB()}"> ${model.domainB()}</a>
            </div>
            <span class="grow"></span>
        </div>
    </div>
</header>

<div class="max-w-[1000px] mx-auto flex gap-1 flex-col md:flex-row place-items-center md:place-items-start py-4">

    @if (model.hasA())
    <div class="border rounded bg-white flex flex-col overflow-hidden mx-4">
        <div class="flex space-x-2 flex-row place-items-baseline bg-margeblue text-white p-2  text-md">
            <span class="fas fa-globe"></span>
            <a href="/site/${model.domainA()}">${model.domainA()}</a>
            <span class="grow"></span>
            <a rel="nofollow noopener external" href="${new EdgeDomain(model.domainA()).toRootUrlHttps().toString()}" class="fa-solid fa-arrow-up-right-from-square" ></a>
        </div>

        @for (UrlDetails details : model.aToB())
            <div class="p-2 font-medium text-sm text-gray-800 mx-2 mt-2">$unsafe{details.displayTitle()}</div>
            <div class="p-2 mx-2 text-gray-700 text-sm">
                $unsafe{details.displayDescription()}
            </div>
            <div class="p-2 text-sm border-b pb-6">
                <a rel="external noopener nofollow" href="${details.url.toString()}" class="mx-3 text-liteblue flex space-x-2 place-items-baseline hyphens-auto">
                    <i class="fa fa-link"></i>
                    <span class="grow break-all">$unsafe{details.displayUrl()}</span>
                </a>
            </div>
        @endfor
    </div>
    @endif

    @if (model.hasB())
    <div class="border rounded bg-white flex flex-col overflow-hidden mx-4">
        <div class="flex space-x-2 flex-row place-items-baseline bg-margeblue text-white p-2  text-md">
            <span class="fas fa-globe"></span>
            <a href="/site/${model.domainB()}">${model.domainB()}</a>
            <span class="grow"></span>
            <a rel="nofollow noopener external" href="${new EdgeDomain(model.domainB()).toRootUrlHttps().toString()}" class="fa-solid fa-arrow-up-right-from-square" ></a>
        </div>

        @for (UrlDetails details : model.bToA())
            <div class="p-2 font-medium text-sm text-gray-800 mx-2 mt-2">$unsafe{details.displayTitle()}</div>
            <div class="p-2 mx-2 text-gray-700 text-sm">
                $unsafe{details.displayDescription()}
            </div>
            <div class="p-2 text-sm border-b pb-6">
                <a rel="external noopener nofollow" href="${details.url.toString()}" class="mx-3 text-liteblue flex space-x-2 place-items-baseline hyphens-auto">
                    <i class="fa fa-link"></i>
                    <span class="grow break-all">$unsafe{details.displayUrl()}</span>
                </a>
            </div>
        @endfor
    </div>
    @endif

</div>


@template.part.footerLegal()

</body>
</html>