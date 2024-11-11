package nu.marginalia.search.model;

import nu.marginalia.WebsiteUrl;
import nu.marginalia.search.command.*;

import java.util.List;

/** Models the search filters displayed next to the search results */
public class SearchFilters {
    private final WebsiteUrl url;

    public final String currentFilter;

    // These are necessary for the renderer to access the data
    public final RemoveJsOption removeJsOption;
    public final ReduceAdtechOption reduceAdtechOption;
    public final ShowRecentOption showRecentOption;
    public final SearchTitleOption searchTitleOption;

    public final List<List<Filter>> filterGroups;

    // Getters are for the renderer to access the data


    public String getCurrentFilter() {
        return currentFilter;
    }

    public RemoveJsOption getRemoveJsOption() {
        return removeJsOption;
    }

    public ReduceAdtechOption getReduceAdtechOption() {
        return reduceAdtechOption;
    }

    public ShowRecentOption getShowRecentOption() {
        return showRecentOption;
    }

    public SearchTitleOption getSearchTitleOption() {
        return searchTitleOption;
    }

    public List<List<Filter>> getFilterGroups() {
        return filterGroups;
    }

    public SearchFilters(WebsiteUrl url, SearchParameters parameters) {
        this.url = url;

        removeJsOption = new RemoveJsOption(parameters);
        reduceAdtechOption = new ReduceAdtechOption(parameters);
        showRecentOption = new ShowRecentOption(parameters);
        searchTitleOption = new SearchTitleOption(parameters);


        currentFilter = parameters.profile().filterId;

        filterGroups = List.of(
                            List.of(
                                    new Filter("No Filter", SearchProfile.NO_FILTER, parameters),
//                                    new Filter("Popular", SearchProfile.POPULAR, parameters),
                                    new Filter("Small Web", SearchProfile.SMALLWEB, parameters),
                                    new Filter("Blogosphere", SearchProfile.BLOGOSPHERE, parameters),
                                    new Filter("Academia", SearchProfile.ACADEMIA, parameters)
                            ),
                            List.of(
                                    new Filter("Vintage", SearchProfile.VINTAGE, parameters),
                                    new Filter("Plain Text", SearchProfile.PLAIN_TEXT, parameters),
                                    new Filter("~tilde", SearchProfile.TILDE, parameters)
                            ),
                            List.of(
                                new Filter("Wiki", SearchProfile.WIKI, parameters),
                                new Filter("Forum", SearchProfile.FORUM, parameters),
                                new Filter("Docs", SearchProfile.DOCS, parameters),
                                new Filter("Recipes", SearchProfile.FOOD, parameters)
                            )
                        );


    }

    public class RemoveJsOption {
        private final SearchJsParameter value;

        public final String url;
        public String getUrl() {
            return url;
        }

        public boolean isSet() {
            return value.equals(SearchJsParameter.DENY_JS);
        }

        public String name() {
            return "Remove Javascript";
        }

        public RemoveJsOption(SearchParameters parameters) {
            this.value = parameters.js();

            var toggledValue = switch (parameters.js()) {
                case DENY_JS -> SearchJsParameter.DEFAULT;
                default -> SearchJsParameter.DENY_JS;
            };

            this.url = parameters.withJs(toggledValue).renderUrl(SearchFilters.this.url);
        }
    }

    public class ReduceAdtechOption {
        private final SearchAdtechParameter value;

        public final String url;
        public String getUrl() {
            return url;
        }

        public boolean isSet() {
            return value.equals(SearchAdtechParameter.REDUCE);
        }

        public String name() {
            return "Reduce Adtech";
        }

        public ReduceAdtechOption(SearchParameters parameters) {
            this.value = parameters.adtech();

            var toggledValue = switch (parameters.adtech()) {
                case REDUCE -> SearchAdtechParameter.DEFAULT;
                default -> SearchAdtechParameter.REDUCE;
            };

            this.url = parameters.withAdtech(toggledValue).renderUrl(SearchFilters.this.url);
        }
    }

    public class ShowRecentOption {
        private final SearchRecentParameter value;

        public final String url;
        public String getUrl() {
            return url;
        }

        public boolean isSet() {
            return value.equals(SearchRecentParameter.RECENT);
        }

        public String name() {
            return "Recent Results";
        }

        public ShowRecentOption(SearchParameters parameters) {
            this.value = parameters.recent();

            var toggledValue = switch (parameters.recent()) {
                case RECENT -> SearchRecentParameter.DEFAULT;
                default -> SearchRecentParameter.RECENT;
            };

            this.url = parameters.withRecent(toggledValue).renderUrl(SearchFilters.this.url);
        }
    }

    public class SearchTitleOption {
        private final SearchTitleParameter value;

        public final String url;
        public String getUrl() {
            return url;
        }

        public boolean isSet() {
            return value.equals(SearchTitleParameter.TITLE);
        }

        public String name() {
            return "Search In Title";
        }

        public SearchTitleOption(SearchParameters parameters) {
            this.value = parameters.searchTitle();

            var toggledValue = switch (parameters.searchTitle()) {
                case TITLE -> SearchTitleParameter.DEFAULT;
                default -> SearchTitleParameter.TITLE;
            };

            this.url = parameters.withTitle(toggledValue).renderUrl(SearchFilters.this.url);
        }
    }

    public class Filter {
        public final SearchProfile profile;

        public final String displayName;
        public final boolean current;
        public final String url;

        public Filter(String displayName, SearchProfile profile, SearchParameters parameters) {
            this.displayName = displayName;
            this.profile = profile;
            this.current = profile.equals(parameters.profile());

            this.url = parameters.withProfile(profile).renderUrl(SearchFilters.this.url);
        }

        public String getDisplayName() {
            return displayName;
        }

        public boolean isCurrent() {
            return current;
        }

        public String getUrl() {
            return url;
        }
    }
}
