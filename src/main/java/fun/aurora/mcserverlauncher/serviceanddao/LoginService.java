package fun.aurora.mcserverlauncher.serviceanddao;

import java.util.Map;

public interface LoginService {
    Map<String, Object> login(Map<String, String> userInfo) throws Exception;
}
