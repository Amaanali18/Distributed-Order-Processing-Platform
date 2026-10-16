package com.dpp.payment.provider;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

public final class StripeWebhookSignature {

    private static final Duration TOLERANCE = Duration.ofMinutes(5);

    private StripeWebhookSignature() {
    }

    public static boolean verify(String payload, String signatureHeader, String secret) {
        if (payload == null || signatureHeader == null || secret == null || secret.isBlank()) {
            return false;
        }
        Long timestamp = null;
        String expected = null;
        for (String part : signatureHeader.split(",")) {
            String[] pair = part.split("=", 2);
            if (pair.length != 2) {
                continue;
            }
            if ("t".equals(pair[0])) {
                try {
                    timestamp = Long.parseLong(pair[1]);
                } catch (NumberFormatException e) {
                    return false;
                }
            } else if ("v1".equals(pair[0])) {
                expected = pair[1];
            }
        }
        if (timestamp == null || expected == null) {
            return false;
        }
        if (Duration.between(Instant.ofEpochSecond(timestamp), Instant.now()).abs().compareTo(TOLERANCE) > 0) {
            return false;
        }
        String signedPayload = timestamp + "." + payload;
        byte[] computed = hmacSha256(signedPayload, secret);
        byte[] provided = HexFormat.of().parseHex(expected);
        return MessageDigest.isEqual(computed, provided);
    }

    private static byte[] hmacSha256(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to compute webhook signature", e);
        }
    }
}
