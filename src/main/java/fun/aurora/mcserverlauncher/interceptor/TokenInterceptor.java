package fun.aurora.mcserverlauncher.interceptor;

import fun.aurora.mcserverlauncher.serviceanddao.RedisService;
import fun.aurora.mcserverlauncher.utils.TokenUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class TokenInterceptor implements HandlerInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(TokenInterceptor.class);

    @Resource
    private TokenUtil tokenUtil;

    @Resource
    private RedisService redisService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 处理OPTIONS预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return true;
        }

        // 放行登录接口
        if ("/login".equals(request.getRequestURI()) && "POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 检查token
        String token = getTokenFromRequest(request);
        if (token == null) {
            logger.warn("Access denied: No token provided for request: {}", request.getRequestURI());
            response.setStatus(403);
            response.getWriter().write("{\"error\": \"Access denied: No token provided\"}");
            return false;
        }

        // 验证token格式和在不在redis
        if (!tokenUtil.validateToken(token) || !redisService.validateToken(token)) {
            logger.warn("Access denied: Invalid token for request: {}", request.getRequestURI());
            response.setStatus(403);
            response.getWriter().write("{\"error\": \"Access denied: Invalid token\"}");
            return false;
        }

        logger.debug("Token validation successful for user: {}", redisService.getUsernameByToken(token));
        return true;
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        //从cookie中获取
        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (jakarta.servlet.http.Cookie cookie : cookies) {
                if ("token".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }

        //从header中获取
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }

        //从参数中获取
        return request.getParameter("token");
    }
}