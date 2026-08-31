package com.devbrowser.app.network;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tiny HTTP proxy that lives inside the app and tunnels upstream traffic.
 *
 * Two modes:
 *  - SOCKS mode  : every outgoing connection goes through a SOCKS5 server
 *                  (Orbot / Tor at 127.0.0.1:9050 by default).
 *  - DIRECT mode : plain passthrough (useful for per-site rules later).
 *
 * It implements the minimal HTTP proxy surface WebView actually uses:
 *  - "CONNECT host:port" for HTTPS tunneling,
 *  - absolute-form GET/POST/etc. for plain HTTP.
 *
 * The app then points its WebView proxy override at 127.0.0.1:<this port>,
 * which lets a single setting route ALL browser traffic through Tor.
 */
public class LocalProxyServer {

    private static final String TAG = "LocalProxy";
    private static final int MAX_HEADER = 64 * 1024;

    public interface Status {
        void onStatus(boolean running, int port, String detail);
    }

    private static LocalProxyServer instance;

    public static synchronized LocalProxyServer get() {
        if (instance == null) instance = new LocalProxyServer();
        return instance;
    }

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private ServerSocket server;
    private volatile int port;
    private volatile String socksHost = "127.0.0.1";
    private volatile int socksPort = 9050;
    private volatile boolean useSocks = true;
    private volatile Status status;

    public boolean isRunning() {
        return running.get();
    }

    public int port() {
        return port;
    }

    public void configure(String socksHost, int socksPort, boolean useSocks) {
        this.socksHost = socksHost;
        this.socksPort = socksPort;
        this.useSocks = useSocks;
    }

    public void setStatusListener(Status s) {
        this.status = s;
    }

    /** Start the listener on an ephemeral loopback port. Safe to call repeatedly. */
    public synchronized boolean start() {
        if (running.get()) return true;
        try {
            server = new ServerSocket();
            server.bind(new InetSocketAddress("127.0.0.1", 0));
            port = server.getLocalPort();
            running.set(true);
            pool.execute(this::acceptLoop);
            Log.i(TAG, "listening on 127.0.0.1:" + port);
            notifyStatus(true, "127.0.0.1:" + port);
            return true;
        } catch (IOException e) {
            Log.w(TAG, "start failed", e);
            notifyStatus(false, "start failed: " + e.getMessage());
            return false;
        }
    }

