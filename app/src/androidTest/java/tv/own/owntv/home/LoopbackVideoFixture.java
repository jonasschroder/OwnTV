package tv.own.owntv.home;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Disposable synthetic bytes, loopback only. Live ExoPlayer uses an HTTP data source. */
public final class LoopbackVideoFixture implements AutoCloseable {
    private final ServerSocket server;
    private final Thread worker;
    private final byte[] bytes;
    private volatile Socket active;

    public LoopbackVideoFixture(byte[] bytes) throws IOException {
        if (bytes.length == 0 || bytes.length > 1_000_000) throw new IllegalArgumentException();
        this.bytes = bytes;
        server = new ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"));
        worker = new Thread(() -> {
            while (!server.isClosed()) {
                try (Socket socket = server.accept()) {
                    active = socket;
                    socket.setSoTimeout(5000);
                    serve(socket);
                } catch (IOException ignored) { /* Closing a superseded HTTP load is normal. */ }
                finally { active = null; }
            }
        }, "synthetic-live-http");
        worker.setDaemon(true);
        worker.start();
    }

    public String url(int index) { return "http://127.0.0.1:" + server.getLocalPort() + "/" + index + ".mp4"; }

    private void serve(Socket socket) throws IOException {
        BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
        String request = input.readLine();
        if (request == null || !request.matches("(?:GET|HEAD) /[0-4]\\.mp4 HTTP/1\\.[01]")) return;
        int start = 0, end = bytes.length - 1;
        boolean range = false;
        for (int count = 0; count < 32; count++) {
            String header = input.readLine();
            if (header == null || header.isEmpty()) break;
            if (header.toLowerCase(Locale.ROOT).startsWith("range: bytes=")) {
                String[] parts = header.substring(13).trim().split("-", -1);
                try {
                    if (parts.length != 2 || parts[0].isEmpty()) return;
                    start = Integer.parseInt(parts[0]);
                    if (!parts[1].isEmpty()) end = Math.min(end, Integer.parseInt(parts[1]));
                    range = true;
                } catch (NumberFormatException ignored) { return; }
            }
        }
        OutputStream output = socket.getOutputStream();
        if (start < 0 || start > end) {
            output.write(("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */" + bytes.length + "\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            return;
        }
        String header = "HTTP/1.1 " + (range ? "206 Partial Content" : "200 OK") +
            "\r\nContent-Type: video/mp4\r\nAccept-Ranges: bytes\r\nContent-Length: " + (end - start + 1) +
            (range ? "\r\nContent-Range: bytes " + start + "-" + end + "/" + bytes.length : "") + "\r\nConnection: close\r\n\r\n";
        output.write(header.getBytes(StandardCharsets.US_ASCII));
        if (!request.startsWith("HEAD ")) output.write(bytes, start, end - start + 1);
        output.flush();
    }

    @Override public void close() throws IOException {
        server.close();
        Socket socket = active;
        if (socket != null) socket.close();
        try { worker.join(1000); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
    }
}
