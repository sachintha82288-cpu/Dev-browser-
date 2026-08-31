package com.devbrowser.app.network;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Minimal SOCKS5 client (no-auth), used by LocalProxyServer to reach Tor
 * (Orbot listens on 127.0.0.1:9050 by default). Platform API only.
 */
public class Socks5 {

    /** Open a TCP connection to host:port through the SOCKS5 server. */
    public static Socket connectThrough(String socksHost, int socksPort,
                                        String targetHost, int targetPort) throws IOException {
        Socket s = new Socket();
        s.connect(new InetSocketAddress(socksHost, socksPort), 15000);
        s.setSoTimeout(15000);
        try {
            OutputStream out = s.getOutputStream();
            InputStream in = s.getInputStream();

            // greeting: SOCKS5, one method (no auth)
            out.write(new byte[]{5, 1, 0});
            out.flush();
            byte[] greet = readN(in, 2);
            if (greet[0] != 5 || greet[1] != 0) {
                throw new IOException("SOCKS5 handshake rejected (methods " + greet[1] + ")");
            }

            // CONNECT with domain name (ATYP=3)
            byte[] host = targetHost.getBytes("UTF-8");
            ByteArrayOutputStream req = new ByteArrayOutputStream();
            req.write(5);          // version
            req.write(1);          // CONNECT
            req.write(0);          // reserved
            req.write(3);          // domain
            req.write(host.length);
            req.write(host);
            req.write((targetPort >> 8) & 0xFF);
            req.write(targetPort & 0xFF);
            out.write(req.toByteArray());
            out.flush();

            byte[] head = readN(in, 4);
            if (head[0] != 5 || head[1] != 0) {
                throw new IOException("SOCKS5 connect failed (rep " + head[1] + ")");
            }
            // skip bind address: ATYP 1=4 bytes, 3=1+domain, 4=16 bytes, then 2-byte port
            int atyp = head[3] & 0xFF;
            int addrLen;
            if (atyp == 1) addrLen = 4;
            else if (atyp == 4) addrLen = 16;
            else if (atyp == 3) addrLen = readN(in, 1)[0] & 0xFF;
            else throw new IOException("SOCKS5 unknown atyp " + atyp);
            readN(in, addrLen + 2);

            s.setSoTimeout(0);
            return s;
        } catch (IOException e) {
            try { s.close(); } catch (IOException ignored) { }
            throw e;
        }
    }

    private static byte[] readN(InputStream in, int n) throws IOException {
        byte[] buf = new byte[n];
        int off = 0;
        while (off < n) {
            int r = in.read(buf, off, n - off);
            if (r < 0) throw new IOException("SOCKS5 connection closed");
            off += r;
        }
        return buf;
    }
}
