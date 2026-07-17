package com.jeequan.jeepay.pay.channel.wxpay.kits;

import com.github.binarywang.wxpay.config.WxPayConfig;
import com.github.binarywang.wxpay.config.WxPayHttpProxy;
import com.github.binarywang.wxpay.util.HttpProxyUtils;
import com.github.binarywang.wxpay.v3.WxPayV3HttpClientBuilder;
import com.github.binarywang.wxpay.v3.auth.AutoUpdateCertificatesVerifier;
import com.github.binarywang.wxpay.v3.auth.PrivateKeySigner;
import com.github.binarywang.wxpay.v3.auth.PublicCertificateVerifier;
import com.github.binarywang.wxpay.v3.auth.Verifier;
import com.github.binarywang.wxpay.v3.auth.WxPayCredentials;
import com.github.binarywang.wxpay.v3.auth.WxPayValidator;
import com.github.binarywang.wxpay.v3.util.PemUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.commons.lang3.StringUtils;

import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;

public final class WxpayV3VerifierFactory {

    private final PlatformVerifierFactory platformVerifierFactory;
    private final PublicKeyVerifierFactory publicKeyVerifierFactory;
    private final ApiV3HttpClientFactory apiV3HttpClientFactory;

    public WxpayV3VerifierFactory() {
        this(WxpayV3VerifierFactory::createPlatformVerifier,
                WxpayV3VerifierFactory::createPublicKeyVerifier,
                WxpayV3VerifierFactory::createApiV3HttpClient);
    }

    WxpayV3VerifierFactory(PlatformVerifierFactory platformVerifierFactory,
                           PublicKeyVerifierFactory publicKeyVerifierFactory,
                           ApiV3HttpClientFactory apiV3HttpClientFactory) {
        this.platformVerifierFactory = platformVerifierFactory;
        this.publicKeyVerifierFactory = publicKeyVerifierFactory;
        this.apiV3HttpClientFactory = apiV3HttpClientFactory;
    }

    public Verifier create(WxPayConfig config) {
        boolean publicKeyConfigured = validatePublicKeyConfiguration(config);
        WxpayV3NetworkSettings networkSettings = WxpayV3NetworkSettings.from(config);
        return create(config, networkSettings, publicKeyConfigured);
    }

    private Verifier create(WxPayConfig config, WxpayV3NetworkSettings networkSettings,
                            boolean publicKeyConfigured) {
        try {
            Verifier platformVerifier = platformVerifierFactory.create(config, networkSettings);
            if (!publicKeyConfigured) {
                return platformVerifier;
            }
            Verifier publicKeyVerifier = publicKeyVerifierFactory.create(config);
            return new WechatpaySerialDispatchingVerifier(platformVerifier, publicKeyVerifier);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to initialize WeChat Pay verifier", e);
        }
    }

    public void install(WxPayConfig config) {
        boolean publicKeyConfigured = validatePublicKeyConfiguration(config);
        WxpayV3NetworkSettings networkSettings = WxpayV3NetworkSettings.from(config);
        try {
            Verifier verifier = create(config, networkSettings, publicKeyConfigured);
            CloseableHttpClient apiV3HttpClient = apiV3HttpClientFactory.create(
                    config, verifier, networkSettings);
            config.setVerifier(verifier);
            config.setApiV3HttpClient(apiV3HttpClient);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to initialize WeChat Pay verifier", e);
        }
    }

    private static boolean validatePublicKeyConfiguration(WxPayConfig config) {
        boolean publicKeyIdBlank = StringUtils.isBlank(config.getPublicKeyId());
        boolean publicKeyPathBlank = StringUtils.isBlank(config.getPublicKeyPath());
        if (publicKeyIdBlank != publicKeyPathBlank) {
            throw new IllegalStateException("Incomplete WeChat Pay public key configuration");
        }
        return !publicKeyIdBlank;
    }

    private static Verifier createPlatformVerifier(WxPayConfig config,
                                                   WxpayV3NetworkSettings networkSettings) throws Exception {
        try (FileInputStream inputStream = new FileInputStream(config.getPrivateKeyPath())) {
            PrivateKey privateKey = PemUtils.loadPrivateKey(inputStream);
            WxPayCredentials credentials = new WxPayCredentials(
                    config.getMchId(),
                    new PrivateKeySigner(config.getCertSerialNo(), privateKey)
            );
            Verifier verifier = new AutoUpdateCertificatesVerifier(
                    credentials,
                    config.getApiV3Key().getBytes(StandardCharsets.UTF_8),
                    networkSettings.certAutoUpdateTime(),
                    networkSettings.payBaseUrl(),
                    networkSettings.httpProxy()
            );
            config.setPrivateKey(privateKey);
            return verifier;
        }
    }

    private static Verifier createPublicKeyVerifier(WxPayConfig config) throws Exception {
        try (FileInputStream inputStream = new FileInputStream(config.getPublicKeyPath())) {
            PublicKey publicKey = PemUtils.loadPublicKey(inputStream);
            return new PublicCertificateVerifier(publicKey, config.getPublicKeyId());
        }
    }

    private static CloseableHttpClient createApiV3HttpClient(WxPayConfig config, Verifier verifier,
                                                              WxpayV3NetworkSettings networkSettings) {
        WxPayV3HttpClientBuilder builder = WxPayV3HttpClientBuilder.create()
                .withMerchant(config.getMchId(), config.getCertSerialNo(), config.getPrivateKey())
                .withValidator(new WxPayValidator(verifier));
        HttpProxyUtils.initHttpProxy(builder, networkSettings.httpProxy());
        if (config.getApiV3HttpClientBuilderCustomizer() != null) {
            config.getApiV3HttpClientBuilderCustomizer().customize(builder);
        }
        return builder.build();
    }

}

record WxpayV3NetworkSettings(String payBaseUrl, int certAutoUpdateTime,
                              WxPayHttpProxy httpProxy) {

    static WxpayV3NetworkSettings from(WxPayConfig config) {
        WxPayHttpProxy httpProxy = null;
        if (StringUtils.isNotBlank(config.getHttpProxyHost())
                && config.getHttpProxyPort() != null
                && config.getHttpProxyPort() > 0) {
            httpProxy = new WxPayHttpProxy(
                    config.getHttpProxyHost(),
                    config.getHttpProxyPort(),
                    config.getHttpProxyUsername(),
                    config.getHttpProxyPassword()
            );
        }
        return new WxpayV3NetworkSettings(
                config.getApiHostWithPathPrefix(),
                config.getCertAutoUpdateTime(),
                httpProxy
        );
    }
}

@FunctionalInterface
interface PlatformVerifierFactory {
    Verifier create(WxPayConfig config, WxpayV3NetworkSettings networkSettings) throws Exception;
}

@FunctionalInterface
interface PublicKeyVerifierFactory {
    Verifier create(WxPayConfig config) throws Exception;
}

@FunctionalInterface
interface ApiV3HttpClientFactory {
    CloseableHttpClient create(WxPayConfig config, Verifier verifier,
                               WxpayV3NetworkSettings networkSettings) throws Exception;
}
