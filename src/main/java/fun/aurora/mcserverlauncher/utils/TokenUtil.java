
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

    //token生成器
    public String generateToken(String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + tokenConfig.getExpireTime() * 1000L);

        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .setId(UUID.randomUUID().toString())
                .signWith(SignatureAlgorithm.HS512, tokenConfig.getSecretKey())
                .compact();
    }

    //验证token
    public boolean validateToken(String token) {
        try {
            Jwts.parser().setSigningKey(tokenConfig.getSecretKey()).parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            logger.warn("Invalid token: {}", e.getMessage());
            return false;
        }
    }

    //从token中获取用户名
    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parser()
                .setSigningKey(tokenConfig.getSecretKey())
                .parseClaimsJws(token)
                .getBody();
        return claims.getSubject();
    }
}