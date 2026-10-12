package com.codeSense_Ai.Utility;

import org.bouncycastle.asn1.pkcs.RSAPrivateKey;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
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
                    "Loading GitHub private key from resources: "
                            + pemFilePath
            );

            ClassPathResource resource =
                    new ClassPathResource(pemFilePath);

            if (!resource.exists()) {
                throw new RuntimeException(
                        "Private key file not found in resources: "
                                + pemFilePath
                );
            }

            String pem;

            try (InputStream inputStream = resource.getInputStream()) {
                pem = new String(
                        inputStream.readAllBytes()
                );
            }

            String base64 = pem
                    .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                    .replace("-----END RSA PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");

            byte[] keyBytes =
                    Base64.getDecoder().decode(base64);

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

            KeyFactory keyFactory =
                    KeyFactory.getInstance("RSA");

            PrivateKey privateKey =
                    keyFactory.generatePrivate(keySpec);

            System.out.println(
                    "GitHub private key loaded successfully"
            );

            return privateKey;

        } catch (Exception e) {

            e.printStackTrace();

            throw new RuntimeException(
                    "Failed to load GitHub App private key: "
                            + e.getMessage(),
                    e
            );
        }
    }
}
