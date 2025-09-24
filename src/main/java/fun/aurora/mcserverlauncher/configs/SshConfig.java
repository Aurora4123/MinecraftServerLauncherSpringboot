package fun.aurora.mcserverlauncher.configs;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "ssh")
public class SshConfig {
    private int connectTimeout = 10000;
    private int commandTimeout = 30000;
    private int executeDelay = 1000; //命令执行间隔毫秒

    private Map<String, SshHost> hosts = new HashMap<>();

    @Setter
    @Getter
    public static class SshHost {
        private String address;
        private int port = 22;
        private String username;
        private String password;
        private String privateKeyPath; //私钥路径
    }

    //根据主机名获取Ssh配置
    public SshHost getHostConfig(String hostname) {
        return hosts.get(hostname);
    }

    //验证主机配置是否存在O.o
    public boolean hasHostConfig(String hostname) {
        return hosts.containsKey(hostname) && hosts.get(hostname) != null;
    }
}