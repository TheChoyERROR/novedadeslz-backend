package com.novedadeslz.backend.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HexFormat;

@Component
public class JwtTokenProvider {

    /** Longitud minima exigida por HS512. */
    private static final int MIN_SECRET_LENGTH_BYTES = 64;

    /** Secreto que se publico en el repositorio; jamas debe firmar tokens reales. */
    private static final String LEAKED_DEFAULT_SECRET =
            "TuClaveSecretaSuperSeguraDeAlMenos64CaracteresParaHS512Algorithm";

    @Value("${jwt.secret:}")
    private String jwtSecret;

    @Value("${jwt.expiration:86400000}") // 24 horas en ms
    private long jwtExpirationMs;

    @Value("${app.whatsapp-approval-link-expiration-minutes:20}")
    private long whatsappApprovalLinkExpirationMinutes;

    /**
     * Falla el arranque si el secreto no esta configurado, es demasiado corto o sigue siendo el
     * valor que estuvo versionado en el repositorio. Es preferible no arrancar a firmar tokens de
     * administrador con una clave que cualquiera puede leer en GitHub.
     */
    @PostConstruct
    void validateSecret() {
        if (!StringUtils.hasText(jwtSecret)) {
            throw new IllegalStateException(
                    "JWT_SECRET no esta configurado. Genera uno con `openssl rand -base64 64` " +
                            "y agregalo a las variables de entorno del servicio."
            );
        }

        if (LEAKED_DEFAULT_SECRET.equals(jwtSecret)) {
            throw new IllegalStateException(
                    "JWT_SECRET usa el valor por defecto que estuvo publicado en el repositorio. " +
                            "Genera uno nuevo con `openssl rand -base64 64`."
            );
        }

        int secretLength = jwtSecret.getBytes(StandardCharsets.UTF_8).length;
        if (secretLength < MIN_SECRET_LENGTH_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET debe tener al menos " + MIN_SECRET_LENGTH_BYTES +
                            " bytes para HS512 (actual: " + secretLength + ")."
            );
        }
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(Authentication authentication) {
        UserDetails userPrincipal = (UserDetails) authentication.getPrincipal();

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
            .setSubject(userPrincipal.getUsername())
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .signWith(getSigningKey(), SignatureAlgorithm.HS512)
            .compact();
    }

    /**
     * El token queda atado al comprobante concreto que se esta revisando. Si el cliente sube una
     * captura nueva, el enlace anterior deja de servir aunque no haya expirado: evita que un
     * mensaje reenviado apruebe un comprobante distinto del que se reviso.
     */
    public String generateWhatsAppApprovalToken(Long orderId, String paymentProofUrl) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + (whatsappApprovalLinkExpirationMinutes * 60_000));

        return Jwts.builder()
                .setSubject("whatsapp-approval")
                .claim("action", "approve-payment")
                .claim("orderId", orderId)
                .claim("proof", fingerprintOf(paymentProofUrl))
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    public boolean validateWhatsAppApprovalToken(
            String token,
            Long expectedOrderId,
            String currentPaymentProofUrl) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String subject = claims.getSubject();
            String action = claims.get("action", String.class);
            Number orderIdClaim = claims.get("orderId", Number.class);
            String proofClaim = claims.get("proof", String.class);

            return "whatsapp-approval".equals(subject) &&
                    "approve-payment".equals(action) &&
                    orderIdClaim != null &&
                    expectedOrderId.equals(orderIdClaim.longValue()) &&
                    fingerprintOf(currentPaymentProofUrl).equals(proofClaim);
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    /** Hash corto de la URL del comprobante; no hace falta guardar la URL completa en el token. */
    private String fingerprintOf(String paymentProofUrl) {
        String value = paymentProofUrl == null ? "" : paymentProofUrl;

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", ex);
        }
    }

    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();

        return claims.getSubject();
    }

    public boolean validateToken(String authToken) {
        try {
            Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(authToken);
            return true;
        } catch (SecurityException ex) {
            // Invalid JWT signature
        } catch (MalformedJwtException ex) {
            // Invalid JWT token
        } catch (ExpiredJwtException ex) {
            // Expired JWT token
        } catch (UnsupportedJwtException ex) {
            // Unsupported JWT token
        } catch (IllegalArgumentException ex) {
            // JWT claims string is empty
        }
        return false;
    }

    public long getExpirationMs() {
        return jwtExpirationMs;
    }

    public long getWhatsAppApprovalLinkExpirationMinutes() {
        return whatsappApprovalLinkExpirationMinutes;
    }
}
