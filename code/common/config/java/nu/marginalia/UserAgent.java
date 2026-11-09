package nu.marginalia;

/**
 * A record representing a User Agent.
 *
 * @param uaString     - the header value of the User Agent
 * @param uaIdentifier - what we look for in robots.txt
 * @param uaFrom
 */
public record UserAgent(String uaString, String uaIdentifier, String uaFrom) {}
