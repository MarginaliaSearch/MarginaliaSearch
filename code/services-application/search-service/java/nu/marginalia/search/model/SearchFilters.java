package nu.marginalia.search.model;

import nu.marginalia.WebsiteUrl;
import nu.marginalia.search.command.*;

import java.util.List;

/** Models the search filters displayed next to the search results */
public class SearchFilters {
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
    public List<SearchOption> searchOptions() {
        return List.of(
                searchTitleOption,
                showRecentOption,
                removeJsOption,
                reduceAdtechOption
                );
    }

    public SearchFilters(WebsiteUrl url) {
        this(new SearchParameters(url, "",
                SearchProfile.NO_FILTER,
                SearchJsParameter.DEFAULT,
                SearchRecentParameter.DEFAULT,
                SearchTitleParameter.DEFAULT,
                SearchAdtechParameter.DEFAULT,
                false,
                1));
    }

    public SearchFilters(SearchParameters parameters) {

        removeJsOption = new RemoveJsOption(parameters);
        reduceAdtechOption = new ReduceAdtechOption(parameters);
        showRecentOption = new ShowRecentOption(parameters);
        searchTitleOption = new SearchTitleOption(parameters);


        currentFilter = parameters.profile().filterId;

        filterGroups = List.of(
                            List.of(
                                    new Filter("All", "fa-globe", SearchProfile.NO_FILTER, parameters),
                                    new Filter("Blogs", "fa-blog", SearchProfile.BLOGOSPHERE, parameters),
                                    new Filter("Academia", "fa-university", SearchProfile.ACADEMIA, parameters)
                            ),
                            List.of(
                                    new Filter("Vintage", "fa-clock-rotate-left", SearchProfile.VINTAGE, parameters),
                                    new Filter("Small Web", "fa-minus", SearchProfile.SMALLWEB, parameters),
                                    new Filter("Plain Text", "fa-file", SearchProfile.PLAIN_TEXT, parameters),
                                    new Filter("Tilde", "fa-house", SearchProfile.TILDE, parameters)
                            ),
                            List.of(
                                new Filter("Wikis", "fa-pencil", SearchProfile.WIKI, parameters),
                                new Filter("Forums", "fa-comments", SearchProfile.FORUM, parameters),
                                new Filter("Recipes", "fa-utensils", SearchProfile.FOOD, parameters)
                            )
                        );


    }

    public class RemoveJsOption implements SearchOption {
        private final SearchJsParameter value;
        private final String icon = "fa-wrench";
        public final String url;

        public String value() {
            return this.value.name();
        }

        public String getUrl() {
            return url;
        }
        public String id() {
            return getClass().getSimpleName();
        }
        public boolean isSet() {
            return value.equals(SearchJsParameter.DENY_JS);
        }

        public String icon() {
            return icon;
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

            this.url = parameters.withJs(toggledValue).renderUrl();
        }
    }

    public class ReduceAdtechOption implements SearchOption {
        private final SearchAdtechParameter value;
        private final String icon = "fa-dumpster-fire";
        public final String url;

        public String value() {
            return this.value.name();
        }

        public String id() {
            return getClass().getSimpleName();
        }
        public String icon() {
            return icon;
        }

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

            this.url = parameters.withAdtech(toggledValue).renderUrl();
        }
    }

    public class ShowRecentOption implements SearchOption {
        private final SearchRecentParameter value;
        private final String icon = "fa-baby";

        public String value() {
            return this.value.name();
        }

        public final String url;
        public String getUrl() {
            return url;
        }
        public String id() {
            return getClass().getSimpleName();
        }
        public String icon() {
            return icon;
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

            this.url = parameters.withRecent(toggledValue).renderUrl();
        }
    }

    public class SearchTitleOption implements SearchOption {
        private final SearchTitleParameter value;
        public String icon = "fa-angle-up";

        public final String url;

        public String value() {
            return this.value.name();
        }

        public String id() {
            return getClass().getSimpleName();
        }
        public String icon() {
            return icon;
        }
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

            this.url = parameters.withTitle(toggledValue).renderUrl();
        }
    }

    public interface SearchOption {
        String name();
        boolean isSet();
        String getUrl();
        String icon();
        String id();
        String value();
    }

    public class Filter {
        public final String icon;
        public final SearchProfile profile;

        public final String displayName;
        public final boolean current;
        public final String url;

        public Filter(String displayName, String icon, SearchProfile profile, SearchParameters parameters) {
            this.displayName = displayName;
            this.icon = icon;
            this.profile = profile;
            this.current = profile.equals(parameters.profile());

            this.url = parameters.withProfile(profile).renderUrl();
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
