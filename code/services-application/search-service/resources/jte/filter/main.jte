@import nu.marginalia.api.searchquery.model.query.SpecificationLimitType
@import nu.marginalia.functions.searchquery.searchfilter.model.SearchFilterSpec
@import nu.marginalia.search.model.NavbarModel
@import java.util.Map

@param NavbarModel navbar
@param SearchFilterSpec filter

<!DOCTYPE html>
<html lang="en">

@template.part.head(title =  "Filter Settings - Marginalia Search")

<body class="min-h-screen bg-bgblue dark:bg-gray-900 dark:text-white font-sans " >

@template.part.navbar(navbar = navbar)

<header class="border-b border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 shadow-md">
    <div class="max-w-[1400px] mx-auto p-4">
        <div class="flex place-items-baseline space-x-2">
            <div class="hidden sm:block md:w-32 md:mr-16 md:ml-16"><h1 class="text-md sm:text-xl mr-8 font-serif whitespace-nowrap"><a href="/">Marginalia Search</a></h1></div>
            <span class="text-gray-900 dark:text-white break-none text-sm sm:text-md rounded-sm block p-2.5">
                Filter Settings
            </span>
            <span class="grow"></span>
            <span>
                <button onclick="(async () => { try {

                    const filters = localStorage.getItem('custom-filter')
                    if (filters === null) {
                        alert('No filter configured!')
                        return false
                    }

                    const rsp = await fetch('/filters/format', {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'text/xml'
                        },
                        body: filters
                    })

                    if (rsp.ok) {
                        const now = new Date();

                        const year = now.getFullYear().toString().slice(-2);
                        const month = String(now.getMonth() + 1).padStart(2, '0');
                        const day = String(now.getDate()).padStart(2, '0');
                        const hours = String(now.getHours()).padStart(2, '0');
                        const minutes = String(now.getMinutes()).padStart(2, '0');
                        const seconds = String(now.getSeconds()).padStart(2, '0');

                        @raw
                        const filename =  `marginalia-filter-${year}-${month}-${day}_${hours}${minutes}${seconds}.xml`;
                        @endraw

                        const url = window.URL.createObjectURL(await rsp.blob());
                        const a = document.createElement('a')
                        a.href = url
                        a.download = filename
                        a.click()
                        window.URL.revokeObjectURL(url)
                    }
                    else {
                        console.log(rsp)
                        alert('Something went wrong: ' + await rsp.text())
                    }
                    return false
                } catch (error) {
                    console.log(error)
                    return false
                }})();"
                        title="Export as XML file"
                        class="flex gap-2 place-items-baseline flex-1 ml-1 py-2 px-2 rounded flex space-x-2 hover:bg-gray-50 dark:hover:bg-gray-950 bg-white dark:bg-gray-900 dark:border dark:border-gray-600 text-margeblue dark:text-slate-200 outline-1 active:outline">
                    <i class="fas fa-download"></i>
                    Export XML
                </button>

            </span>
            <span>
                    <label for="upload-field" title="Upload XML representation of filter"
                        class="flex gap-2 place-items-baseline flex-1 ml-1 py-2 px-2 rounded flex space-x-2 hover:bg-gray-50 dark:hover:bg-gray-950 bg-white dark:bg-gray-900 dark:border dark:border-gray-600 text-margeblue dark:text-slate-200 outline-1 active:outline">
                    <i class="fas fa-upload" onclick=""></i> Import XML
                    </label>
                    <input id="upload-field" type="file" title="Import from XML file" class="sr-only"
                            onchange="(async (event) => { try {
                                const content = await event.files[0].text()
                                console.log(content)

                                const rsp = await fetch('/filters/format', {
                                    method: 'POST',
                                    headers: {
                                        'Content-Type': 'text/xml'
                                    },
                                    body: content
                                })

                                if (rsp.ok) {
                                    const text = await rsp.text()
                                    console.log('New filter: ' + text)
                                    localStorage.setItem('custom-filter', text)
                                    window.location.reload()
                                }
                                else {
                                    console.log(rsp)
                                    alert('Something went wrong')
                                }

                                return false
                            }
                            catch (error) {
                                console.log(error)
                                alert('Something went wrong')
                                return false
                            }})(this);">
            </span>
            <span class="px-2 border-left"></span>
            <span>
                <button type="button"
                        onclick="(async () => { try {
                    const rsp = await fetch('/filters/format', {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/json'
                        },
                        body: filtersAsJson()
                    })

                    if (rsp.ok) {
                        const text = await rsp.text()
                        console.log('New filter: ' + text)
                        localStorage.setItem('custom-filter', text)
                        alert('Changed saved to web browser storage.')
                    }
                    else {
                        console.log(rsp)
                        alert('Something went wrong')
                    }
                    return false
                } catch (error) {
                    console.log(error)
                    return false
                }})();"
                        title="Save Changes"
                        class="flex-1 ml-1 py-2 px-2 rounded flex space-x-2 hover:bg-green-50 dark:hover:bg-green-950 bg-white dark:bg-green-900 dark:border dark:border-green-600 text-margeblue dark:text-slate-200 outline-1 active:outline">
                    Save
                </button>

            </span>
        </div>
    </div>
