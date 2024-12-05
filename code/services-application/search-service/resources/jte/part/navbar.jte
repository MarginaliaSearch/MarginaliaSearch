@import nu.marginalia.search.model.NavbarModel

@param NavbarModel navbar

<header class="bg-margeblue text-white">
    <div class="max-w-[1400px] mx-auto">
        <nav>
            <div class="px-4">
                <div class="flex flex-row text-sm gap-1 xs:gap-2">
                    @for (var item : navbar.first().entries())

                        <a href="${item.url()}" class="has-[:checked]:underline py-2 px-2 hover:text-slate-200">
                            @if (item.active())
                                <input type="checkbox" checked readonly class="sr-only" />
                            @endif
                            ${item.name()}
                        </a>

                    @endfor
                    <div class="grow"></div>
                    @for (var item : navbar.second().entries())
                        <a href="${item.url()}" class="has-[:checked]:underline  py-2 px-2 hover:text-slate-200">
                            @if (item.active())
                                <input type="checkbox" checked readonly class="sr-only" />
                            @endif
                            ${item.name()}
                        </a>
                    @endfor
                </div>
            </div>
        </nav>
    </div>
</header>