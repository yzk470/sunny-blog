package com.sunnyblog.security;

import com.sunnyblog.entity.BlogUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expireHours;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expire-hours:168}") long expireHours) {
        this.key = Keys.hmacShaKeyFor(normalize(secret));
        this.expireHours = expireHours;
    }

    public String generate(BlogUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("nickname", user.getNickname())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expireHours * 3600)))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getExpireSeconds() {
        return expireHours * 3600;
    }

    private byte[] normalize(String secret) {
        try {
            byte[] raw = secret.getBytes(StandardCharsets.UTF_8);
            if (raw.length >= 32) {
                return raw;
            }
            return MessageDigest.getInstance("SHA-256").digest(raw);
        } catch (Exception ex) {
            throw new IllegalStateException("无法初始化 JWT 密钥", ex);
        }
    }
}
