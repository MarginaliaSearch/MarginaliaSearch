package nu.marginalia.integration;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public sealed interface MarginaliaQueryParam {
    public String asQueryElement();

    public record Query(String query) implements MarginaliaQueryParam {
        @Override
        public String asQueryElement() {
            return "query=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        }
    }
    public record Page(int page) implements MarginaliaQueryParam  {
        @Override
        public String asQueryElement() {
            return "page="+page;
        }
    }
    public record Count(int count) implements MarginaliaQueryParam  {
        @Override
        public String asQueryElement() {
            return "count="+count;
        }
    }
    public record NsfwFlag(int flag) implements MarginaliaQueryParam  {
        @Override
        public String asQueryElement() {
            return "nsfw="+flag;
        }
    }
}
