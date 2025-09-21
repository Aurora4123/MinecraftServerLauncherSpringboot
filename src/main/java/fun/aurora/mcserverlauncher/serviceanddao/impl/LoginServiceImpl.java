package fun.aurora.mcserverlauncher.serviceanddao.impl;

import fun.aurora.mcserverlauncher.configs.YggdrasilConfig;
import fun.aurora.mcserverlauncher.entity.UserInfoVO;
import fun.aurora.mcserverlauncher.entity.YggdrasilUserAgent;
import fun.aurora.mcserverlauncher.entity.YggdrasilUserInfo;
import fun.aurora.mcserverlauncher.serviceanddao.LoginService;
import fun.aurora.mcserverlauncher.utils.HttpUtil;
import fun.aurora.mcserverlauncher.utils.UserInfoUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;
@Service
public class LoginServiceImpl implements LoginService {

    @Resource
    private YggdrasilConfig yggdrasilConfig;

    @Override
    public Map<String, Object> login(Map<String, String> userInfo) throws IOException, InterruptedException {
        YggdrasilUserInfo info = UserInfoUtil.parseUserInfo(new UserInfoVO(userInfo.get("username"), userInfo.get("password")), false);
        return HttpUtil.yggdrasilAuthenticate(yggdrasilConfig.getApiUrl(), info);
    }
}
