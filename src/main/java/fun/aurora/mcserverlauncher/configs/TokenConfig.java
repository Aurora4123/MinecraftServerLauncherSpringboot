package fun.aurora.mcserverlauncher.configs;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "token")
public class TokenConfig {
    private int expireTime = 3600; //给您最疼爱的token设置过期时间，单位秒
    private String secretKey = "default-secret-key";
}