package com.xgen.mongot.util.security;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Performs AES-GCM symmetric encryption and decryption and also handles IV generation and encoding.
 * Generates a new random 96-bit IV using {@link Security#getSecureRandom()} on each encryption call
 * and uses a 128-bit authentication tag.
 *
 * <p>The encrypted output is encoded as [IV | ciphertext]. For decryption, the encrypted input is
 * expected to be encoded in the same format.
 */
public final class AesGcmCipher {
  static {
    Security.installFipsSecurityProvider();
  }

  private static final int IV_LENGTH_BITS = 96;
  private static final int AUTHENTICATION_TAG_LENGTH_BITS = 128;

  private static final int IV_LENGTH_BYTES = IV_LENGTH_BITS / Byte.SIZE;
  private static final int AUTHENTICATION_TAG_LENGTH_BYTES =
      AUTHENTICATION_TAG_LENGTH_BITS / Byte.SIZE;
  private static final int MIN_ENCRYPTED_LENGTH = IV_LENGTH_BYTES + AUTHENTICATION_TAG_LENGTH_BYTES;

  private AesGcmCipher() {}

  /**
   * Encrypts plaintext using AES-GCM with a random 96-bit IV and 128-bit authentication tag.
   *
   * <p>Note: Because we use a 96-bit IV, a key should not be used to encrypt more than 2^32
   * messages. Once you pass 2^32 messages, the probability of IV reuse becomes non-negligible, and
   * any reuse of a (IV, key) pair in AES-GCM is a complete confidentiality failure. More details
   * can be found in "Practical Challenges with AES-GCM and the need for a new cipher"
   * (https://tinyurl.com/3x2wfpsm)
   *
   * @param key the symmetric key to encrypt with
   * @param plaintextData the data to encrypt
   * @return the encrypted output with the IV prepended
   * @throws GeneralSecurityException if the cipher operation fails
   */
  public static byte[] encrypt(byte[] key, byte[] plaintextData) throws GeneralSecurityException {
    ByteBuffer output =
        ByteBuffer.allocate(
            IV_LENGTH_BYTES + plaintextData.length + AUTHENTICATION_TAG_LENGTH_BYTES);

    // Write the IV to the front of the output buffer.
    putRandomIv(output);

    // Pass the output buffer as the IV source buffer. The IV will be read from the start of the
    // buffer.
    Cipher cipher = initCipher(Cipher.ENCRYPT_MODE, key, output.array());

    // Encrypt the plaintext data and write the ciphertext to the output buffer after the IV.
    // The cipher will automatically write the authentication tag at the end of the ciphertext.
    output.put(cipher.doFinal(plaintextData));

    return output.array();
  }

  /**
   * Decrypts ciphertext previously encrypted by {@link #encrypt}.
   *
   * @param decryptionKey the symmetric key to decrypt with
   * @param encryptedData the encrypted output from {@link #encrypt}
   * @return the decrypted plaintext
   * @throws GeneralSecurityException if the cipher operation fails or authentication fails
   */
  public static byte[] decrypt(byte[] decryptionKey, byte[] encryptedData)
      throws GeneralSecurityException {
    // The encrypted data must be at least the IV + authentication tag. If it's not, it's malformed
    // and cannot be decrypted.
    if (encryptedData.length < MIN_ENCRYPTED_LENGTH) {
      throw new IllegalArgumentException("Encrypted data malformed: missing ciphertext");
    }

    // Pass encryptedData as the IV source buffer. The IV will be read from the start of the buffer.
    Cipher cipher = initCipher(Cipher.DECRYPT_MODE, decryptionKey, encryptedData);

    // Decrypt the data after the IV.
    return cipher.doFinal(encryptedData, IV_LENGTH_BYTES, encryptedData.length - IV_LENGTH_BYTES);
  }

  /** Writes a random {@link #IV_LENGTH_BITS}-bit IV into the buffer. */
  private static void putRandomIv(ByteBuffer buffer) {
    byte[] iv = new byte[IV_LENGTH_BYTES];
    Security.getSecureRandom().nextBytes(iv);
    buffer.put(iv);
  }

  /**
   * Initializes a cipher, reading the IV from the first {@link #IV_LENGTH_BYTES} of {@code
   * ivSourceBuffer}.
   */
  private static Cipher initCipher(int mode, byte[] keyBytes, byte[] ivSourceBuffer)
      throws GeneralSecurityException {
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding", Security.FIPS_PROVIDER_NAME);
    cipher.init(
        mode,
        new SecretKeySpec(keyBytes, "AES"),
        new GCMParameterSpec(AUTHENTICATION_TAG_LENGTH_BITS, ivSourceBuffer, 0, IV_LENGTH_BYTES));
    return cipher;
  }
}
