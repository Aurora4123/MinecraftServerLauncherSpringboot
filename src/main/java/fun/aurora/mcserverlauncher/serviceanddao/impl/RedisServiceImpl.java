package fun.aurora.mcserverlauncher.serviceanddao.impl;

import fun.aurora.mcserverlauncher.serviceanddao.RedisService;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class RedisServiceImpl implements RedisService {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    //存储token
    public void storeToken(String token, String username, long expireTime) {
        String key = "token:" + token;
        redisTemplate.opsForValue().set(key, username, expireTime, TimeUnit.SECONDS);

        //同时存储用户到token的映射
        String userKey = "user_token:" + username;
        redisTemplate.opsForValue().set(userKey, token, expireTime, TimeUnit.SECONDS);
    }

    //这token阿，不咸不淡，味道真是豪极了，不对用户好像在token里下了毒，炊逝员你这么辛苦你得先尝尝这token
    //验证token真假
    public boolean validateToken(String token) {
        String key = "token:" + token;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    //让我看看是哪位大可爱用户的哪个token
    //查询token对应的user
    public String getUsernameByToken(String token) {
        String key = "token:" + token;
        return (String) redisTemplate.opsForValue().get(key);
    }

    //你被开除了，哦，应该说你被优化了
    //删除token
    public void deleteToken(String token) {
        String key = "token:" + token;
        String username = getUsernameByToken(token);
        redisTemplate.delete(key);

        if (username != null) {
            String userKey = "user_token:" + username;
            redisTemplate.delete(userKey);
        }
    }

    //我认为写这段代码的人已经疯了一半了，建议刷新精神状态
    //刷新token
    public void refreshToken(String token, long expireTime) {
        String key = "token:" + token;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            String username = getUsernameByToken(token);
            if (username != null) {
                storeToken(token, username, expireTime);
            }
        }
    }
}