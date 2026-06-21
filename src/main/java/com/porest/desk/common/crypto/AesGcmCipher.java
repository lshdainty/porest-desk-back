package com.porest.desk.common.crypto;

import com.porest.desk.common.config.properties.AppProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 대칭 암호화 유틸. 민감값(토스 client_id/secret)을 DB에 암호문으로 저장하기 위해 사용.
 * 출력 형식: {@code Base64(IV(12B) ‖ ciphertext‖tag)} 단일 문자열(별도 IV 컬럼 불필요).
 *
 * <p>키는 {@code app.security.encryption-key}(Base64 인코딩된 32바이트, env 주입)에서 읽는다.
 * 키 미설정 시 암호화/복호화 호출에서 {@link IllegalStateException} 을 던진다.</p>
 */
@Component
public class AesGcmCipher {
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecureRandom random = new SecureRandom();
    private final byte[] key;

    public AesGcmCipher(AppProperties appProperties) {
        String b64 = appProperties.getSecurity().getEncryptionKey();
        this.key = (b64 == null || b64.isBlank()) ? null : Base64.getDecoder().decode(b64);
        if (this.key != null && this.key.length != 32) {
            throw new IllegalStateException("app.security.encryption-key 는 Base64 인코딩된 32바이트(AES-256)여야 합니다");
        }
    }

    /** 평문을 암호화해 Base64(IV‖ciphertext) 문자열로 반환. */
    public String encrypt(String plaintext) {
        byte[] iv = new byte[IV_LENGTH];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] out = ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array();
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("암호화 실패", e);
        }
    }

    /** Base64(IV‖ciphertext) 문자열을 복호화해 평문 반환. */
    public String decrypt(String enc) {
        try {
            byte[] all = Base64.getDecoder().decode(enc);
            byte[] iv = new byte[IV_LENGTH];
            byte[] ct = new byte[all.length - IV_LENGTH];
            System.arraycopy(all, 0, iv, 0, IV_LENGTH);
            System.arraycopy(all, IV_LENGTH, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ct), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("복호화 실패", e);
        }
    }

    public boolean isConfigured() {
        return key != null;
    }

    private SecretKeySpec secretKey() {
        if (key == null) {
            throw new IllegalStateException("app.security.encryption-key 가 설정되지 않았습니다");
        }
        return new SecretKeySpec(key, "AES");
    }
}
