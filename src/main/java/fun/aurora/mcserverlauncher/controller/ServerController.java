package fun.aurora.mcserverlauncher.controller;

import fun.aurora.mcserverlauncher.serviceanddao.LoginService;
import fun.aurora.mcserverlauncher.serviceanddao.RedisService;
import fun.aurora.mcserverlauncher.utils.TokenUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class ServerController {
    private static final Logger logger = LoggerFactory.getLogger(ServerController.class);
    
    @Resource
    private LoginService loginService;

    //注入Token工具和Redis服务
    @Resource
    private TokenUtil tokenUtil;

    @Resource //注入RedisService
    private RedisService redisService;
    
    @PostMapping("/login")
    @CrossOrigin(origins = "*")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> userInfo, HttpServletResponse response) {
        logger.info("Received login request with username: {}", userInfo.get("username"));
        Map<String, Object> responseBody = new HashMap<>();
        HttpHeaders headers = new HttpHeaders();
        
        try {
            Map<String, Object> result = loginService.login(userInfo);
            logger.info("Login service response status: {}", result.get("status"));
            
            // 将原始响应中的headers添加到HTTP响应头中
            if (result.containsKey("headers") && result.get("headers") instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, List<String>> originalHeaders = (Map<String, List<String>>) result.get("headers");
                originalHeaders.forEach((key, values) -> {
                    if (!key.equals(":status")) { // 过滤掉HTTP/2伪头部
                        headers.addAll(key, values);
                    }
                });
            }

            //登录成功时生成token并设置cookie
            if (result.get("status").equals(200)) {
                String username = userInfo.get("username");
                String token = tokenUtil.generateToken(username);

                redisService.storeToken(token, username, 3600); // 1小时过期

                // 设置Cookie
                Cookie tokenCookie = new Cookie("token", token);
                tokenCookie.setHttpOnly(true);
                tokenCookie.setSecure(false); // 生产环境建议设置为true
                tokenCookie.setPath("/");
                tokenCookie.setMaxAge(3600); // 1小时
                response.addCookie(tokenCookie);

                logger.info("Token generated and set for user: {}", username);

                // 将token也放入响应体中，方便前端使用
                responseBody.put("token", token);
            }

            // 将结果中的状态码放入响应中，但始终返回HTTP 200
            responseBody.put("httpStatus", 200);

            // 将body和status添加到响应中，但不包含headers
            responseBody.put("body", result.get("body"));
            responseBody.put("status", result.get("status"));

        } catch (Exception e) {
            logger.error("Error occurred during login process for user: {}", userInfo.get("username"), e);
            // 出现异常时也返回HTTP 200，但包含错误信息
            responseBody.put("httpStatus", 500);
            responseBody.put("error", e.getMessage());
        }

        logger.info("Login request completed with httpStatus: {}", responseBody.get("httpStatus"));
        return ResponseEntity.ok().headers(headers).body(responseBody);
    }

    @PostMapping("/logout")
    @CrossOrigin(origins = "*")
    public ResponseEntity<Map<String, Object>> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletResponse response) {

        Map<String, Object> responseBody = new HashMap<>();
        String token = null;

        try {
            // 从Authorization头获取token
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
                logger.debug("Received token from Authorization header");
            } else {
                logger.warn("No Authorization header found or invalid format");
            }

            if (token == null || token.trim().isEmpty()) {
                responseBody.put("success", false);
                responseBody.put("message", "Token is required");
                logger.warn("Logout failed: token is required");
                return ResponseEntity.badRequest().body(responseBody);
            }

            logger.debug("Validating token format");
            if (!tokenUtil.validateToken(token)) {
                responseBody.put("success", false);
                responseBody.put("message", "Invalid token format");
                logger.warn("Logout failed: invalid token format");
                return ResponseEntity.badRequest().body(responseBody);
            }

            logger.debug("Checking token in Redis");
            if (redisService.validateToken(token)) {
                String username = redisService.getUsernameByToken(token);
                redisService.deleteToken(token);

                // 清除Cookie
                Cookie tokenCookie = new Cookie("token", "");
                tokenCookie.setHttpOnly(true);
                tokenCookie.setSecure(false);
                tokenCookie.setPath("/");
                tokenCookie.setMaxAge(0);
                response.addCookie(tokenCookie);

                responseBody.put("success", true);
                responseBody.put("message", "Logout successful");
                logger.info("User {} logged out successfully", username);
            } else {
                responseBody.put("success", false);
                responseBody.put("message", "Invalid token or already logged out");
                logger.warn("Logout failed: invalid token or token not found in Redis");

                // 即使token无效也清除cookie
                Cookie tokenCookie = new Cookie("token", "");
                tokenCookie.setHttpOnly(true);
                tokenCookie.setSecure(false);
                tokenCookie.setPath("/");
                tokenCookie.setMaxAge(0);
                response.addCookie(tokenCookie);
            }

            return ResponseEntity.ok().body(responseBody);

        } catch (Exception e) {
            logger.error("Error during logout process", e);
            responseBody.put("success", false);
            responseBody.put("message", "Internal server error during logout");
            return ResponseEntity.status(500).body(responseBody);
        }
    }

    @PostMapping("/validate-token")
    @CrossOrigin(origins = "*")
    public ResponseEntity<Map<String, Object>> validateToken(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        Map<String, Object> responseBody = new HashMap<>();
        String token = null;

        try {
            //从Authorization头获取token
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
                logger.debug("Validating token from Authorization header");
            } else {
                responseBody.put("valid", false);
                responseBody.put("message", "No token provided");
                logger.warn("Token validation failed: no token provided");
                return ResponseEntity.ok().body(responseBody);
            }

            if (token == null || token.trim().isEmpty()) {
                responseBody.put("valid", false);
                responseBody.put("message", "Token is empty");
                return ResponseEntity.ok().body(responseBody);
            }

            //验证token格式
            if (!tokenUtil.validateToken(token)) {
                responseBody.put("valid", false);
                responseBody.put("message", "Invalid token format");
                logger.debug("Token validation failed: invalid format");
                return ResponseEntity.ok().body(responseBody);
            }

            //检查token是否过期
            if (tokenUtil.isTokenExpired(token)) {
                responseBody.put("valid", false);
                responseBody.put("message", "Token expired");
                responseBody.put("expired", true);
                logger.debug("Token validation failed: token expired");

                //自动清理过期的token
                redisService.deleteToken(token);
                return ResponseEntity.ok().body(responseBody);
            }

            //验证Redis中的存在性
            if (!redisService.validateToken(token)) {
                responseBody.put("valid", false);
                responseBody.put("message", "Token not found in storage");
                logger.debug("Token validation failed: not found in Redis");
                return ResponseEntity.ok().body(responseBody);
            }

            //token有效
            String username = redisService.getUsernameByToken(token);
            responseBody.put("valid", true);
            responseBody.put("message", "Token is valid");
            responseBody.put("username", username);
            responseBody.put("expired", false);

            //返回token剩余有效时间
            Long tokenTimestamp = tokenUtil.getTokenTimestamp(token);
            if (tokenTimestamp != null) {
                long currentTime = System.currentTimeMillis();
                long tokenAge = currentTime - tokenTimestamp;
                long remainingTime = (3600 * 1000L) - tokenAge; // 1小时减去已过时间
                responseBody.put("remainingTime", Math.max(0, remainingTime / 1000)); // 转换为秒
            }

            logger.debug("Token validation successful for user: {}", username);
            return ResponseEntity.ok().body(responseBody);

        } catch (Exception e) {
            logger.error("Error during token validation", e);
            responseBody.put("valid", false);
            responseBody.put("message", "Error validating token");
            return ResponseEntity.status(500).body(responseBody);
        }
    }
}