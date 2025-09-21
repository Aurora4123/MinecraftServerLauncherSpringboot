package fun.aurora.mcserverlauncher.configs;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "yggdrasil")
public class YggdrasilConfig {
    private String apiUrl;
    private String execEndPoint;
}

