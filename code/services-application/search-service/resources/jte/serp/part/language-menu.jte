@import java.util.List
@import java.util.Objects
@import nu.marginalia.language.model.LanguageDefinition
@import nu.marginalia.search.model.SearchParameters

@param SearchParameters parameters
@param List<LanguageDefinition> languageDefinitions

<aside id="language-menu" class="fixed inset-0 z-50 flex justify-center bg-white/90 dark:bg-black/90 backdrop-blur-sm hidden">
    <button id="hide-language-button" class="fixed bottom-10 right-5 text-sm bg-margeblue text-white p-4 rounded-xl active:text-slate-200">
        <i class="fas fa-close mr-4"></i> Close
    </button>
    <div class="flex mx-auto flex-col my-10 px-4 max-w-xl">
        <div class="text-xl my-4"><i class="fas fa-globe-europe mr-2 text-margeblue dark:text-slate-200"></i>Language</div>
        <ol>
        @for (LanguageDefinition languageDefinition: languageDefinitions)
            <li>
                <a href="${parameters.withLanguage(languageDefinition.isoCode()).renderUrl()}" class="flex-1 p-2 my-1 rounded flex flex-col place-items-center has-[:checked]:bg-gray-100 has-[:checked]:text-slate-900 dark:has-[:checked]:bg-gray-800 dark:has-[:checked]:text-slate-100 hover:bg-gray-50 dark:hover:bg-gray-800 text-margeblue dark:text-slate-200 outline-1 active:outline border border-gray-700">
                    @if (Objects.equals(parameters.languageIsoCode(), languageDefinition.isoCode()))
                        <input type="checkbox" checked class="sr-only" aria-checked="true" />
                    @else
                        <input type="checkbox" class="sr-only" aria-checked="false" />
                    @endif
                    ${languageDefinition.displayName()}
                </a>
            </li>
        @endfor
        </ol>
        <div class="my-4 text-xs">
            Multiple language support is currently in a public testing phase,
            and does not yet work as well as the English portion of the search engine.
            The UI will also be streamlined when the functionality starts to work better.
            <p class="py-2"></p>
            Please report any issues at the <a class="underline text-liteblue dark:text-blue-200" href="https://github.com/MarginaliaSearch/MarginaliaSearch/issues">GitHub issue tracker</a>,
            or by emailing <a class="underline text-liteblue dark:text-blue-200" href="mailto:contact@marginalia-search.com">contact@marginalia-search.com</a>,
            or by visiting the <a class="underline text-liteblue dark:text-blue-200" href="https://chat.marginalia.nu">project discord</a>.
            <p class="py-2"></p>
            If you speak languages other than English and Swedish, know a bit of programming and grammar, then help is also welcome!
        </div>
        <div class="flex-grow"></div>
    </div>
</aside>