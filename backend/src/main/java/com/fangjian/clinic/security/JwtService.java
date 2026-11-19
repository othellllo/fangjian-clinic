package com.fangjian.clinic.security;

import com.fangjian.clinic.domain.enums.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

/**
 * JWT 签发与解析。
 */
@Component
public class JwtService {

    private final SecretKey key;
    private final Duration expire;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expire-minutes:720}") long expireMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expire = Duration.ofMinutes(expireMinutes);
    }

    public String generate(LoginUser user) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(user.id()))
                .claim("username", user.username())
                .claim("realName", user.realName())
                .claim("role", user.role().name())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expire.toMillis()))
                .signWith(key)
                .compact();
    }

    public LoginUser parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new LoginUser(
                Long.valueOf(claims.getSubject()),
                claims.get("username", String.class),
                claims.get("realName", String.class),
                UserRole.valueOf(claims.get("role", String.class)));
    }
}
