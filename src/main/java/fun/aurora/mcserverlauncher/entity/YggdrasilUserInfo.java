package fun.aurora.mcserverlauncher.entity;

import lombok.Data;
import org.springframework.stereotype.Repository;


@Data
public class YggdrasilUserInfo {
    private String username;
    private String password;
    private String clientToken;
    private boolean requestUser;
    private YggdrasilUserAgent agent;
}
