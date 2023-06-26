package nu.marginalia.converting.processor.plugin.specialization;

import nu.marginalia.model.EdgeUrl;
import org.jsoup.nodes.Document;

import java.util.Set;

/** This interface is used to specify how to process a specific website.
 *  The implementations of this interface are used by the HtmlProcessor to
 *  process the HTML documents.
 */
public interface HtmlProcessorSpecialization {
    Document prune(Document original);
    String getSummary(Document original,
                      Set<String> importantWords);

    default boolean shouldIndex(EdgeUrl url) { return true; }
    default double lengthModifier() { return 1.0; }
}
