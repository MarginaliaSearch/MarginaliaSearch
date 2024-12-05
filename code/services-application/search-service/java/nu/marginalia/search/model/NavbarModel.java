package nu.marginalia.search.model;

import java.util.List;

public record NavbarModel(NavbarGroup first, NavbarGroup second) {

    public record NavbarEntry(String name, String url, boolean active) { }
    public record NavbarGroup(List<NavbarEntry> entries) { }

    public static NavbarModel LIMBO =
            new NavbarModel(
                new NavbarGroup(
                    List.of(
                        new NavbarEntry("Search", "/", false),
                        new NavbarEntry("Domains", "/site", false),
                        new NavbarEntry("Explore", "/explore", false)
                    )
                )
                ,
                new NavbarGroup(
                    List.of(
                        new NavbarEntry("About", "/", false)
                    )
                )
            );

    public static NavbarModel SEARCH =
            new NavbarModel(
                new NavbarGroup(
                        List.of(
                                new NavbarEntry("Search", "/", true),
                                new NavbarEntry("Domains", "/site", false),
                                new NavbarEntry("Explore", "/explore", false)
                        )
                )
                ,
                new NavbarGroup(
                        List.of(
                                new NavbarEntry("About", "/", false)
                        )
                )
            );

    public static NavbarModel SITEINFO =
            new NavbarModel(
                new NavbarGroup(
                        List.of(
                                new NavbarEntry("Search", "/", false),
                                new NavbarEntry("Domains", "/site", true),
                                new NavbarEntry("Explore", "/explore", false)
                        )
                )
                ,
                new NavbarGroup(
                        List.of(
                                new NavbarEntry("About", "/", false)
                        )
                )
            );

    public static NavbarModel EXPLORE =
            new NavbarModel(
                    new NavbarGroup(
                            List.of(
                                    new NavbarEntry("Search", "/", false),
                                    new NavbarEntry("Domains", "/site", false),
                                    new NavbarEntry("Explore", "/explore", true)
                            )
                    )
                    ,
                    new NavbarGroup(
                            List.of(
                                    new NavbarEntry("About", "/", false)
                            )
                    )
            );
}
