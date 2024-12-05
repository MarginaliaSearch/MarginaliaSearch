@import nu.marginalia.api.domains.model.SimilarDomain
@import java.util.List

@param String title
@param String domainName
@param List<SimilarDomain> list

@if (!list.isEmpty())

    <div class="bg-white shadow-sm rounded overflow-hidden border">
        <div class="px-4 py-2 bg-margeblue text-white border-b border-gray-200">
            <h2 class="text-xs font-semibold">${title}</h2>
        </div>

        <div class="overflow-x-auto">
            <table class="min-w-full divide-y divide-gray-200">
                <thead>
                <tr class="bg-gray-50">
                    <th scope="col" class="px-2 py-1 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Link</th>
                    <th scope="col" class="px-2 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Rank</th>
                    <th scope="col" class="px-2 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Domain</th>
                    <th scope="col" class="px-2 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Similarity</th>
                </tr>
                </thead>
                <tbody class="bg-white divide-y divide-gray-200 text-xs">
                @for(SimilarDomain item : list)
                    <tr class="hover:bg-gray-50">
                        <td class="px-3 py-3 whitespace-nowrap">
                            @if(item.linkType().isLinked())
                                <a href="/crosstalk/?domains=${domainName},${item.url().getDomain().toString()}"
                                   class="text-liteblue ${item.linkType().faIcon()}"
                                   title="${item.linkType().getDescription()}">
                                </a>
                            @endif
                        </td>
                         <td class="px-3 py-3 whitespace-nowrap">
                            <div class="flex items-center">
                <span title="${item.rank()}%" class="text-sm text-gray-600">
                  $unsafe{item.getRankSymbols()}
                </span>
                            </div>
                        </td>

                        <td class="px-3 py-3 whitespace-nowrap">
                            <a href="/site/${item.url().getDomain().toString()}?view=similar"
                               rel="external noopener nofollow"
                               class="text-liteblue hover:text-liteblue">
                                ${item.url().getDomain().toString()}
                            </a>
                        </td>

                        <td class="px-3 py-3">
                            <div class="w-16">
                                <div class="bg-gray-200 rounded-full h-2 overflow-hidden">
                                    <div class="bg-margeblue h-2 rounded-full"
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
