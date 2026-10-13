package com.codeSense_Ai.Utility;

import org.bouncycastle.asn1.pkcs.RSAPrivateKey;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.util.Base64;

@Component
public class RsaKeyLoader {

    public PrivateKey loadPrivateKey(String pemFilePath) {
        try {
            System.out.println(
                    "Loading GitHub private key from: " + pemFilePath
            );

            String pem;

            // First, try the external filesystem path (Render).
            Path path = Path.of(pemFilePath);

            if (path.isAbsolute() && Files.isRegularFile(path)) {
                pem = Files.readString(path, StandardCharsets.UTF_8);
            } else {
                // Otherwise, try the classpath (local development).
                ClassPathResource resource =
                        new ClassPathResource(pemFilePath);

                if (!resource.exists()) {
                    throw new RuntimeException(
                            "Private key not found at filesystem path "
                                    + "or classpath: " + pemFilePath
                    );
                }

                try (InputStream inputStream = resource.getInputStream()) {
                    pem = new String(
                            inputStream.readAllBytes(),
                            StandardCharsets.UTF_8
                    );
                }
            }

            String base64 = pem
                    .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                    .replace("-----END RSA PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");

            byte[] keyBytes = Base64.getDecoder().decode(base64);

            RSAPrivateKey rsaPrivateKey =
                    RSAPrivateKey.getInstance(keyBytes);

            RSAPrivateCrtKeySpec keySpec =
                    new RSAPrivateCrtKeySpec(
                            rsaPrivateKey.getModulus(),
                            rsaPrivateKey.getPublicExponent(),
                            rsaPrivateKey.getPrivateExponent(),
                            rsaPrivateKey.getPrime1(),
                            rsaPrivateKey.getPrime2(),
                            rsaPrivateKey.getExponent1(),
                            rsaPrivateKey.getExponent2(),
                            rsaPrivateKey.getCoefficient()
                    );

            PrivateKey privateKey = KeyFactory.getInstance("RSA")
                    .generatePrivate(keySpec);

            System.out.println("GitHub private key loaded successfully");

            return privateKey;

        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to load GitHub App private key: "
                            + e.getMessage(),
                    e
            );
        }
    }
}
