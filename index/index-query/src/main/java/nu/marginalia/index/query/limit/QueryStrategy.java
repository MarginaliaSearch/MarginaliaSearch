package nu.marginalia.index.query.limit;

public enum QueryStrategy {
    SENTENCE,
    TOPIC,

    REQUIRE_FIELD_SITE,
    REQUIRE_FIELD_TITLE,
    REQUIRE_FIELD_SUBJECT,

    AUTO
}
