package fun.aurora.mcserverlauncher.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserInfoVO {
    private String username;
    private String password;
}
