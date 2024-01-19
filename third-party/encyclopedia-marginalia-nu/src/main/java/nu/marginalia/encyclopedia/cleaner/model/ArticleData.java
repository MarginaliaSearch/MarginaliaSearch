package nu.marginalia.encyclopedia.cleaner.model;

public record ArticleData(
        String url,
        String title,
        String summary,
        byte[] parts,
        byte[] links,
        byte[] disambigs) {
}
