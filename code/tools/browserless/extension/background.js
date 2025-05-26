

// Listen to web requests and buffer them until the content script is ready

chrome.webRequest.onBeforeRequest.addListener(
    (details) => {
        const requestData = {
            url: details.url,
            method: details.method,
            timestamp: Date.now()
        };
        console.log(requestData);

        chrome.tabs.sendMessage(details.tabId, {
            type: 'URL_INTERCEPTED',
            ...requestData
        });
    },
    { urls: ["<all_urls>"] }
);

// Listen to web navigation events and re-register content scripts when a page is reloaded or navigated to a new subframe

chrome.webNavigation.onCommitted.addListener(function(details) {
    if (details.transitionType === 'reload' || details.transitionType === 'auto_subframe') {
        chrome.scripting.registerContentScripts([{
            matches : [ "<all_urls>" ],
            js : [ "content.js" ],
        }]);
    }
});
