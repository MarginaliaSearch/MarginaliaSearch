package nu.marginalia.keyword.model;

import nu.marginalia.language.sentence.tag.HtmlTag;

public record DocumentWordSpan(HtmlTag tag, int start, int end) {
}
