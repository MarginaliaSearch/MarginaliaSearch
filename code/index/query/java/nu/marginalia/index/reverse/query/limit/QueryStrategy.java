package nu.marginalia.index.reverse.query.limit;

public enum QueryStrategy {
    SENTENCE,
    TOPIC,

    REQUIRE_FIELD_SITE,
    REQUIRE_FIELD_TITLE,
    REQUIRE_FIELD_SUBJECT,
    REQUIRE_FIELD_URL,
    REQUIRE_FIELD_DOMAIN,
    REQUIRE_FIELD_LINK,

    AUTO
}
