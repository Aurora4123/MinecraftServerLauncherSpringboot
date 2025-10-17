package fun.aurora.mcserverlauncher.serviceanddao;

import java.util.Map;

public interface TaskService {
    Map<String, Object> getTasksStatus();
    Map<String, Object> startTask(String taskName, Integer timeOption);
    Map<String, Object> stopTask(String taskName);
    Map<String, Object> getTaskDetails(String taskName);
}
