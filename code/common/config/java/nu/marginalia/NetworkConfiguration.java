package nu.marginalia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.Objects;

/** Resolves which local address a server should listen on.
 */
public class NetworkConfiguration {
    private static final Logger logger = LoggerFactory.getLogger(NetworkConfiguration.class);

    /** Get the bind address for the service.  This is the address that the service will listen on.
     */
    public static String getBindAddress() {
        String configuredValue = System.getProperty("service.bind-address");
        if (configuredValue != null) {
            logger.info("Using configured bind address {}", configuredValue);
            return configuredValue;
        }

        if (Boolean.getBoolean("system.multiFace")) {
            try {
                return Objects.requireNonNullElse(getLocalNetworkIP(), "0.0.0.0");
            } catch (Exception ex) {
                logger.warn("Failed to get local network IP, falling back to bind to 0.0.0.0", ex);
                return "0.0.0.0";
            }
        }
        else {
            return "0.0.0.0";
        }
    }

    public static String getLocalNetworkIP() throws IOException {
        // Overrride for when the namespace holds several site-local addresses
        String preferredInterface = System.getProperty("system.multiFaceInterface");

        Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();

        while (nets.hasMoreElements()) {
            NetworkInterface netif = nets.nextElement();
            logger.info("Considering network interface {}:  Up? {},  Loopback? {}", netif.getDisplayName(), netif.isUp(), netif.isLoopback());
            if (!netif.isUp() || netif.isLoopback()) {
                continue;
            }

            if (preferredInterface != null && !Objects.equals(preferredInterface, netif.getName())) {
                continue;
            }

            Enumeration<InetAddress> inetAddresses = netif.getInetAddresses();
            while (inetAddresses.hasMoreElements()) {
                InetAddress addr = inetAddresses.nextElement();
                logger.info("Considering address {}: SiteLocal? {}, Loopback? {}", addr.getHostAddress(), addr.isSiteLocalAddress(), addr.isLoopbackAddress());
                if (addr.isSiteLocalAddress() && !addr.isLoopbackAddress()) {
                    return addr.getHostAddress();
                }
            }
        }
        return null;
    }
}
