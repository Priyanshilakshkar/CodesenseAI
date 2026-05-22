package com.codeSense_Ai.Github.webhook;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
@Slf4j
public class WebhookSignatureValidator {

    private static final String  HMAC_ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_PREFIX  = "sha256=";

    public boolean validate(String payload , String recievedSignature , String secret){
        if(recievedSignature == null || !recievedSignature.startsWith(SIGNATURE_PREFIX)){
            return false;
        }

        try{
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8) , HMAC_ALGORITHM);
            mac.init(secretKeySpec);

            byte[] computedHash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String computedSignature = SIGNATURE_PREFIX + HexFormat.of().formatHex(computedHash);

            return MessageDigest.isEqual(
                    computedSignature.getBytes(StandardCharsets.UTF_8),
                    recievedSignature.getBytes(StandardCharsets.UTF_8)
            );
        }catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("HMAC computation failed", e);
            return false;
        }

    }
}
