package com.xgen.mongot.util.security;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import org.junit.Test;

public class AesGcmCipherTest {

  private static final byte[] KEY = new byte[32];

  static {
    for (int i = 0; i < KEY.length; i++) {
      KEY[i] = (byte) i;
    }
  }

  @Test
  public void encrypt_decrypt_roundTrip_succeeds() throws Exception {
    byte[] plaintext = "hello world".getBytes(StandardCharsets.UTF_8);

    byte[] encrypted = AesGcmCipher.encrypt(KEY, plaintext);
    byte[] decrypted = AesGcmCipher.decrypt(KEY, encrypted);

    assertThat(decrypted).isEqualTo(plaintext);
  }

  @Test
  public void encrypt_outputFormat_correctLength() throws Exception {
    byte[] plaintext = new byte[] {1, 2, 3};

    byte[] encrypted = AesGcmCipher.encrypt(KEY, plaintext);

    // Output should be: 96-bit IV + plaintext + 128-bit auth tag
    assertThat(encrypted.length).isEqualTo(96 / Byte.SIZE + plaintext.length + 128 / Byte.SIZE);
  }

  @Test
  public void encrypt_samePlaintext_producesDifferentOutput() throws Exception {
    byte[] plaintext = "same input".getBytes(StandardCharsets.UTF_8);

    byte[] first = AesGcmCipher.encrypt(KEY, plaintext);
    byte[] second = AesGcmCipher.encrypt(KEY, plaintext);

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  public void decrypt_tamperedCiphertext_throwsGeneralSecurityException() throws Exception {
    byte[] encrypted = AesGcmCipher.encrypt(KEY, "tamper test".getBytes(StandardCharsets.UTF_8));

    // Flip a byte in the ciphertext area (after the 12-byte IV)
    encrypted[13] ^= (byte) 0xFF;

    assertThrows(GeneralSecurityException.class, () -> AesGcmCipher.decrypt(KEY, encrypted));
  }

  @Test
  public void decrypt_wrongKey_throwsGeneralSecurityException() throws Exception {
    byte[] encrypted = AesGcmCipher.encrypt(KEY, "wrong key test".getBytes(StandardCharsets.UTF_8));

    byte[] wrongKey = Arrays.copyOf(KEY, KEY.length);
    wrongKey[0] ^= (byte) 0xFF;

    assertThrows(GeneralSecurityException.class, () -> AesGcmCipher.decrypt(wrongKey, encrypted));
  }

  @Test
  public void decrypt_inputLength_correctlyValidated() throws Exception {
    // Empty plaintext produces the minimum valid length and should round-trip.
    byte[] encrypted = AesGcmCipher.encrypt(KEY, new byte[0]);
    assertThat(AesGcmCipher.decrypt(KEY, encrypted)).isEmpty();

    // One byte of plaintext should also round-trip.
    byte[] oneByte = AesGcmCipher.encrypt(KEY, new byte[] {42});
    assertThat(AesGcmCipher.decrypt(KEY, oneByte)).isEqualTo(new byte[] {42});

    // Truncated ciphertext below the minimum valid length is invalid.
    byte[] truncated = Arrays.copyOf(encrypted, encrypted.length - 1);
    assertThrows(IllegalArgumentException.class, () -> AesGcmCipher.decrypt(KEY, truncated));
  }
}
