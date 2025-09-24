// [文件名称]: TokenUtil.java
// [修正文件]
package fun.aurora.mcserverlauncher.utils;

import fun.aurora.mcserverlauncher.configs.TokenConfig;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Component
public class TokenUtil {
    private static final Logger logger = LoggerFactory.getLogger(TokenUtil.class);

    @Resource
    private TokenConfig tokenConfig;

    //统一使用SecretKey对象
    private SecretKey getSigningKey() {
        String secretKey = tokenConfig.getSecretKey();
        if (secretKey == null || secretKey.getBytes().length < 64) {
            SecretKey key = Keys.secretKeyFor(SignatureAlgorithm.HS512);
            logger.warn("Generated new secure key for HS512 algorithm");
            return key;
        } else {
            return Keys.hmacShaKeyFor(secretKey.getBytes());
        }
    }

    //token生成器
    public String generateToken(String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + tokenConfig.getExpireTime() * 1000L);

        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .setId(UUID.randomUUID().toString())
                .signWith(getSigningKey(), SignatureAlgorithm.HS512) // [修正] 使用SecretKey对象而不是字符串
                .compact();
    }

    //验证token
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getSigningKey()) // [修正] 使用SecretKey对象
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (ExpiredJwtException e) {
            logger.warn("Token expired: {}", e.getMessage());
            return false;
        } catch (MalformedJwtException e) {
            logger.warn("Invalid token format: {}", e.getMessage());
            return false;
        } catch (JwtException e) {
            logger.warn("Invalid token: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            logger.warn("Error validating token: {}", e.getMessage());
            return false;
        }
    }

    //从token中获取用户名
    public String getUsernameFromToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey()) // [修正] 使用SecretKey对象
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return claims.getSubject();
        } catch (Exception e) {
            logger.warn("Error extracting username from token: {}", e.getMessage());
            return null;
        }
    }

    //检查token是否过期
    public boolean isTokenExpired(String token) {
        try {
            Date expiration = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .getExpiration();
            return expiration.before(new Date());
        } catch (Exception e) {
            logger.warn("Error checking token expiration: {}", e.getMessage());
            return true;
        }
    }

    //获取Token生成时间戳
    public Long getTokenTimestamp(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return claims.getIssuedAt().getTime();
        } catch (Exception e) {
            logger.warn("Error extracting timestamp from token: {}", e.getMessage());
            return null;
        }
    }

    // [修正] 获取Token过期时间戳
    public Long getTokenExpiration(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return claims.getExpiration().getTime();
        } catch (Exception e) {
            logger.warn("Error extracting expiration from token: {}", e.getMessage());
            return null;
        }
    }
}