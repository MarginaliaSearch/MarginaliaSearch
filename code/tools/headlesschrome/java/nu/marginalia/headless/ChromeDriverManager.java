package nu.marginalia.headless;

import nu.marginalia.WmsaHome;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

public class ChromeDriverManager {
    private static final String CHROME_PATH = "/usr/bin/chromium";

    private final ChromeDriver driver;

    public ChromeDriverManager() {
        ChromeOptions options = chromeOptions();
        driver = new ChromeDriver(options);
    }

    // FIXME: We should pool these
    public ChromeDriver getDriver() {
        return driver;
    }

    private static ChromeOptions chromeOptions() {
        ChromeOptions options = new ChromeOptions();
        options.setBinary(CHROME_PATH);
        options.addArguments(
                "--headless=new",
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--disable-gpu",
                "--window-size=1024,768",
                "--user-agent=" + WmsaHome.getUserAgent().uaString()
        );
        return options;
    }
}
