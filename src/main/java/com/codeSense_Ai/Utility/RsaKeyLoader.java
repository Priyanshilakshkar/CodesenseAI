package com.codeSense_Ai.Utility;

import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

@Component
public class RsaKeyLoader {

    public PrivateKey loadPrivateKey(String pemFilePath) throws Exception{
        String pemContent = Files.readString(Path.of(pemFilePath));

        String privateKeyPem = pemContent
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----" , "")
                .replaceAll("\\s+", "");

        byte[] keyBytes = Base64.getDecoder().decode(privateKeyPem);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(keySpec);
    }
}
