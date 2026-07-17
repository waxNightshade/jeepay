package com.jeequan.jeepay.pay.compat.epay.protocol;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class EpaySigner {

    private static final Pattern MD5_SIGNATURE = Pattern.compile("[0-9a-fA-F]{32}");

    private EpaySigner() {
    }

    public static String sign(Map<String, String> params, String key) {
        Objects.requireNonNull(params, "params");
        Objects.requireNonNull(key, "key");

        String canonical = params.entrySet().stream()
                .filter(entry -> !"sign".equals(entry.getKey()))
                .filter(entry -> !"sign_type".equals(entry.getKey()))
                .filter(entry -> entry.getValue() != null && !entry.getValue().isEmpty())
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));

        return md5(canonical + key);
    }

    public static boolean verify(Map<String, String> params, String key, String signature) {
        if (signature == null || !MD5_SIGNATURE.matcher(signature).matches()) {
            return false;
        }

        byte[] expected = sign(params, key).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = signature.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, actual);
    }

    private static String md5(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 is not available", e);
        }
    }
}
