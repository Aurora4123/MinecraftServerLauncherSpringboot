package fun.aurora.mcserverlauncher.controller;

import fun.aurora.mcserverlauncher.serviceanddao.LoginService;
import fun.aurora.mcserverlauncher.serviceanddao.RedisService;
import fun.aurora.mcserverlauncher.utils.TokenUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
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
    public ResponseEntity<Map<String, Object>> logout(@RequestBody Map<String, String> requestData, HttpServletResponse response) {
        String token = requestData.get("token");
        Map<String, Object> responseBody = new HashMap<>();

        if (token != null && redisService.validateToken(token)) {
            redisService.deleteToken(token);

            //清除Cookie
            Cookie tokenCookie = new Cookie("token", "");
            tokenCookie.setHttpOnly(true);
            tokenCookie.setSecure(false);
            tokenCookie.setPath("/");
            tokenCookie.setMaxAge(0);
            response.addCookie(tokenCookie);

            responseBody.put("success", true);
            responseBody.put("message", "Logout successful");
        } else {
            responseBody.put("success", false);
            responseBody.put("message", "Invalid token");
        }

        return ResponseEntity.ok().body(responseBody);
    }
}
