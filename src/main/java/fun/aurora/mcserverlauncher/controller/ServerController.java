package fun.aurora.mcserverlauncher.controller;

import fun.aurora.mcserverlauncher.serviceanddao.LoginService;
import jakarta.annotation.Resource;
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
    
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> userInfo) {
        logger.info("Received login request with username: {}", userInfo.get("username"));
        Map<String, Object> response = new HashMap<>();
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
            
            // 将结果中的状态码放入响应中，但始终返回HTTP 200
            response.put("httpStatus", 200);
            
            // 将body和status添加到响应中，但不包含headers
            response.put("body", result.get("body"));
            response.put("status", result.get("status"));
            
        } catch (Exception e) {
            logger.error("Error occurred during login process for user: {}", userInfo.get("username"), e);
            // 出现异常时也返回HTTP 200，但包含错误信息
            response.put("httpStatus", 500);
            response.put("error", e.getMessage());
        }
        
        logger.info("Login request completed with httpStatus: {}", response.get("httpStatus"));
        return ResponseEntity.ok().headers(headers).body(response);
    }
}
