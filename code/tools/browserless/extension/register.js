chrome.webNavigation.onCommitted.addListener(function(details) {
    if (details.transitionType === 'reload' || details.transitionType === 'auto_subframe') {
        chrome.scripting.registerContentScripts([{
            matches : [ "<all_urls>" ],
            js : [ "inject.js" ],
        }]);
    }
});
