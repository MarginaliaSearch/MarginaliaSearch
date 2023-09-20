package nu.marginalia.integration.stackexchange.model;

import javax.annotation.Nullable;
import java.util.List;

public record StackExchangePost(@Nullable
                                String title,
                                List<String> tags,
                                int year,
                                int id,
                                @Nullable Integer parentId,
                                int postTypeId,
                                String body)
{

}
