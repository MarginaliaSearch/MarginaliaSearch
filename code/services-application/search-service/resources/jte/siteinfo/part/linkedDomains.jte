@import nu.marginalia.api.domains.model.SimilarDomain
@import java.util.List

@param String title
@param String domainName
@param List<SimilarDomain> list

@if (!list.isEmpty())

    <div class="bg-white dark:bg-gray-800 shadow-sm rounded overflow-hidden border dark:border-gray-600">
        <div class="px-4 py-2 bg-margeblue text-white border-b border-gray-200 dark:border-gray-600 flex place-items-baseline">
            <h2 class="text-md">${title}</h2>
            <div class="grow"></div>
            <button class="hide-filter-button fas fa-close"></button>
        </div>

        <div class="overflow-x-auto overflow-hidden">
            <table class="min-w-full divide-y divide-gray-200 dark:divide-gray-600">
                <thead>
                <tr class="bg-gray-50 dark:bg-gray-700">
                    <th scope="col" class="px-2 py-2 text-left text-xs font-medium text-gray-500 dark:text-gray-100 uppercase tracking-wider">Link</th>
                    <th scope="col" class="px-2 py-2 text-left text-xs font-medium text-gray-500 dark:text-gray-100 uppercase tracking-wider hidden sm:inline">Rank</th>
                    <th scope="col" class="px-2 py-2 text-left text-xs font-medium text-gray-500 dark:text-gray-100 uppercase tracking-wider">Domain</th>
                    <th scope="col" class="px-2 py-2 text-left text-xs font-medium text-gray-500 dark:text-gray-100 uppercase tracking-wider">Similarity</th>
                </tr>
                </thead>
                <tbody class="bg-white dark:bg-gray-800 divide-y divide-gray-200 dark:divide-gray-600 text-xs">
                @for(SimilarDomain item : list)
                    <tr class="hover:bg-gray-50 dark:hover:bg-gray-700">
                        <td class="px-3 py-6 md:py-3 whitespace-nowrap">
                            @if(item.linkType().isLinked())
                                <a href="/crosstalk/?domains=${domainName},${item.url().getDomain().toString()}"
                                   class="text-liteblue dark:text-blue-200 ${item.linkType().faIcon()}"
                                   title="${item.linkType().getDescription()}">
                                </a>
                            @endif
                        </td>
                         <td class="px-3 py-6 sm:py-3 whitespace-nowrap hidden sm:inline">
                            <div class="flex items-center">
                                <span title="${item.rank()}%" class="text-sm text-gray-600 dark:text-yellow-200">
                                  $unsafe{item.getRankSymbols()}
                                </span>
                            </div>
                        </td>

                        <td class="px-3 py-6 sm:py-3 whitespace-nowrap">
                            <a href="/site/${item.url().getDomain().toString()}?view=similar"
                               rel="external noopener nofollow"
                               class="text-liteblue dark:text-blue-200 dark:text-blue-200">
                                ${item.url().getDomain().toString()}
                            </a>
                        </td>

                        <td class="px-3 py-6 sm:py-3">
                            <div class="w-16">
                                <div class="dark:bg-gray-600 bg-gray-200 rounded-full h-2 overflow-hidden">
                                    <div class="bg-margeblue dark:bg-blue-400 h-2 rounded-full"
                                         aria-valuenow="${item.relatedness()}"
                                         aria-valuemin="0"
                                         aria-valuemax="100"
                                         aria-roledescription="meter"
                                         style="width: ${item.relatedness()}%">
                                    </div>
                                </div>
                            </div>
                        </td>
                    </tr>
                @endfor
                </tbody>
            </table>
        </div>
    </div>
@endif
