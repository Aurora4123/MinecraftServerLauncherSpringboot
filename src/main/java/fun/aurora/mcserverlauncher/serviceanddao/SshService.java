package fun.aurora.mcserverlauncher.serviceanddao;

import java.util.List;
import java.util.Map;

public interface SshService {
    boolean checkPort(String hostPort);
    void cleanup();
    void disconnectAll();
    boolean executeCommands(Map<String, List<String>> commandsByHost);
    Integer pingServer(String ip);
}
