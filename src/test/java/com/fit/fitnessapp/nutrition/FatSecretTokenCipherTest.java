package com.fit.fitnessapp.nutrition;

import com.fit.fitnessapp.nutrition.adapter.out.persistence.FatSecretTokenCipher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class FatSecretTokenCipherTest {

    private static final String TEST_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    @Test
    void encryptsAndDecryptsTokenWithoutExposingPlaintext() {
        FatSecretTokenCipher cipher = new FatSecretTokenCipher(TEST_KEY, new MockEnvironment());

        String encrypted = cipher.encrypt("fatsecret-token");

        assertThat(encrypted).isNotEqualTo("fatsecret-token");
        assertThat(encrypted).startsWith("enc:v1:");
        assertThat(cipher.decrypt(encrypted)).isEqualTo("fatsecret-token");
    }

    @Test
    void decryptPassesThroughLegacyPlaintextRows() {
        FatSecretTokenCipher cipher = new FatSecretTokenCipher(TEST_KEY, new MockEnvironment());

        assertThat(cipher.decrypt("legacy-token")).isEqualTo("legacy-token");
    }

    @Test
    void missingEncryptionKeyFailsOutsideTestProfile() {
        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withBean(FatSecretTokenCipher.class);

        contextRunner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseMessage("fatsecret.token-encryption.key is required; "
                            + "set FATSECRET_TOKEN_ENCRYPTION_KEY to a base64 AES key");
        });
    }
}
