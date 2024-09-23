package nu.marginalia.crawl.fetcher.socket;

import javax.net.SocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

public class FastTerminatingSocketFactory extends SocketFactory {
    private static final SocketFactory delegate = SocketFactory.getDefault();

    private void configure(Socket sock) throws IOException {
        // Setting SO_LINGER to enabled but low reduces TIME_WAIT
        // which can get pretty... bad when you're crawling
        // and opening thousands of connections
        sock.setSoLinger(true, 3);
    }

    public Socket createSocket() throws IOException {
        var sock = delegate.createSocket();
        configure(sock);
        return sock;
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        var sock = delegate.createSocket(host, port);
        configure(sock);
        return sock;
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
        var sock = delegate.createSocket(host, port, localHost, localPort);
        configure(sock);
        return sock;
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        var sock = delegate.createSocket(host, port);
        configure(sock);
        return sock;
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        var sock = delegate.createSocket(address, port, localAddress, localPort);
        configure(sock);
        return sock;
    }
}