</header>

<div class="max-w-[1000px] mx-auto columns-1 md:columns-2 gap-1">
    <div class="flex-1 p-4 space-y-4 w-full">
        <div class="flex border border-gray-300  dark:border-gray-600 rounded bg-white dark:bg-gray-800 flex-col space-y-4 pb-4 overflow-hidden md:max-w-lg" >
            <div class="flex place-items-center space-x-2 p-2 text-md border-b dark:border-gray-600 bg-margeblue text-white">
            Terms
            </div>

            <term id="term-prototype"
                  class="border hidden border dark:border-gray-600 border-gray-300 px-2 py-1 rounded-sm bg-gray-900 flex place-items-baseline gap-2"><span class="font-mono">&nbsp;</span><i class="fas fa-remove cursor-pointer"></i></term>

            <div class="mx-3 flex place-items-baseline space-x-2 p-2 bg-gray-100 dark:bg-gray-600 rounded">
                <i class="fas fa-question-circle"></i>
                <span class="grow">Required Terms</span>
            </div>

            <div class="mx-4 gap-1 flex flex-wrap " id="required-terms"></div>
            <script language="javascript">
                function addRequiredTerm(term) {
                    const terms = document.getElementById('required-terms');
                    const prototype = document.getElementById('term-prototype');
                    const newTermElem = prototype.cloneNode(true);

                    newTermElem.classList.remove('hidden')
                    newTermElem.firstChild.textContent = term;
                    newTermElem.setAttribute('data-term', term);
                    newTermElem.lastChild.addEventListener('click', (evt) => { terms.removeChild(newTermElem); });
                    terms.append(newTermElem);
                }

                setTimeout(() => {
                    @for (String term : filter.termsRequire())
                    addRequiredTerm("${term}");
                    @endfor
                });
            </script>

            <div class="hidden border-red-400"></div>

            <form onsubmit="try {
                        const terms = document.getElementById('required-terms');
                        const textElem = document.getElementById('required-term-text');
                        const errorElem = document.getElementById('add-term-error');
                        const newTerm = textElem.value;

                        @raw
                        const existingTerm = terms.querySelector(`term[data-term='${CSS.escape(newTerm)}']`)
                        @endraw

                        if (!textElem.checkValidity() || newTerm === '') {
                            errorElem.classList.remove('hidden')
                            errorElem.textContent = 'Term must not contain spaces'

                            return false;
                        }

                        if (existingTerm !== null) {
                            errorElem.classList.remove('hidden')
                            errorElem.textContent = 'Term is already in the list'
                            return false;
                        }

                        addRequiredTerm(textElem.value);
                        errorElem.classList.add('hidden')
                        return false;
                    } catch (error) {
                        console.log(error)
                        return false;
                    }">
            <div class="flex flex-row px-5">
            <input id="required-term-text" type="text" pattern="[\w\d:_\.]+" placeholder="term, e.g. cats"
                    class="invalid:border-red-400 shadow-inner flex-grow dark:bg-black dark:text-gray-100 bg-gray-50 border dark:border-gray-600 border-gray-300 text-gray-900 text-sm rounded-sm block w-full p-2.5"
            />
            <button type="submit"
                    title="Add term"
                    class="flex-1 ml-1 py-2 px-2 rounded flex space-x-2 hover:bg-gray-50 dark:hover:bg-gray-950 bg-white dark:bg-gray-900 dark:border dark:border-gray-600 text-margeblue dark:text-slate-200 outline-1 active:outline">
                Add
            </button>
            </div>
                <div id="add-term-error" class="hidden text-red-500 text-sm px-4"></div>
            </form>
            <div class="mx-3 flex place-items-baseline space-x-2 p-2 bg-gray-100 dark:bg-gray-600 rounded">
                <i class="fas fa-question-circle"></i>
                <span class="grow">Forbidden Terms</span>
            </div>

            <div class="mx-4 gap-1 flex flex-wrap " id="forbidden-terms">
            </div>

            <script language="javascript">
                function addForbiddenTerm(term) {
                    const terms = document.getElementById('forbidden-terms');
                    const prototype = document.getElementById('term-prototype');
                    const newTermElem = prototype.cloneNode(true);

                    newTermElem.classList.remove('hidden')
                    newTermElem.firstChild.textContent = term;
                    newTermElem.setAttribute('data-term', term);
                    newTermElem.lastChild.addEventListener('click', (evt) => { terms.removeChild(newTermElem); });
                    terms.append(newTermElem);
                }


                setTimeout(() => {
                    @for (String term : filter.termsExclude())
                    addForbiddenTerm("${term}");
                    @endfor
                });
            </script>

            <form onsubmit="try {
                            const terms = document.getElementById('forbidden-terms');
                            const textElem = document.getElementById('forbidden-term-text');
                            const errorElem = document.getElementById('remove-term-error');
                            const newTerm = textElem.value;

                            @raw
                            const existingTerm = terms.querySelector(`term[data-term='${CSS.escape(newTerm)}']`)
                            @endraw

                            if (!textElem.checkValidity() || newTerm === '') {
                                errorElem.classList.remove('hidden')
                                errorElem.textContent = 'Term must not contain spaces'

                                return false;
                            }

                            if (existingTerm !== null) {
                                errorElem.classList.remove('hidden')
                                errorElem.textContent = 'Term is already in the list'
                                return false;
                            }

                            addForbiddenTerm(textElem.value)

                            errorElem.classList.add('hidden')
                            return false;
                        } catch (error) {
                            console.log(error)
                            return false;
                        }">
            <div class="flex flex-row px-5">
                <input id="forbidden-term-text" type="text" pattern="[\w\d:_\.]+" placeholder="term, e.g. dogs"
                       class="invalid:border-red-400 shadow-inner flex-grow dark:bg-black dark:text-gray-100 bg-gray-50 border dark:border-gray-600 border-gray-300 text-gray-900 text-sm rounded-sm block w-full p-2.5"
                />
                <button
                        title="Add term"
                        class="flex-1 ml-1 py-2 px-2 rounded flex space-x-2 hover:bg-gray-50 dark:hover:bg-gray-950 bg-white dark:bg-gray-900 dark:border dark:border-gray-600 text-margeblue dark:text-slate-200 outline-1 active:outline">Add</button>
            </div>
                <div id="remove-term-error" class="hidden text-red-500 text-sm px-4"></div>
            </form>
            <div class="mx-3 flex place-items-baseline space-x-2 p-2 bg-gray-100 dark:bg-gray-600 rounded">
                <i class="fas fa-question-circle"></i>
                <span class="grow">Weighted Terms</span>
            </div>

            <div class="mx-4 gap-1 flex flex-wrap " id="priority-terms">
            </div>
            <script language="javascript">
                function addPriorityTerm(term, value) {
                    const terms = document.getElementById('priority-terms');
                    const prototype = document.getElementById('term-prototype');
                    const newTermElem = prototype.cloneNode(true);

                    newTermElem.classList.remove('hidden')
                    newTermElem.firstChild.textContent = term + "(" + value +")";
                    newTermElem.setAttribute('data-term', term);
                    newTermElem.setAttribute('data-val', value);
                    newTermElem.lastChild.addEventListener('click', (evt) => { terms.removeChild(newTermElem); });
                    terms.append(newTermElem);
                }

                setTimeout(() => {
                    @for (Map.Entry<String, Float> term : filter.termsPromote())
                    addPriorityTerm("${term.getKey()}", ${term.getValue()});
                    @endfor
                });

            </script>
            <div class="text-sm px-5">
                Modify the ranking of results containing the selected terms.  Positive numbers increases the ranking,
                negative number decreases it.
            </div>
            <form onsubmit="try {

                            const terms = document.getElementById('priority-terms');
                            const textElem = document.getElementById('priority-term-text');
                            const prioElem = document.getElementById('priority-term-val');
                            const errorElem = document.getElementById('priority-term-error');
                            const newTerm = textElem.value;

                            @raw
                            const existingTerm = terms.querySelector(`term[data-term='${CSS.escape(newTerm)}']`)
                            @endraw

                            if (!textElem.checkValidity()) {
                                errorElem.classList.remove('hidden')
                                errorElem.textContent = 'Term must not contain spaces'

                                return false;
                            }
                            if (!prioElem.checkValidity() || isNaN(prioElem.value) || prioElem.value === '') {
                                errorElem.classList.remove('hidden')
                                errorElem.textContent = 'Invalid weight'

                                return false;
                            }

                            if (existingTerm !== null) {
                                existingTerm.setAttribute('data-val', prioElem.value)
                                existingTerm.firstChild.textContent = textElem.value + '(' + prioElem.value + ')';
                                errorElem.classList.add('hidden')
                                return false;
                            }

                            addPriorityTerm(textElem.value, prioElem.value)

                            errorElem.classList.add('hidden')
                            return false;
                        } catch (error) {
                            console.log(error)
                            return false
                        }">
                <div class="flex flex-row px-5 gap-1">
                    <input id="priority-term-text" type="text" pattern="[\w\d\:_\.]+" placeholder="term, e.g. birds"
                           class="invalid:border-red-400 shadow-inner flex-grow dark:bg-black dark:text-gray-100 bg-gray-50 border dark:border-gray-600 border-gray-300 text-gray-900 text-sm rounded-sm block w-full p-2.5"
                    />
                    <input id="priority-term-val" type="number" step="0.1" placeholder="0"
                           class="shadow-inner px-2 dark:bg-black dark:text-gray-100 bg-gray-50 border dark:border-gray-600 border-gray-300 text-gray-900 text-sm rounded-sm block w-full p-2.5"
                    />
                    <button type="submit"
                            title="Add term"
                            class="flex-1 py-2 px-2 rounded flex space-x-2 hover:bg-gray-50 dark:hover:bg-gray-950 bg-white dark:bg-gray-900 dark:border dark:border-gray-600 text-margeblue dark:text-slate-200 outline-1 active:outline">Add</button>
                </div>
                <div id="priority-term-error" class="hidden text-red-500 text-sm px-4"></div>
            </form>
        </div>
    </div>
    <div class="flex-1 p-4 space-y-4 mx-auto w-full">
        <div class="flex border border-gray-300  dark:border-gray-600 rounded bg-white dark:bg-gray-800 flex-col space-y-4 pb-4 overflow-hidden md:max-w-lg" >
            <div class="flex place-items-center space-x-2 p-2 text-md border-b dark:border-gray-600 bg-margeblue text-white">
                Limits
            </div>
            <div class="mx-3 flex place-items-baseline space-x-2 p-2 bg-gray-100 dark:bg-gray-600 rounded">
                <i class="fas fa-question-circle"></i>
                <span class="grow">Year</span>
            </div>
            <div class="text-sm px-5">
                Filter results based on estimated publication year.
            </div>
            <form>
                <div class="flex flex-row px-5 gap-2">
                    <select id="limit-year-type" type="select" name="year"
                            class="shadow-inner flex-grow px-2 dark:bg-black dark:text-gray-100 bg-gray-50 border dark:border-gray-600 border-gray-300 text-gray-900 text-sm rounded-sm block w-full p-2.5"
                    >
                        <option selected="${filter.year().type() == SpecificationLimitType.NONE}">none</option>
                        <option selected="${filter.year().type() == SpecificationLimitType.EQUALS}">equals</option>
                        <option selected="${filter.year().type() == SpecificationLimitType.LESS_THAN}">less than</option>
                        <option selected="${filter.year().type() == SpecificationLimitType.GREATER_THAN}">greater than</option>
                    </select>
                    <input id="limit-year-val" type="number" step="1" placeholder="0" min="1997" value="${filter.year().value()}"
                           class="shadow-inner px-2 dark:bg-black dark:text-gray-100 bg-gray-50 border dark:border-gray-600 border-gray-300 text-gray-900 text-sm rounded-sm block w-full p-2.5"
                    />
                </div>
            </form>

            <div class="mx-3 flex place-items-baseline space-x-2 p-2 bg-gray-100 dark:bg-gray-600 rounded">
                <i class="fas fa-question-circle"></i>
                <span class="grow">Size</span>
            </div>
            <div class="text-sm px-5">
                Limit the size of the website, in number of documents.
            </div>
            <form>
                <div class="flex flex-row px-5 gap-2">
                    <select id="limit-size-type" type="select" name="size"
                            class="shadow-inner flex-grow px-2 dark:bg-black dark:text-gray-100 bg-gray-50 border dark:border-gray-600 border-gray-300 text-gray-900 text-sm rounded-sm block w-full p-2.5"
                    >
                        <option selected="${filter.size().type() == SpecificationLimitType.NONE}">none</option>
                        <option selected="${filter.size().type() == SpecificationLimitType.LESS_THAN}">less than</option>
                        <option selected="${filter.size().type() == SpecificationLimitType.GREATER_THAN}">greater than</option>
                    </select>
                    <input id="limit-size-val" type="number" step="100" placeholder="0" min="0" max="10000" value="${filter.size().value()}"
                           class="shadow-inner px-2 dark:bg-black dark:text-gray-100 bg-gray-50 border dark:border-gray-600 border-gray-300 text-gray-900 text-sm rounded-sm block w-full p-2.5"
                    />
                </div>
            </form>


            <div class="mx-3 flex place-items-baseline space-x-2 p-2 bg-gray-100 dark:bg-gray-600 rounded">
                <i class="fas fa-question-circle"></i>
                <span class="grow">Rank</span>
            </div>
            <div class="text-sm px-5">
                Limit the ranking of the website, pagerank.
            </div>
            <form>
                <div class="flex flex-row px-5 gap-2">
                    <select id="limit-rank-type" type="select" name="rank"
                            class="shadow-inner flex-grow px-2 dark:bg-black dark:text-gray-100 bg-gray-50 border dark:border-gray-600 border-gray-300 text-gray-900 text-sm rounded-sm block w-full p-2.5"
                    >
                        <option selected="${filter.rank().type() == SpecificationLimitType.NONE}">none</option>
                        <option selected="${filter.rank().type() == SpecificationLimitType.LESS_THAN}">less than</option>
                        <option selected="${filter.rank().type() == SpecificationLimitType.GREATER_THAN}">greater than</option>
                    </select>
                    <input id="limit-rank-val" type="number" step="100" placeholder="0" min="0" max="10000" value="${filter.rank().value()}"
                           class="shadow-inner px-2 dark:bg-black dark:text-gray-100 bg-gray-50 border dark:border-gray-600 border-gray-300 text-gray-900 text-sm rounded-sm block w-full p-2.5"
                    />
                </div>
            </form>


            <div class="mx-3 flex place-items-baseline space-x-2 p-2 bg-gray-100 dark:bg-gray-600 rounded">
                <i class="fas fa-question-circle"></i>
                <span class="grow">Quality</span>
            </div>
            <div class="text-sm px-5">
                Limit the amount of javascript in the website, in number of documents.
            </div>
            <form>
                <div class="flex flex-row px-5 gap-2">
                    <select id="limit-quality-type" type="select" name="quality"
                            class="shadow-inner flex-grow px-2 dark:bg-black dark:text-gray-100 bg-gray-50 border dark:border-gray-600 border-gray-300 text-gray-900 text-sm rounded-sm block w-full p-2.5"
                    >
                        <option selected="${filter.quality().type() == SpecificationLimitType.NONE}">none</option>
                        <option selected="${filter.quality().type() == SpecificationLimitType.LESS_THAN}">less than</option>
                        <option selected="${filter.quality().type() == SpecificationLimitType.GREATER_THAN}">greater than</option>
                    </select>
                    <input id="limit-quality-val" type="number" step="0.1" placeholder="0" min="0" max="15" value="${filter.quality().value()}"
                           class="shadow-inner px-2 dark:bg-black dark:text-gray-100 bg-gray-50 border dark:border-gray-600 border-gray-300 text-gray-900 text-sm rounded-sm block w-full p-2.5"
                    />
                </div>
            </form>
        </div>

    </div>
    <div class="flex-1 p-4 space-y-4 mx-auto w-full">
        <div class="flex border border-gray-300  dark:border-gray-600 rounded bg-white dark:bg-gray-800 flex-col space-y-4 pb-4 overflow-hidden md:max-w-lg" >

            <div class="flex place-items-center space-x-2 p-2 text-md border-b dark:border-gray-600 bg-margeblue text-white">
                Domains
            </div>

            <domain id="domain-prototype"
                  class="border hidden border dark:border-gray-600 border-gray-300 px-2 py-1 rounded-sm bg-gray-900 flex place-items-baseline gap-2"><span class="font-mono">&nbsp;</span><i class="fas fa-remove cursor-pointer"></i></domain>


            <div class="mx-3 flex place-items-baseline space-x-2 p-2 bg-gray-100 dark:bg-gray-600 rounded">
                <i class="fas fa-question-circle"></i>
                <span class="grow">Search Domains</span>
            </div>

            <div class="mx-4 gap-1 flex flex-wrap " id="required-domains"></div>

            <script language="javascript">
                function addRequiredDomain(domain) {
                    const domains = document.getElementById('required-domains');
                    const prototype = document.getElementById('domain-prototype');
                    const newDomainElem = prototype.cloneNode(true);

                    newDomainElem.classList.remove('hidden')
                    newDomainElem.firstChild.textContent = domain;
                    newDomainElem.setAttribute('data-domain', domain);
                    newDomainElem.lastChild.addEventListener('click', (evt) => { domains.removeChild(newDomainElem); });
                    domains.append(newDomainElem);
                }
                setTimeout(() => {
                @for (String domain : filter.domainsInclude())
                    addRequiredDomain("${domain}");
                @endfor
                });
            </script>

            <form onsubmit="
                        try {
                            const domains = document.getElementById('required-domains');
                            const textElem = document.getElementById('required-domain-text');
                            const errorElem = document.getElementById('required-domain-error');
                            const newDomain = textElem.value;

                    @raw
                            const existingDomain = domains.querySelector(`domain[data-domain='${CSS.escape(newDomain)}']`)
                    @endraw

                            if (!textElem.checkValidity() || newDomain === '') {
                                errorElem.classList.remove('hidden')
                                errorElem.textContent = 'Domain must be all lower case and contain no spaces'

                                return false;
                            }

                            if (existingDomain !== null) {
                                errorElem.classList.remove('hidden')
                                errorElem.textContent = 'Domain is already in the list'

                                return false;
                            }

                            addRequiredDomain(newDomain)

                            errorElem.classList.add('hidden')

                            return false;
                        }
                        catch (error) {
                            console.log(error);
                            return false;
                        }
                    ">
                <div class="flex flex-row px-5">
                    <input id="required-domain-text" type="text" pattern="[*]?[\w\d\.\-]+" placeholder="e.g. en.wikipedia.org or *.neocities.org"
                           class="invalid:border-red-400 shadow-inner flex-grow dark:bg-black dark:text-gray-100 bg-gray-50 border dark:border-gray-600 border-gray-300 text-gray-900 text-sm rounded-sm block w-full p-2.5"
                    />
                    <button type="submit"
                            title="Add domain"
                            class="flex-1 ml-1 py-2 px-2 rounded flex space-x-2 hover:bg-gray-50 dark:hover:bg-gray-950 bg-white dark:bg-gray-900 dark:border dark:border-gray-600 text-margeblue dark:text-slate-200 outline-1 active:outline">Add</button>
                </div>
                <div id="required-domain-error" class="hidden text-red-500 text-sm px-4"></div>
            </form>


            <div class="mx-3 flex place-items-baseline space-x-2 p-2 bg-gray-100 dark:bg-gray-600 rounded">
                <i class="fas fa-question-circle"></i>
                <span class="grow">Excluded Domains</span>
            </div>

            <div class="mx-4 gap-1 flex flex-wrap " id="excluded-domains"></div>

            <script language="javascript">
                function addExcludedDomain(domain) {
                    const domains = document.getElementById('excluded-domains');
                    const prototype = document.getElementById('domain-prototype');
                    const newDomainElem = prototype.cloneNode(true);

                    newDomainElem.classList.remove('hidden')
                    newDomainElem.firstChild.textContent = domain;
                    newDomainElem.setAttribute('data-domain', domain);
                    newDomainElem.lastChild.addEventListener('click', (evt) => { domains.removeChild(newDomainElem); });
                    domains.append(newDomainElem);
                }

                setTimeout(() => {
                    @for (String domain : filter.domainsExclude())
                    addExcludedDomain("${domain}");
                    @endfor
                });
            </script>

             <form onsubmit="
                        try {
                            const domains = document.getElementById('excluded-domains');
                            const textElem = document.getElementById('excluded-domain-text');
                            const errorElem = document.getElementById('excluded-domain-error');
                            const newDomain = textElem.value;

                    @raw
                            const existingDomain = domains.querySelector(`domain[data-domain='${CSS.escape(newDomain)}']`)
                    @endraw

                            if (!textElem.checkValidity() || newDomain === '') {
                                errorElem.classList.remove('hidden')
                                errorElem.textContent = 'Domain must be all lower case and contain no spaces'

                                return false;
                            }

                            if (existingDomain !== null) {
                                errorElem.classList.remove('hidden')
                                errorElem.textContent = 'Domain is already in the list'

                                return false;
                            }

                            addExcludedDomain(newDomain)

                            errorElem.classList.add('hidden')

                            return false;
                        }
                        catch (error) {
                            console.log(error);
                            return false;
                        }
                    ">
                 <div class="flex flex-row px-5">
                    <input id="excluded-domain-text" type="text" pattern="[*]?[\w\d\.\-]+" placeholder="e.g. pinterest.com or *.tumblr.com"
                           class="invalid:border-red-400 shadow-inner flex-grow dark:bg-black dark:text-gray-100 bg-gray-50 border dark:border-gray-600 border-gray-300 text-gray-900 text-sm rounded-sm block w-full p-2.5"
                    />
                    <button
                            title="Add term"
                            class="flex-1 ml-1 py-2 px-2 rounded flex space-x-2 hover:bg-gray-50 dark:hover:bg-gray-950 bg-white dark:bg-gray-900 dark:border dark:border-gray-600 text-margeblue dark:text-slate-200 outline-1 active:outline">Add</button>
                 </div>
                 <div id="excluded-domain-error" class="hidden text-red-500 text-sm px-4"></div>
            </form>

            <div class="mx-3 flex place-items-baseline space-x-2 p-2 bg-gray-100 dark:bg-gray-600 rounded">
                <i class="fas fa-question-circle"></i>
                <span class="grow">Weighted Domains</span>
            </div>

            <div class="mx-4 gap-1 flex flex-wrap " id="priority-domains"></div>

            <script lang="javascript">
                function addPriorityDomain(domain, value) {
                    const domains = document.getElementById('priority-domains');
                    const prototype = document.getElementById('domain-prototype');
                    const newDomainElem = prototype.cloneNode(true);

                    newDomainElem.classList.remove('hidden')
                    newDomainElem.firstChild.textContent = domain + "(" + value + ")";
                    newDomainElem.setAttribute('data-domain', domain);
                    newDomainElem.setAttribute('data-val', value);
                    newDomainElem.lastChild.addEventListener('click', (evt) => { domains.removeChild(newDomainElem); });
                    domains.append(newDomainElem);
                }

                setTimeout(() => {
                    @for (Map.Entry<String, Float> domain : filter.domainsPromote())
                    addPriorityDomain("${domain.getKey()}", ${domain.getValue()});
                    @endfor
                });
            </script>
            <div class="text-sm px-5">
                Modify the ranking of results from the selected domains.  Positive numbers increases the ranking,
                negative number decreases it.
            </div>

            <form onsubmit="try {
                            const domains = document.getElementById('priority-domains');
                            const textElem = document.getElementById('priority-domain-text');
                            const prioElem = document.getElementById('priority-domain-value');
                            const errorElem = document.getElementById('priority-domain-error');
                            const newDomain = textElem.value;

                            @raw
                            const existingTerm = domains.querySelector(`domain[data-domain='${CSS.escape(newDomain)}']`)
                            @endraw

                            if (!textElem.checkValidity()) {
                                errorElem.classList.remove('hidden')
                                errorElem.textContent = 'Term must not contain spaces'

                                return false;
                            }
                            if (!prioElem.checkValidity() || isNaN(prioElem.value) || prioElem.value === '') {
                                errorElem.classList.remove('hidden')
                                errorElem.textContent = 'Invalid weight'

                                return false;
                            }

                            if (existingTerm !== null) {
                                existingTerm.setAttribute('data-val', prioElem.value)
                                existingTerm.firstChild.textContent = textElem.value + '(' + prioElem.value + ')';
                                errorElem.classList.add('hidden')
                                return false;
                            }

                            addPriorityDomain(textElem.value, prioElem.value)

                            errorElem.classList.add('hidden')
                            return false;
                        }
                        catch (error) {
                            console.log(error);
                            return false;
                        }
                    ">
                <div class="flex flex-row px-5 gap-1">
                    <input id="priority-domain-text" type="text" pattern="[*]?[\w\d\.\-]+" placeholder="e.g. en.wikipedia.org or *.neocities.org"
                           class="invalid:border-red-400 shadow-inner flex-grow dark:bg-black dark:text-gray-100 bg-gray-50 border dark:border-gray-600 border-gray-300 text-gray-900 text-sm rounded-sm block w-full p-2.5"
                    />
                    <input id="priority-domain-value" type="number" step="0.1" placeholder="0"
                           class="shadow-inner px-2 dark:bg-black dark:text-gray-100 bg-gray-50 border dark:border-gray-600 border-gray-300 text-gray-900 text-sm rounded-sm block w-full p-2.5"
                    />
                    <button
                            title="Add term"
                            class="flex-1 py-2 px-2 rounded flex space-x-2 hover:bg-gray-50 dark:hover:bg-gray-950 bg-white dark:bg-gray-900 dark:border dark:border-gray-600 text-margeblue dark:text-slate-200 outline-1 active:outline">Add</button>
                </div>
                <div id="priority-domain-error" class="hidden text-red-500 text-sm px-4"></div>
            </form>
        </div>
    </div>
    <div class="flex-1 p-4 space-y-4 mx-auto w-full">
        <div class="flex border border-gray-300  dark:border-gray-600 rounded bg-white dark:bg-gray-800 flex-col space-y-4 pb-4 overflow-hidden md:max-w-lg" >
            <div class="flex place-items-center space-x-2 p-2 text-md border-b dark:border-gray-600 bg-margeblue text-white">
                Query
            </div>

            <div class="mx-3 flex place-items-baseline space-x-2 p-2 bg-gray-100 dark:bg-gray-600 rounded">
                <i class="fas fa-question-circle"></i>
                <span class="grow">Temporal Bias</span>
            </div>

            <div class="text-sm px-5">
                Slightly skews the ranking to prefer documents of a particular age.
            </div>

            <div class="flex gap-2 flex-row mx-5">
            <label for="temporal-old" role="button" class="flex-1 py-2 pl-2 rounded flex space-x-2 dark:has-[:checked]:bg-gray-950 has-[:checked]:bg-gray-300 has-[:checked]:text-slate-900 dark:has-[:checked]:text-slate-100 hover:bg-gray-50 dark:hover:bg-gray-950 bg-white dark:bg-gray-900 dark:border dark:border-gray-600 text-margeblue dark:text-slate-200 outline-1 active:outline">
            <input value="old" type="radio" id="temporal-old" name="temporal" title="Prefer Old" class="sr-only" checked="${"OLD".equalsIgnoreCase(filter.temporalBias())}">
                Old
            </label>

            <label for="temporal-none" role="button" class="flex-1 py-2 pl-2 rounded flex space-x-2 dark:has-[:checked]:bg-gray-950 has-[:checked]:bg-gray-300 has-[:checked]:text-slate-900 dark:has-[:checked]:text-slate-100 hover:bg-gray-50 dark:hover:bg-gray-950 bg-white dark:bg-gray-900 dark:border dark:border-gray-600 text-margeblue dark:text-slate-200 outline-1 active:outline">
                <input value="none" type="radio" id="temporal-none" name="temporal" title="No Preference" class="sr-only"  checked="${"NONE".equalsIgnoreCase(filter.temporalBias())}">
                None
            </label>

            <label for="temporal-recent" role="button" class="flex-1 py-2 pl-2 rounded flex space-x-2 dark:has-[:checked]:bg-gray-950 has-[:checked]:bg-gray-300 has-[:checked]:text-slate-900 dark:has-[:checked]:text-slate-100 hover:bg-gray-50 dark:hover:bg-gray-950 bg-white dark:bg-gray-900 dark:border dark:border-gray-600 text-margeblue dark:text-slate-200 outline-1 active:outline">
                <input value="recent" type="radio" id="temporal-recent" name="temporal" title="Prefer Recent" class="sr-only" checked="${"RECENT".equalsIgnoreCase(filter.temporalBias())}">
                Recent
            </label>
            </div>
        </div>
    </div>
