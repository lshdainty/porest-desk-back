package com.porest.desk.common.crypto;

import com.porest.desk.common.config.properties.AppProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 민감값 암호화 프로세스 — AES-GCM 라운드트립·IV 랜덤성·키 미설정 차단.
 */
class AesGcmCipherTest {

    private AesGcmCipher cipherWithKey() {
        AppProperties props = new AppProperties();
        props.getSecurity().setEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
        return new AesGcmCipher(props);
    }

    @Test
    @DisplayName("암호화한 값을 복호화하면 원문이 복원된다")
    void roundTrip() {
        AesGcmCipher cipher = cipherWithKey();
        String plain = "c_01HXYZABCDEFG123456789";

        String enc = cipher.encrypt(plain);

        assertThat(enc).isNotEqualTo(plain);
        assertThat(cipher.decrypt(enc)).isEqualTo(plain);
    }

    @Test
    @DisplayName("같은 평문도 IV 랜덤으로 매번 다른 암호문(동일 평문 노출 방지)")
    void ivRandomness() {
        AesGcmCipher cipher = cipherWithKey();
        assertThat(cipher.encrypt("same")).isNotEqualTo(cipher.encrypt("same"));
    }

    @Test
    @DisplayName("키 미설정 시 암호화 호출은 차단된다")
    void missingKey() {
        AesGcmCipher cipher = new AesGcmCipher(new AppProperties()); // 키 없음
        assertThat(cipher.isConfigured()).isFalse();
        assertThatThrownBy(() -> cipher.encrypt("x")).isInstanceOf(IllegalStateException.class);
    }
}
