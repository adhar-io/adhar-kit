package com.adhar.kit.batch.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A TCP forwarder a test can cut: every open connection is closed and new ones are closed as soon
 * as they are accepted, until {@link #restore()}. In front of the test's PostgreSQL it is the
 * database becoming unreachable for one application (design 0025 FQ3) while the test itself still
 * reads the database directly.
 */
public final class TcpProxy implements AutoCloseable {

    private final ServerSocket server;
    private final String host;
    private final int port;
    private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
    private volatile boolean cut;
    private volatile boolean closed;

    /**
     * @param host the upstream host
     * @param port the upstream port
     */
    public TcpProxy(String host, int port) {
        this.host = host;
        this.port = port;
        try {
            this.server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        Thread acceptor = new Thread(this::accept, "tcp-proxy-" + server.getLocalPort());
        acceptor.setDaemon(true);
        acceptor.start();
    }

    /**
     * @param jdbcUrl a {@code jdbc:postgresql://host:port/database} URL
     * @return a proxy in front of that server
     */
    public static TcpProxy inFrontOf(String jdbcUrl) {
        String authority = jdbcUrl.substring(jdbcUrl.indexOf("//") + 2, jdbcUrl.lastIndexOf('/'));
        return new TcpProxy(authority.substring(0, authority.lastIndexOf(':')),
                Integer.parseInt(authority.substring(authority.lastIndexOf(':') + 1)));
    }

    /**
     * @param jdbcUrl the URL the proxy was made {@linkplain #inFrontOf(String) in front of}
     * @return the same database, through this proxy
     */
    public String through(String jdbcUrl) {
        return "jdbc:postgresql://127.0.0.1:" + server.getLocalPort() + jdbcUrl.substring(jdbcUrl.lastIndexOf('/'));
    }

    /**
     * Makes the upstream unreachable: closes every connection and refuses new ones.
     */
    public void cut() {
        cut = true;
        sockets.forEach(TcpProxy::quietly);
    }

    /**
     * Makes the upstream reachable again.
     */
    public void restore() {
        cut = false;
    }

    private void accept() {
        while (!closed) {
            try {
                Socket client = server.accept();
                if (cut) {
                    quietly(client);
                    continue;
                }
                Socket upstream = new Socket(host, port);
                sockets.add(client);
                sockets.add(upstream);
                pump(client, upstream);
                pump(upstream, client);
            } catch (IOException ex) {
                // the upstream refused, or the proxy is closing: the client sees a closed connection
            }
        }
    }

    private void pump(Socket from, Socket to) {
        Thread thread = new Thread(() -> {
            try (InputStream in = from.getInputStream(); OutputStream out = to.getOutputStream()) {
                in.transferTo(out);
            } catch (IOException ex) {
                // one side closed
            } finally {
                quietly(from);
                quietly(to);
                sockets.remove(from);
                sockets.remove(to);
            }
        }, "tcp-proxy-pump");
        thread.setDaemon(true);
        thread.start();
    }

    private static void quietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ex) {
            // already closed
        }
    }

    @Override
    public void close() {
        closed = true;
        cut();
        try {
            server.close();
        } catch (IOException ex) {
            // already closed
        }
    }
}
