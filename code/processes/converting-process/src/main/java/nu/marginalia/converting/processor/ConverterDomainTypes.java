package nu.marginalia.converting.processor;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.db.DomainTypes;
import nu.marginalia.model.EdgeDomain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/** Converter-side wrapper for of common:db's DomainTypes,
 * which is a list of domains of a known type (e.g. blog)
 */
@Singleton
public class ConverterDomainTypes {
    private final Logger logger = LoggerFactory.getLogger(ConverterDomainTypes.class);
    private final Map<EdgeDomain, DomainType> domainTypes = new HashMap<>();

    private enum DomainType {
        BLOG
    }

    @Inject
    public ConverterDomainTypes(DomainTypes types) throws SQLException {
        var allBlogs = types.getAllDomainsByType(DomainTypes.Type.BLOG);

        if (allBlogs.isEmpty()) {
            logger.info("No domains of type BLOG found in database, downloading list");
            try {
                types.reloadDomainsList(DomainTypes.Type.BLOG);
                allBlogs = types.getAllDomainsByType(DomainTypes.Type.BLOG);
            }
            catch (IOException ex) {
                logger.error("Failed to download domains list", ex);
            }
        }

        for (var item : allBlogs) {
            domainTypes.put(new EdgeDomain(item), DomainType.BLOG);
        }

        logger.info("Loaded {} domain types", domainTypes.size());

    }

    public boolean isBlog(EdgeDomain domain) {
        return domainTypes.get(domain) == DomainType.BLOG;
    }
}
