package fun.aurora.mcserverlauncher.utils;

import fun.aurora.mcserverlauncher.entity.YggdrasilUserInfo;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpUtil {

    private static final Logger logger = LoggerFactory.getLogger(HttpUtil.class);
    
    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 发送GET请求
     *
     * @param url 请求URL
     * @return 响应结果Map，包含status、headers和body
     * @throws IOException          IO异常
     * @throws InterruptedException 中断异常
     */
    public static Map<String, Object> get(String url) throws IOException, InterruptedException {
        logger.info("Sending GET request to: {}", url);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "McServerLauncher/1.0")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        logger.info("Received response from {} with status code: {}", url, response.statusCode());
        return buildResponseMap(response);
    }

    /**
     * 发送POST请求
     *
     * @param url  请求URL
     * @param body 请求体
     * @return 响应结果Map，包含status、headers和body
     * @throws IOException          IO异常
     * @throws InterruptedException 中断异常
     */
    public static Map<String, Object> post(String url, String body) throws IOException, InterruptedException {
        logger.info("Sending POST request to: {}", url);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("User-Agent", "McServerLauncher/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        logger.info("Received response from {} with status code: {}", url, response.statusCode());
        return buildResponseMap(response);
    }

    /**
     * 发送Yggdrasil认证请求
     *
     * @param authServerUrl 认证服务器URL
     * @param userInfo      Yggdrasil用户信息
     * @return 响应结果Map，包含status、headers和body
     * @throws IOException          IO异常
     * @throws InterruptedException 中断异常
     */
    public static Map<String, Object> yggdrasilAuthenticate(String authServerUrl, YggdrasilUserInfo userInfo) throws IOException, InterruptedException {
        String url = authServerUrl + "/authserver/authenticate";
        logger.info("Sending Yggdrasil authentication request to: {}", url);
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("agent", userInfo.getAgent());
        requestBody.put("username", userInfo.getUsername());
        requestBody.put("password", userInfo.getPassword());
        requestBody.put("requestUser", userInfo.isRequestUser());
        
        if (userInfo.getClientToken() != null && !userInfo.getClientToken().isEmpty()) {
            requestBody.put("clientToken", userInfo.getClientToken());
        }
        
        String jsonBody = objectMapper.writeValueAsString(requestBody);
        logger.debug("Authentication request body: {}", jsonBody);
        Map<String, Object> result = post(url, jsonBody);
        logger.info("Yggdrasil authentication completed with status: {}", result.get("status"));
        return result;
    }

    /**
     * 发送Yggdrasil服务器加入请求
     *
     * @param authServerUrl 认证服务器URL
     * @param accessToken   访问令牌
     * @param selectedProfile 选中的用户档案
     * @param serverId      服务器ID
     * @return 响应结果Map，包含status、headers和body
     * @throws IOException          IO异常
     * @throws InterruptedException 中断异常
     */
    public static Map<String, Object> yggdrasilJoinServer(String authServerUrl, String accessToken, Map<String, Object> selectedProfile, String serverId) throws IOException, InterruptedException {
        String url = authServerUrl + "/sessionserver/session/minecraft/join";
        logger.info("Sending Yggdrasil join server request to: {}", url);
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("accessToken", accessToken);
        requestBody.put("selectedProfile", selectedProfile);
        requestBody.put("serverId", serverId);
        
        String jsonBody = objectMapper.writeValueAsString(requestBody);
        logger.debug("Join server request body: {}", jsonBody);
        Map<String, Object> result = post(url, jsonBody);
        logger.info("Yggdrasil join server completed with status: {}", result.get("status"));
        return result;
    }

    /**
     * 构建响应Map
     *
     * @param response HttpResponse对象
     * @return 包含状态码和解析后的响应体的Map
     */
    private static Map<String, Object> buildResponseMap(HttpResponse<String> response) throws IOException {
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("status", response.statusCode());
        
        // 解析JSON响应体
        String responseBody = response.body();
        try {
            // 尝试解析JSON
            Object parsedBody = objectMapper.readValue(responseBody, Object.class);
            resultMap.put("body", parsedBody);
            logger.debug("Response body parsed as JSON");
        } catch (Exception e) {
            // 如果不是有效的JSON，保持原样
            resultMap.put("body", responseBody);
            logger.debug("Response body is not valid JSON, keeping as string");
        }

        // 添加headers到结果中
        Map<String, List<String>> headers = new HashMap<>();
        response.headers().map().forEach(headers::put);
        resultMap.put("headers", headers);

        return resultMap;
    }
}
