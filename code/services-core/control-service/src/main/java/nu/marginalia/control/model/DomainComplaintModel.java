package nu.marginalia.control.model;

public record DomainComplaintModel(String domain,
                                   DomainComplaintCategory category,
                                   String description,
                                   String sample,
                                   String decision,
                                   String fileDate,
                                   String reviewDate,
                                   boolean reviewed)
{

    public boolean isAppeal() {
        return category == DomainComplaintCategory.BLACKLIST;
    }

}
