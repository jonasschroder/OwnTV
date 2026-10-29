import tv.own.owntv.home.LoopbackVideoFixture;
import java.net.URI;
import java.net.http.*;
import java.util.Arrays;

/** Plain JDK smoke check; no Android install or external network. Run instructions in README. */
public final class LoopbackVideoFixtureSmoke {
    public static void main(String[] args) throws Exception {
        byte[] bytes = new byte[400];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) i;
        try (LoopbackVideoFixture fixture = new LoopbackVideoFixture(bytes)) {
            HttpClient client = HttpClient.newHttpClient();
            URI uri = URI.create(fixture.url(0));
            HttpResponse<byte[]> full = client.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofByteArray());
            if (full.statusCode() != 200 || !Arrays.equals(bytes, full.body())) throw new AssertionError("GET");
            HttpResponse<byte[]> regional = client.send(HttpRequest.newBuilder(URI.create(fixture.url(4))).build(), HttpResponse.BodyHandlers.ofByteArray());
            if (regional.statusCode() != 200 || !Arrays.equals(bytes, regional.body())) throw new AssertionError("Regional fixture");
            HttpResponse<byte[]> range = client.send(HttpRequest.newBuilder(uri).header("Range", "bytes=20-39").build(), HttpResponse.BodyHandlers.ofByteArray());
            if (range.statusCode() != 206 || !Arrays.equals(Arrays.copyOfRange(bytes, 20, 40), range.body())) throw new AssertionError("Range");
            HttpResponse<byte[]> head = client.send(HttpRequest.newBuilder(uri).method("HEAD", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofByteArray());
            if (head.statusCode() != 200 || head.body().length != 0 || !head.headers().firstValue("Content-Length").orElse("").equals("400")) throw new AssertionError("HEAD");
            HttpResponse<byte[]> invalid = client.send(HttpRequest.newBuilder(uri).header("Range", "bytes=500-").build(), HttpResponse.BodyHandlers.ofByteArray());
            if (invalid.statusCode() != 416) throw new AssertionError("Invalid range");
        }
        System.out.println("PASS: loopback GET, HEAD, byte range, invalid range and close");
    }
}
