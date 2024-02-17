package nu.marginalia.executor.upload;

public record UploadDirItem (
        String name,
        String lastModifiedTime,
        boolean isDirectory,
        long size
) {

    public boolean isZim() {
        if (name.endsWith(".zim"))
            return true;
        if (name.contains(".zim.") && name.endsWith(".db"))
            return true;
        return false;
    }

    public boolean isStackexchange7z() {
        if (name.endsWith(".7z"))
            return true;
        if (name.contains(".7z.") && name.endsWith(".db"))
            return true;
        return isDirectory;
    }

    public boolean isWarc() {
        if (name.endsWith(".warc"))
            return true;
        if (name.contains(".warc.gz"))
            return true;
        return isDirectory;
    }

}
