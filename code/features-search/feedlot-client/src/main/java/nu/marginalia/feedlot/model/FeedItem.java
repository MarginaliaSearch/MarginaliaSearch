package nu.marginalia.feedlot.model;

public record FeedItem(String title, String date, String description, String url) {

    public String pubDay() { // Extract the date from an ISO style date string
        if (date.length() > 10) {
            return date.substring(0, 10);
        }
        return date;
    }

}
