package nu.marginalia.screenshot.tool;


import nu.marginalia.service.module.DatabaseModule;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.mariadb.jdbc.Driver;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.zip.GZIPInputStream;

public class ScreenshotLoaderMain {
    public static void main(String... args) throws IOException {

        org.mariadb.jdbc.Driver driver = new Driver();
        var ds = new DatabaseModule().provideConnection();

        try (var tis = new TarArchiveInputStream(new GZIPInputStream(new FileInputStream(args[0])));
             var conn = ds.getConnection();
             var ps = conn.prepareStatement("REPLACE INTO DATA_DOMAIN_SCREENSHOT(DOMAIN_NAME, CONTENT_TYPE, DATA) VALUES (?,?,?)")
        ) {
            for (TarArchiveEntry entry = tis.getNextTarEntry(); entry != null; entry = tis.getNextTarEntry()) {
                if (entry.isFile()) {
                    String fileName = entry.getName();
                    String domainName = fileName.substring(fileName.indexOf('/')+1, fileName.lastIndexOf('.'));

                    ps.setString(1, domainName);
                    ps.setString(2, "image/webp");
                    ps.setBlob(3, tis);
                    ps.executeUpdate();

                    System.out.println(domainName);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