    public synchronized void stop() {
        running.set(false);
        if (server != null) {
            try { server.close(); } catch (IOException ignored) { }
            server = null;
        }
        notifyStatus(false, "stopped");
        Log.i(TAG, "stopped");
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket client = server.accept();
                pool.execute(() -> handleClient(client));
            } catch (IOException e) {
                if (running.get()) Log.w(TAG, "accept error", e);
            }
        }
    }

    // ------------------------------------------------------------------
    //  Connection handling
    // ------------------------------------------------------------------
    private void handleClient(Socket client) {
        try {
            client.setSoTimeout(20000);
            String line = readLine(client.getInputStream());
            if (line == null || line.trim().isEmpty()) return;

            String[] parts = line.split(" ");
            if (parts.length < 2) return;
            String method = parts[0].toUpperCase();
            String target = parts[1];

            if ("CONNECT".equals(method)) {
                String[] hp = splitHostPort(target);
                connectAndRelay(client, hp[0], Integer.parseInt(hp[1]), true);
            } else if (method.equals("GET") || method.equals("POST") || method.equals("PUT")
                    || method.equals("DELETE") || method.equals("HEAD") || method.equals("OPTIONS")) {
                forwardHttp(client, method, target);
            } else {
                Log.w(TAG, "unsupported method " + method);
            }
        } catch (Exception e) {
            Log.d(TAG, "client error: " + e.getMessage());
        } finally {
            closeQuietly(client);
        }
    }

    private void connectAndRelay(Socket client, String host, int port, boolean connectHeader)
            throws IOException {
        Socket upstream = openUpstream(host, port);
        try {
            if (connectHeader) {
                OutputStream out = client.getOutputStream();
                out.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes("ISO-8859-1"));
                out.flush();
            }
            relay(client, upstream);
        } finally {
            closeQuietly(upstream);
        }
    }

    private void forwardHttp(Socket client, String method, String absoluteTarget) throws IOException {
        // absolute-form: http://host:port/path
        String host = U_host(absoluteTarget);
        if (host == null) return;
        String path = pathOf(absoluteTarget);

        int port = 80;
        String h;
        int colon = host.indexOf(':');
        if (colon > 0) {
            port = Integer.parseInt(host.substring(colon + 1));
            h = host.substring(0, colon);
        } else {
            h = host;
        }

        Socket upstream = openUpstream(h, port);
        try {
            OutputStream out = upstream.getOutputStream();
            StringBuilder sb = new StringBuilder();
            sb.append(method).append(' ').append(path).append(" HTTP/1.1\r\n");
            // re-emit the original headers, skipping proxy-specific and Host
            String line;
            while ((line = readLine(client.getInputStream())) != null && !line.isEmpty()) {
                String lower = line.toLowerCase();
                if (lower.startsWith("proxy-connection")) continue;
                if (lower.startsWith("host:")) {
                    sb.append("Host: ").append(host).append("\r\n");
                    continue;
                }
                sb.append(line).append("\r\n");
            }
            sb.append("\r\n");
            out.write(sb.toString().getBytes("ISO-8859-1"));
            out.flush();
            relay(client, upstream);
        } finally {
            closeQuietly(upstream);
        }
    }

    private Socket openUpstream(String host, int port) throws IOException {
        if (useSocks) {
            return Socks5.connectThrough(socksHost, socksPort, host, port);
        }
        Socket s = new Socket();
        s.connect(new InetSocketAddress(host, port), 20000);
        s.setSoTimeout(20000);
        return s;
    }

    // ------------------------------------------------------------------
    //  Stream copying
    // ------------------------------------------------------------------
    private void relay(final Socket a, final Socket b) {
        Thread t1 = pump(a, b);
        Thread t2 = pump(b, a);
        try {
            t1.join();
        } catch (InterruptedException ignored) {
        }
        try { b.setSoTimeout(1000); } catch (IOException ignored) { }
        try { t2.join(2000); } catch (InterruptedException ignored) { }
        closeQuietly(a);
        closeQuietly(b);
    }

    private Thread pump(final Socket from, final Socket to) {
        Thread t = new Thread(() -> {
            byte[] buf = new byte[16 * 1024];
            try {
                InputStream in = from.getInputStream();
                OutputStream out = to.getOutputStream();
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    out.flush();
                }
            } catch (IOException ignored) {
            } finally {
                try { to.shutdownOutput(); } catch (IOException ignored2) { }
            }
        }, "proxy-pump");
        t.setDaemon(true);
        t.start();
        return t;
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------
    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream(256);
        int c;
        boolean cr = false;
        while ((c = in.read()) != -1 && bo.size() < MAX_HEADER) {
            if (cr && c == '\n') break;
            if (c == '\r') { cr = true; continue; }
            cr = false;
            bo.write(c);
        }
        if (bo.size() == 0) return null;
        return new String(bo.toByteArray(), "ISO-8859-1");
    }

    private static String[] splitHostPort(String s) {
        if (s.startsWith("[")) { // IPv6 literal
            int end = s.indexOf(']');
            String host = s.substring(1, end);
            String rest = s.substring(end + 1);
            int port = rest.startsWith(":") ? Integer.parseInt(rest.substring(1)) : 443;
            return new String[]{host, String.valueOf(port)};
        }
        int colon = s.lastIndexOf(':');
        if (colon > 0) {
            return new String[]{s.substring(0, colon), s.substring(colon + 1)};
        }
        return new String[]{s, "443"};
    }

    private static String U_host(String url) {
        try {
            java.net.URI uri = new java.net.URI(url);
            String h = uri.getHost();
            return (h == null || h.isEmpty()) ? null : h + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
        } catch (Exception e) {
            int idx = url.indexOf("://");
            if (idx < 0) return null;
            String rest = url.substring(idx + 3);
            int slash = rest.indexOf('/');
            return slash > 0 ? rest.substring(0, slash) : rest;
        }
    }

    private static String pathOf(String url) {
        try {
            java.net.URI uri = new java.net.URI(url);
            String p = uri.getRawPath();
            String q = uri.getRawQuery();
            String fragment = uri.getRawFragment();
            StringBuilder sb = new StringBuilder();
            sb.append(p == null || p.isEmpty() ? "/" : p);
            if (q != null) sb.append('?').append(q);
            if (fragment != null) sb.append('#').append(fragment);
            return sb.toString();
        } catch (Exception e) {
            int idx = url.indexOf("://");
            if (idx < 0) return url;
            String rest = url.substring(idx + 3);
            int slash = rest.indexOf('/');
            return slash > 0 ? rest.substring(slash) : "/";
        }
    }

    private static void closeQuietly(Closeable c) {
        try { c.close(); } catch (IOException ignored) { }
    }

    private void notifyStatus(boolean running, String detail) {
        if (status != null) {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(
                    () -> status.onStatus(running, port, detail));
        }
    }
}
