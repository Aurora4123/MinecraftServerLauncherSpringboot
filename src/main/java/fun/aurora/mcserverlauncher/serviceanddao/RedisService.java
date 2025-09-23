package fun.aurora.mcserverlauncher.serviceanddao;

public interface RedisService {
    
    //存储token
    void storeToken(String token, String username, long expireTime);

    //验证token真假
    boolean validateToken(String token);

    //查询token对应的user
    String getUsernameByToken(String token);

    //删除token
    void deleteToken(String token);

    //刷新token
    void refreshToken(String token, long expireTime);
}
