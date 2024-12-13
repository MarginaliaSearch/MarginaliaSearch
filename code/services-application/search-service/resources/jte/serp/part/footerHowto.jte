<footer class="max-w-[1400px] mx-auto text-slate-900 dark:text-slate-100 bg-white dark:bg-gray-800 p-5 text-sm border-gray-300 border dark:border-gray-600 rounded flex my-5 md:space-x-8 space-y-4 md:flex-row flex-col leading-6">
    <div class="flex space-y-4 flex-col flex-1">
        <p class="text-base"><i class="fas fa-book mr-2 text-margeblue dark:text-slate-200"></i>Syntax</p>
        <p>This is a keyword-based search engine. When entering multiple search terms, the search engine will attempt to match them against documents where the terms occur in close proximity.</p>
        <p>Search terms can be excluded with a hyphen.</p>
        <p>While the search engine at present does not allow full text search, quotes can be used to specifically search for names or terms in the title. Using quotes will also cause the search engine to be as literal as possible in interpreting the query.</p>
        <p>Parentheses can be used to add terms to the query without giving weight to the terms when ranking the search results.</p>

        <p>Samples</p>
        <dl>
            <dt class="font-mono text-red-800 dark:text-red-200">soup -chicken</dt>
            <dd class="ml-4">Look for keywords that contain <span class="font-mono text-red-800 dark:text-red-200">soup</span>, but not
                <span class="font-mono text-red-800 dark:text-red-200">chicken</span>.</dd>
            <dt class="font-mono text-red-800 dark:text-red-200">"keyboard"</dt>
            <dd class="ml-4">Look for pages containing the exact word
                <span class="font-mono text-red-800 dark:text-red-200">keyboard</span>, not <span class="font-mono text-red-800 dark:text-red-200">keyboards</span> or the like.</dd>
            <dt class="font-mono text-red-800 dark:text-red-200">"steve mcqueen"</dt>
            <dd class="ml-4">Look for pages containing the exact words <span class="font-mono text-red-800 dark:text-red-200">steve mcqueen</span>
                in that order, with no words in between.</dd>
            <dt class="font-mono text-red-800 dark:text-red-200">apology (plato)</dt>
            <dd class="ml-4">Look for pages containing <span class="font-mono text-red-800 dark:text-red-200">apology</span> and <span class="font-mono text-red-800 dark:text-red-200">plato</span>, but only rank them
                based on their relevance to <span class="font-mono text-red-800 dark:text-red-200">apology</span></dd>
        </dl>
        <p class="text-base"><i class="fas fa-coffee mr-2 text-margeblue dark:text-slate-200"></i>Match Indicator</p>
        <p>The "coffee stain" indicator next to the search results illustrates the locations in a document the keywords appear.
            It operates a bit like a clock, starting at the top and working its way around clockwise.</p>

        <div class="flex gap-4 place-items-middle">
            @template.serp.part.matchogram(mask = 90)
            <div>This is by the beginning</div>
        </div>
        <div class="flex gap-4 place-items-middle">
            @template.serp.part.matchogram(mask = 90L<<26)
            <div>This is in the middle</div>
        </div>
        <div class="flex gap-4 place-items-middle">
            @template.serp.part.matchogram(mask = 5L<<48)
            <div>This is toward the end</div>
        </div>

        <p class="text-base"><i class="fas fa-flag mr-2 text-margeblue dark:text-slate-200"></i>Language Limitations</p>
        <p>The search engine currently does not support any languages other than English. </p>
        <p> Support for other languages is planned,
            but not available right now.  Adding support for additional languages and making it work well is somewhat time-consuming,
            meanwhile having bad support for a language won't make anyone happy.
        </p>
        <p class="text-base"><i class="fas fa-server mr-2 text-margeblue dark:text-slate-200"></i>Webmaster Information</p>
        <p>If you wish to add your website to the index, follow the instructions in this <a class="underline text-liteblue dark:text-blue-200"  href="https://github.com/MarginaliaSearch/submit-site-to-marginalia-search">git repository</a>,
            if you do not want to mess with git, you can also email <a class="underline text-liteblue dark:text-blue-200" href="mailto:kontakt@marginalia.nu">kontakt@marginalia.nu</a> with the domain name.</p>
        <p>The search engine's crawler uses the user-agent string <span class="font-mono text-red-800 dark:text-red-200">search.marginalia.nu</span>, and requests come from the IPs indicated in
            <a class="underline text-liteblue dark:text-blue-200" href="https://search.marginalia.nu/crawler-ips.txt">https://search.marginalia.nu/crawler-ips.txt</a>.</p>
        <p>If you do not want your website to be crawled, the search engine respects robots.txt.  In case of questions, bug reports or concerns, email <a class="underline text-liteblue dark:text-blue-200" href="mailto:kontakt@marginalia.nu">kontakt@marginalia.nu</a>.
        </p>
    </div>
    <div class="flex space-y-4 flex-col flex-1">
        <p class="text-base"><i class="fas fa-code mr-2 text-margeblue dark:text-slate-200"></i>Special Keywords</p>
        <table class="overflow-scroll">
            <thead>
            <tr><th>Keyword</th><th>Meaning</th></tr>
            </thead>
            <tbody>

            <tr><td>site:<em>example.com</em></td><td>Display site information about <em>example.com</em></td></tr>
            <tr><td>site:<em>example.com</em> <em>keyword</em></td><td>Search <em>example.com</em> for <em>keyword</em></td></tr>
            <tr><td>browse:<em>example.com</em></td><td>Show similar websites to <em>example.com</em></td></tr>
            <tr><td>ip:<em>127.0.0.1</em></td><td>Search documents hosted at <em>127.0.0.1</em></td></tr>
            <tr><td>links:<em>example.com</em></td><td>Search documents linking to <em>example.com</em></td></tr>

            <tr><td>tld:<em>edu</em> <em>keyword</em></td><td>Search documents with the top level domain <em>edu</em>.</td></tr>
            <tr><td>?tld:<em>edu</em> <em>keyword</em></td><td>Prefer but do not require results with the top level domain <em>edu</em>.
                    This syntax is also possible for links:..., ip:... and site:...</td></tr>

            <tr><td>q&gt;5</td><td>The amount of javascript and modern features is at least 5 (on a scale 0 to 25)</td></tr>
            <tr><td>q&lt;5</td><td>The amount of javascript and modern features is at most 5 (on a scale 0 to 25)</td></tr>

            <tr><td>year&gt;2005</td><td>(beta) The document was ostensibly published in or after 2005</td></tr>
            <tr><td>year=2005</td><td>(beta) The document was ostensibly published in 2005</td></tr>
            <tr><td>year&lt;2005</td><td>(beta) The document was ostensibly published in or before 2005</td></tr>

            <tr><td>rank&gt;50</td><td>The ranking of the website is at least 50 in a span of 1 - 255</td></tr>
            <tr><td>rank&lt;50</td><td>The ranking of the website is at most 50 in a span of 1 - 255</td></tr>

            <tr><td>count&gt;10</td><td> The search term must appear in at least 10 results form the domain</td></tr>
            <tr><td>count&lt;10</td><td> The search term must appear in at most 10 results from the domain</td></tr>


            <tr><td>format:html5</td><td>Filter documents using the HTML5 standard. This is typically modern websites.</td></tr>
            <tr><td>format:xhtml</td><td>Filter documents using the XHTML standard</td></tr>
            <tr><td>format:html123</td><td>Filter documents using the HTML standards 1, 2, and 3. This is typically very old websites. </td></tr>

            <tr><td>generator:wordpress</td><td>Filter documents with the specified generator, in this case wordpress</td></tr>

            <tr><td>file:zip</td><td>Filter documents containing a link to a zip file (most file-endings work)</td></tr>
            <tr><td>file:audio</td><td>Filter documents containing a link to an audio file</td></tr>
            <tr><td>file:video</td><td>Filter documents containing a link to a video file</td></tr>
            <tr><td>file:archive</td><td>Filter documents containing a link to a compressed archive</td></tr>
            <tr><td>file:document</td><td>Filter documents containing a link to a document</td></tr>

            <tr><td>-special:media</td><td>Filter out documents with audio or video tags</td></tr>
            <tr><td>-special:scripts</td><td>Filter out documents with javascript</td></tr>
            <tr><td>-special:affiliate</td><td>Filter out documents with likely Amazon affiliate links</td></tr>
            <tr><td>-special:tracking</td><td>Filter out documents with analytics or tracking code</td></tr>
            <tr><td>-special:cookies</td><td>Filter out documents with cookies</td></tr>
            </tbody>
        </table>
    </div>

</footer>