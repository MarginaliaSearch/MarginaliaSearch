package nu.marginalia.wmsa.edge.index.model;

public enum QueryStrategy {
    SENTENCE,
    TOPIC,

    REQUIRE_FIELD_SITE,
    REQUIRE_FIELD_TITLE,
    REQUIRE_FIELD_SUBJECT,

    AUTO
}
