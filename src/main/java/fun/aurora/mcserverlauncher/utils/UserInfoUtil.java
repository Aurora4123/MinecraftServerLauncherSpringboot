package fun.aurora.mcserverlauncher.utils;

import fun.aurora.mcserverlauncher.entity.UserInfoVO;
import fun.aurora.mcserverlauncher.entity.YggdrasilUserAgent;
import fun.aurora.mcserverlauncher.entity.YggdrasilUserInfo;

public class UserInfoUtil {
    public static YggdrasilUserInfo parseUserInfo(UserInfoVO userInfo, boolean requestUser, String version, String agentName) {
        YggdrasilUserAgent agent = new YggdrasilUserAgent();
        agent.setName(agentName);
        agent.setVersion(version);
        YggdrasilUserInfo yggdrasilUserInfo = new YggdrasilUserInfo();
        yggdrasilUserInfo.setUsername(userInfo.getUsername());
        yggdrasilUserInfo.setPassword(userInfo.getPassword());
        yggdrasilUserInfo.setRequestUser(requestUser);
        yggdrasilUserInfo.setAgent(agent);
        return yggdrasilUserInfo;
    }

    public static YggdrasilUserInfo parseUserInfo(UserInfoVO userInfo, boolean requestUser, String version, String agentName, String clientToken) {
        YggdrasilUserInfo info = parseUserInfo(userInfo, requestUser, version, agentName);
        info.setClientToken(clientToken);
        return info;
    }

    public static YggdrasilUserInfo parseUserInfo(UserInfoVO userInfo, boolean requestUser) {
        return parseUserInfo(userInfo, requestUser, "1.20.1", "Minecraft");
    }
    public static YggdrasilUserInfo parseUserInfoWithClientToken(UserInfoVO userInfo, boolean requestUser, String clientToken) {
        YggdrasilUserInfo info = parseUserInfo(userInfo, requestUser, "1.20.1", "Minecraft");
        info.setClientToken(clientToken);
        return info;
    }
}
