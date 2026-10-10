import java.nio.file.*;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;
import java.io.*;

/** JCA only. Never exports a private key or prints native exception/secret details. */
class ManifestSignature {
    public static void main(String[] args) {
        try {
            byte[] payload = Files.readAllBytes(Path.of(args[2]));
            if (payload.length > 24000) throw new IllegalArgumentException();
            Signature signature = Signature.getInstance("SHA256withRSA");
            if (args[0].equals("sign")) {
                KeyStore store = KeyStore.getInstance("PKCS12");
                char[] password = System.getenv("MINTV_KEYSTORE_PASSWORD").toCharArray();
                try (InputStream input = Files.newInputStream(Path.of(args[1]))) { store.load(input, password); }
                Arrays.fill(password, '\0');
                char[] keyPassword = System.getenv("MINTV_KEY_PASSWORD").toCharArray();
                PrivateKey key = (PrivateKey) store.getKey(System.getenv("MINTV_KEY_ALIAS"), keyPassword);
                Arrays.fill(keyPassword, '\0');
                signature.initSign(key);
                signature.update(payload);
                Files.write(Path.of(args[3]), signature.sign());
            } else if (args[0].equals("verify")) {
                X509Certificate cert;
                try (InputStream input = Files.newInputStream(Path.of(args[1]))) {
                    cert = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input);
                }
                signature.initVerify(cert);
                signature.update(payload);
                if (!signature.verify(Files.readAllBytes(Path.of(args[3])))) throw new IllegalArgumentException();
            } else throw new IllegalArgumentException();
        } catch (Exception error) {
            System.err.println("Metadata signature verification/signing failed.");
            System.exit(1);
        }
    }
}
