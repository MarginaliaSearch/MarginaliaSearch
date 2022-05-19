package nu.marginalia.wmsa.configuration;

import io.prometheus.client.hotspot.DefaultExports;
import io.reactivex.rxjava3.exceptions.UndeliverableException;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.client.exception.NetworkException;
import org.mariadb.jdbc.Driver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Arrays;

public abstract class MainClass {
    private Logger logger = LoggerFactory.getLogger(getClass());

    public MainClass() {

        RxJavaPlugins.setErrorHandler(ex -> {
            if (ex instanceof UndeliverableException) {
                ex = ex.getCause();
            }

            if (ex instanceof SocketTimeoutException) {
                logger.warn("SocketTimeoutException");
            }
            else if (ex instanceof UnknownHostException) {
                logger.warn("UnknownHostException");
            }
            else if (ex instanceof NetworkException) {
                logger.warn("NetworkException", ex);
            }
            else {
                logger.error("Uncaught exception", ex);
            }
        });

    }

    @SneakyThrows
    protected static void init(ServiceDescriptor service, String... args) {
        System.setProperty("log4j2.isThreadContextMapInheritable", "true");
        System.setProperty("isThreadContextMapInheritable", "true");
        System.setProperty("service-name", service.name);

        org.mariadb.jdbc.Driver driver = new Driver();

        if (Arrays.asList(args).contains("go-no-go")) {
            System.setProperty("go-no-go", "true");
        }
        DefaultExports.initialize();
    }

}
