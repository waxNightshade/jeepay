package com.jeequan.jeepay.pay.channel.wxpay.kits;

import com.github.binarywang.wxpay.v3.auth.Verifier;

import java.security.cert.X509Certificate;
import java.util.Objects;
import java.util.regex.Pattern;

public final class WechatpaySerialDispatchingVerifier implements Verifier {

    private static final String PUBLIC_KEY_PREFIX = "PUB_KEY_ID_";
    private static final Pattern PLATFORM_CERTIFICATE_SERIAL = Pattern.compile("[0-9A-Fa-f]+");

    private final Verifier platformCertificateVerifier;
    private final Verifier publicKeyVerifier;

    public WechatpaySerialDispatchingVerifier(Verifier platformCertificateVerifier, Verifier publicKeyVerifier) {
        this.platformCertificateVerifier = Objects.requireNonNull(platformCertificateVerifier);
        this.publicKeyVerifier = Objects.requireNonNull(publicKeyVerifier);
    }

    @Override
    public boolean verify(String serialNumber, byte[] message, String signature) {
        if (serialNumber == null) {
            return false;
        }
        if (serialNumber.startsWith(PUBLIC_KEY_PREFIX)) {
            return publicKeyVerifier.verify(serialNumber, message, signature);
        }
        if (PLATFORM_CERTIFICATE_SERIAL.matcher(serialNumber).matches()) {
            return platformCertificateVerifier.verify(serialNumber, message, signature);
        }
        return false;
    }

    @Override
    public X509Certificate getValidCertificate() {
        return platformCertificateVerifier.getValidCertificate();
    }
}
