package com.novedadeslz.backend.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    @Value("${jwt.secret:TuClaveSecretaSuperSeguraDeAlMenos64CaracteresParaHS512Algorithm}")
    private String jwtSecret;

    @Value("${jwt.expiration:86400000}") // 24 horas en ms
    private long jwtExpirationMs;

    @Value("${app.whatsapp-approval-link-expiration-minutes:20}")
    private long whatsappApprovalLinkExpirationMinutes;

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

    public String generateWhatsAppApprovalToken(Long orderId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + (whatsappApprovalLinkExpirationMinutes * 60_000));

        return Jwts.builder()
                .setSubject("whatsapp-approval")
                .claim("action", "approve-payment")
                .claim("orderId", orderId)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    public boolean validateWhatsAppApprovalToken(String token, Long expectedOrderId) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String subject = claims.getSubject();
            String action = claims.get("action", String.class);
            Number orderIdClaim = claims.get("orderId", Number.class);

            return "whatsapp-approval".equals(subject) &&
                    "approve-payment".equals(action) &&
                    orderIdClaim != null &&
                    expectedOrderId.equals(orderIdClaim.longValue());
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
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
