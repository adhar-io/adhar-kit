package com.adhar.kit.security.jwks;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Manages the RSA signing keys used to issue and verify JWTs, and exposes the public
 * half as a JWKS for downstream verifiers.
 *
 * <p>Holds a single <em>current</em> signing key plus a bounded set of retired keys
 * retained for a verification window. Rotation generates a fresh key (with a new
 * {@code kid}), promotes it to current, and trims retired keys beyond the configured
 * retention count. Tokens signed by a still-retained key continue to verify, so
 * rotation does not immediately invalidate outstanding tokens.</p>
 *
 * <ul>
 *   <li>{@link #sign(JWTClaimsSet)} issues an RS256 JWT with the current key, stamping
 *       its {@code kid} into the header.</li>
 *   <li>{@link #verify(String)} / {@link #parseAndVerify(String)} validate a token
 *       against whichever retained key matches the token's {@code kid}.</li>
 *   <li>{@link #getPublicJwkSet()} returns the public keys for the JWKS endpoint.</li>
 * </ul>
 *
 * <p>This class references the optional Nimbus JOSE library and is only instantiated
 * by beans gated with {@code @ConditionalOnClass}, so the module compiles and runs
 * without it.</p>
 *
 * @author Adhar Platform Team
 * @since 1.3.0
 */
@Slf4j
public class JwksKeyManager {

    private final int keySize;
    private final int retainKeys;

    /**
     * Signing keys, current key first, followed by retired keys (most recent first).
     * Replaced atomically; treated as immutable once published to the field.
     */
    private volatile List<RSAKey> keys;

    /**
     * Creates a key manager with a 2048-bit key size retaining one previous key.
     */
    public JwksKeyManager() {
        this(2048, 1);
    }

    /**
     * Creates a key manager.
     *
     * @param keySize RSA key size in bits (e.g. 2048)
     * @param retainKeys number of <em>previous</em> keys to retain for verification
     *                   (in addition to the current key); negative values treated as 0
     */
    public JwksKeyManager(int keySize, int retainKeys) {
        this.keySize = keySize;
        this.retainKeys = Math.max(0, retainKeys);
        this.keys = List.of(generateKey());
        log.info("JWKS key manager initialized (keySize: {}, retainKeys: {}, current kid: {})",
            keySize, this.retainKeys, this.keys.get(0).getKeyID());
    }

    private RSAKey generateKey() {
        try {
            return new RSAKeyGenerator(keySize)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .keyID(UUID.randomUUID().toString())
                .generate();
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to generate RSA signing key", e);
        }
    }

    /**
     * Rotates the signing key: a fresh key becomes current and older keys beyond the
     * retention window are dropped.
     *
     * @return the {@code kid} of the new current key
     */
    public synchronized String rotate() {
        RSAKey newKey = generateKey();
        List<RSAKey> updated = new ArrayList<>();
        updated.add(newKey);
        // Keep up to retainKeys previous keys (the old current plus older retired ones).
        List<RSAKey> previous = this.keys;
        for (int i = 0; i < previous.size() && i < retainKeys; i++) {
            updated.add(previous.get(i));
        }
        this.keys = List.copyOf(updated);
        log.info("Rotated JWKS signing key. New current kid: {} (retaining {} key(s))",
            newKey.getKeyID(), updated.size());
        return newKey.getKeyID();
    }

    /**
     * @return the {@code kid} of the current signing key
     */
    public String getCurrentKid() {
        return keys.get(0).getKeyID();
    }

    /**
     * @return an immutable snapshot of all retained keys (current first)
     */
    public List<RSAKey> getKeys() {
        return keys;
    }

    /**
     * @return the public JWKS (public keys only) suitable for publishing
     */
    public JWKSet getPublicJwkSet() {
        List<com.nimbusds.jose.jwk.JWK> publicKeys = new ArrayList<>();
        for (RSAKey key : keys) {
            publicKeys.add(key.toPublicJWK());
        }
        return new JWKSet(publicKeys);
    }

    /**
     * Signs the given claims into an RS256 JWT using the current key.
     *
     * @param claims the claims to sign
     * @return the serialized, signed JWT
     */
    public String sign(JWTClaimsSet claims) {
        RSAKey signingKey = keys.get(0);
        try {
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(signingKey.getKeyID())
                .type(com.nimbusds.jose.JOSEObjectType.JWT)
                .build();
            SignedJWT signedJwt = new SignedJWT(header, claims);
            signedJwt.sign(new RSASSASigner(signingKey));
            return signedJwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to sign JWT", e);
        }
    }

    /**
     * Parses and verifies a token against the retained keys, also enforcing the
     * expiration claim when present.
     *
     * @param token the serialized JWT
     * @return the verified {@link SignedJWT}
     * @throws JwtVerificationException if the token is malformed, has no matching key,
     *                                  fails signature verification, or is expired
     */
    public SignedJWT parseAndVerify(String token) {
        if (token == null || token.isBlank()) {
            throw new JwtVerificationException("Token is empty");
        }
        try {
            SignedJWT signedJwt = SignedJWT.parse(token);
            String kid = signedJwt.getHeader().getKeyID();

            RSAKey match = null;
            for (RSAKey key : keys) {
                if (key.getKeyID().equals(kid)) {
                    match = key;
                    break;
                }
            }
            if (match == null) {
                throw new JwtVerificationException("No signing key matches kid: " + kid);
            }

            if (!signedJwt.verify(new RSASSAVerifier(match.toPublicJWK()))) {
                throw new JwtVerificationException("Signature verification failed");
            }

            Date expiration = signedJwt.getJWTClaimsSet().getExpirationTime();
            if (expiration != null && expiration.before(Date.from(Instant.now()))) {
                throw new JwtVerificationException("Token has expired");
            }

            return signedJwt;
        } catch (JwtVerificationException e) {
            throw e;
        } catch (Exception e) {
            throw new JwtVerificationException("Invalid token: " + e.getMessage(), e);
        }
    }

    /**
     * Whether a token is well-formed, correctly signed by a retained key and not expired.
     *
     * @param token the serialized JWT
     * @return {@code true} if the token verifies
     */
    public boolean verify(String token) {
        try {
            parseAndVerify(token);
            return true;
        } catch (JwtVerificationException e) {
            log.debug("Token verification failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Thrown when a token fails verification.
     */
    public static class JwtVerificationException extends RuntimeException {
        public JwtVerificationException(String message) {
            super(message);
        }

        public JwtVerificationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