</div>


@template.part.footerLegal()

</body>
<script lang="javascript" src="/js/mobile-button.js"></script>
<script lang="javascript">
    function filtersAsJson() {
        const termsRequired = []
        const termsExcluded = []
        const termsPriority = []
        const termsPriorityWeights = []

        const domainsRequired = []
        const domainsExcluded = []
        const domainsPriority = []
        const domainsPriorityWeights = []

        document.getElementById('required-terms').querySelectorAll("term").forEach(elem => {
            termsRequired.push(elem.getAttribute('data-term'));
        })
        document.getElementById('forbidden-terms').querySelectorAll("term").forEach(elem => {
            termsExcluded.push(elem.getAttribute('data-term'));
        })
        document.getElementById('priority-terms').querySelectorAll("term").forEach(elem => {
            termsPriority.push(elem.getAttribute('data-term'));
            termsPriorityWeights.push(elem.getAttribute('data-val'));
        })

        document.getElementById('required-domains').querySelectorAll("domain").forEach(elem => {
            domainsRequired.push(elem.getAttribute('data-domain'));
        })
        document.getElementById('excluded-domains').querySelectorAll("domain").forEach(elem => {
            domainsExcluded.push(elem.getAttribute('data-domain'));
        })
        document.getElementById('priority-domains').querySelectorAll("domain").forEach(elem => {
            domainsPriority.push(elem.getAttribute('data-domain'));
            domainsPriorityWeights.push(elem.getAttribute('data-val'));
        })


        const temporalValue = document
            .querySelector('input:checked[type="radio"][name="temporal"]')
            ?.getAttribute('value')
            ??'none';

        const yearLimitType = document.getElementById('limit-year-type').value;
        const yearLimitValue = document.getElementById('limit-year-val').value ?? 0;
        
        const sizeLimitType = document.getElementById('limit-size-type').value;
        const sizeLimitValue = document.getElementById('limit-size-val').value ?? 0;

        const rankLimitType = document.getElementById('limit-rank-type').value;
        const rankLimitValue = document.getElementById('limit-rank-val').value ?? 0;

        const qualityLimitType = document.getElementById('limit-quality-type').value;
        const qualityLimitValue = document.getElementById('limit-quality-val').value ?? 0;

        return JSON.stringify({
            "termsRequired": termsRequired,
            "termsExcluded": termsExcluded,
            "termsPriority": termsPriority,
            "termsPriorityWeights": termsPriorityWeights,
            "domainsRequired": domainsRequired,
            "domainsExcluded": domainsExcluded,
            "domainsPriority": domainsPriority,
            "domainsPriorityWeights": domainsPriorityWeights,
            "temporalBias": temporalValue,
            "yearLimitType": yearLimitType,
            "yearLimitValue": yearLimitValue,
            "sizeLimitType": sizeLimitType,
            "sizeLimitValue": sizeLimitValue,
            "rankLimitType": rankLimitType,
            "rankLimitValue": rankLimitValue,
            "qualityLimitType": qualityLimitType,
            "qualityLimitValue": qualityLimitValue
        });
    }
</script>
</html>