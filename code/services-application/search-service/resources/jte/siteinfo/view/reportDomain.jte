@import nu.marginalia.search.svc.SearchFlagSiteService
@import nu.marginalia.search.svc.SearchSiteInfoService.*

@param ReportDomain reportDomain

<div class="flex-col mx-auto">
<div class="max-w-2xl mx-auto bg-white dark:bg-gray-800 border dark:border-gray-600 rounded overflow-auto shadow-sm my-4 space-y-4 w-full">
    <div class="px-4 py-2 bg-margeblue text-white border-b border-gray-200 dark:border-gray-800">
        <h2 class="text-md">Report Domain Issue</h2>
    </div>

    @if (reportDomain.submitted())
        <div class="mx-4 pb-4">Your complaint has been submitted and will be reviewed in a few weeks.
            For urgent issues, email <a href="mailto:kontakt@marginalia.nu" class="text-blue-600 dark:text-blue-200 hover:underline">kontakt@marginalia.nu</a>
            instead of using this form.
        </div>
    @else
        <form class="space-y-6 p-4" method="post">
            <div>
                <label class="block text-sm font-medium text-slate-700 dark:text-gray-200 mb-1">
                    Category
                </label>
                <select required name="category" class="w-full px-3 py-2 bg-white dark:bg-black border dark:border-gray-600 border-slate-300 rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                    <option value="">Select issue type...</option>
                    @for (SearchFlagSiteService.CategoryItem item : SearchFlagSiteService.categories)
                    <option value="${item.categoryName()}">${item.categoryDesc()}</option>
                    @endfor
                </select>
            </div>

            <div>
                <label class="block text-sm font-medium text-slate-700 dark:text-gray-200  mb-1">
                    Description
                </label>
                <textarea name="description" class="w-full h-32 w-64 px-3 py-2 bg-white dark:bg-black border dark:border-gray-600 border-slate-300 rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500" placeholder="Describe the issue..."></textarea>
            </div>

            <div>
                <label class="block text-sm font-medium text-slate-700 dark:text-gray-200 mb-1">
                    Search Query (Optional)
                </label>
                <input name="samplequery"
                        type="text"
                        class="w-full px-3 py-2 bg-white border dark:border-gray-600  dark:bg-black  border-slate-300 rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                        placeholder="Enter search query..."
                />
            </div>

            <div class="space-y-4">
                <button
                        type="submit"
                        class="px-4 py-2 dark:bg-margeblue dark:hover:bg-margeblue bg-blue-600 text-white font-medium rounded-md hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500"
                >
                    Submit Report
                </button>

                <p class="text-sm text-slate-600 dark:text-gray-200 ">
                    For urgent issues, email <a href="mailto:kontakt@marginalia.nu" class="text-liteblue dark:text-blue-200 hover:underline">kontakt@marginalia.nu</a>
                    instead of using this form.
                </p>
            </div>
        </form>
    @endif
</div>

@if (!reportDomain.complaints().isEmpty())
<div class="max-w-2xl mx-auto bg-slate-50 dark:bg-gray-800 rounded overflow-auto shadow-sm my-4 w-full">
    <div class="px-4 py-2 bg-margeblue text-white border-b border-gray-200 dark:border-gray-800">
        <h2 class="text-md">Existing Complaints</h2>
    </div>

    <div class="overflow-x-auto">
        <table class="min-w-full divide-y divide-gray-200">
            <thead>
            <tr class="bg-gray-700">
                <th scope="col" class="px-2 py-1 text-left text-xs font-medium text-gray-500 dark:text-gray-100 uppercase tracking-wider">Category</th>
                <th scope="col" class="px-2 py-2 text-left text-xs font-medium text-gray-500 dark:text-gray-100 uppercase tracking-wider">Timestamp</th>
                <th scope="col" class="px-2 py-2 text-left text-xs font-medium text-gray-500 dark:text-gray-100 uppercase tracking-wider">Reviewed</th>
                <th scope="col" class="px-2 py-2 text-left text-xs font-medium text-gray-500 dark:text-gray-100 uppercase tracking-wider">Decision</th>
            </tr>
            </thead>
            <tbody class="bg-white dark:bg-gray-800 divide-y divide-gray-200 dark:divide-gray-600 text-xs">
                @for (var complaint : reportDomain.complaints())

                <tr>
                    <td class="px-3 py-3 whitespace-nowrap">
                        ${complaint.category()}
                    </td>
                    <td class="px-3 py-3 whitespace-nowrap">
                        ${complaint.submitTime()}
                    </td>
                    <td class="px-3 py-3 whitespace-nowrap">
                        @if(complaint.isReviewed())
                            <i class="fas fa-check"></i>
                        @endif
                    </td>
                    <td class="px-3 py-3 whitespace-nowrap">
                        ${complaint.decision()}
                    </td>
                </tr>
                @endfor
            </tbody>
        </table>
    </div>
</div>
@endif

</div>
<!-- -->
