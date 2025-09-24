package fun.aurora.mcserverlauncher.serviceanddao;

import fun.aurora.mcserverlauncher.configs.TaskConfig;
import jakarta.annotation.Resource;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class TaskService {

    @Resource
    private TaskConfig taskConfig;

    @Resource
    private SshService sshService;

    @Resource
    private RedisService redisService;

    //存储任务状态和剩余时间
    private final Map<String, TaskStatus> taskStatusMap = new ConcurrentHashMap<>();

    //任务状态类
    @Setter
    @Getter
    public static class TaskStatus {
        private String name;
        private String status; // running, stopped, starting, stopping, error
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String eta;
        private String lastError;
        private boolean autoStopped = false;
    }

    //获取所有任务状态
    public Map<String, Object> getTasksStatus() {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> tasks = new ArrayList<>();
        List<Map<String, Object>> servers = new ArrayList<>();

        //获取任务状态
        if (taskConfig.getTasks() != null) {
            taskConfig.getTasks().forEach((name, task) -> {
                Map<String, Object> taskInfo = new HashMap<>();
                taskInfo.put("id", task.getId() != null ? task.getId().toString() : "0");
                taskInfo.put("name", name);

                //获取或创建任务状态
                TaskStatus status = taskStatusMap.computeIfAbsent(name, k -> {
                    TaskStatus newStatus = new TaskStatus();
                    newStatus.setName(name);
                    newStatus.setStatus("unknown");
                    newStatus.setEta("NULL");
                    return newStatus;
                });
                //如果状态未知
                if ("unknown".equals(status.getStatus())) {
                    updateTaskStatusFromSystem(name, task, status);
                }

                taskInfo.put("status", status.getStatus());
                taskInfo.put("ETA", status.getEta());
                //调试信息
                if (status.getStartTime() != null) {
                    taskInfo.put("startTime", status.getStartTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                }
                if (status.getLastError() != null) {
                    taskInfo.put("lastError", status.getLastError());
                }

                tasks.add(taskInfo);
            });
        } else {
            log.warn("No tasks configured in taskConfig");
        }

        //获取服务器ping状态
        if (taskConfig.getPing() != null) {
            taskConfig.getPing().forEach((name, server) -> {
                Map<String, Object> serverInfo = new HashMap<>();
                serverInfo.put("name", name);

                try {
                    Integer ping = sshService.pingServer(server.getIp());
                    serverInfo.put("ping", ping != null ? ping : "timeout");
                } catch (Exception e) {
                    log.error("Error pinging server {}: {}", name, e.getMessage());
                    serverInfo.put("ping", "error");
                }

                servers.add(serverInfo);
            });
        } else {
            log.warn("No servers configured for ping");
        }

        result.put("tasks", tasks);
        result.put("servers", servers);

        log.debug("Returning status for {} tasks and {} servers", tasks.size(), servers.size());
        return result;
    }

    //启动任务
    public Map<String, Object> startTask(String taskName, Integer timeOption) {
        Map<String, Object> response = new HashMap<>();

        try {
            TaskConfig.Task task = taskConfig.getTasks().get(taskName);
            if (task == null) {
                response.put("success", false);
                response.put("message", "Task not found:" + taskName);
                log.error("Task not found: {}", taskName);
                return response;
            }

            //验证时间选项
            if (timeOption == null || !taskConfig.isValidTimeOption(timeOption)) {
                response.put("success", false);
                response.put("message", "Invalid time option!");
                response.put("validOptions", taskConfig.getTimeOptions());
                log.warn("Invalid time option: {} for task: {}", timeOption, taskName);
                return response;
            }

            //根据选项id获取实际小时数
            Integer timeHours = taskConfig.getHoursByOption(timeOption);
            if (timeHours == null) {
                response.put("success", false);
                response.put("message", "time option error!");
                log.error("Time option mapping error for option: {}", timeOption);
                return response;
            }

            //检查任务是否有时间限制
            if (task.getMaxTime() != null && timeHours > task.getMaxTime()) {
                response.put("success", false);
                response.put("message", "Time option is too big!: " + task.getMaxTime() + "hour");
                log.warn("Time option {} hours exceeds max limit {} for task: {}",
                        timeHours, task.getMaxTime(), taskName);
                return response;
            }

            //检查任务是否允许该时间选项
            if (task.getAllowedTimes() != null && !task.getAllowedTimes().isEmpty()) {
                if (!task.getAllowedTimes().contains(timeOption)) {
                    response.put("success", false);
                    response.put("message", "This task not allow this time option🐱!");
                    response.put("allowedOptions", task.getAllowedTimes());
                    log.warn("Time option {} not allowed for task: {}", timeOption, taskName);
                    return response;
                }
            }

            //验证ssh主机配置
            if (task.getCommand() != null) {
                for (String hostname : task.getCommand().keySet()) {
                    //这里需要检查SSH配置中是否有该主机
                    if (task.getCommand().get(hostname) == null) {
                        response.put("success", false);
                        response.put("message", "command setting error for host: " + hostname);
                        return response;
                    }
                }
            }

            //设置任务状态
            TaskStatus status = new TaskStatus();
            status.setName(taskName);
            status.setStatus("starting");
            status.setStartTime(LocalDateTime.now());
            status.setEndTime(LocalDateTime.now().plusHours(timeHours));
            status.setEta(calculateETA(status.getEndTime()));
            status.setLastError(null);
            taskStatusMap.put(taskName, status);

            log.info("Starting task {} with time option {} ({} hours)", taskName, timeOption, timeHours);

            //执行多机命令
            boolean success = false;
            String errorMessage = null;

            try {
                success = sshService.executeCommands(task.getCommand());
            } catch (Exception e) {
                errorMessage = e.getMessage();
                log.error("Error executing commands for task {}: {}", taskName, errorMessage);
            }

            if (success) {
                status.setStatus("running");

                //存储任务结束时间到redis
                String redisKey = "task:" + taskName + ":endtime";
                redisService.storeValue(redisKey, status.getEndTime().toString(), timeHours * 3600);

                //存储任务信息到redis
                String taskInfoKey = "task:" + taskName + ":info";
                Map<String, String> taskInfo = new HashMap<>();
                taskInfo.put("host", "multiple"); //多机任务
                taskInfo.put("startTime", status.getStartTime().toString());
                taskInfo.put("scheduledEndTime", status.getEndTime().toString());
                taskInfo.put("timeOption", timeOption.toString());
                taskInfo.put("timeHours", timeHours.toString());
                redisService.storeValue(taskInfoKey, taskInfo.toString(), timeHours * 3600);

                response.put("success", true);
                response.put("message", "task start succeed");
                response.put("eta", status.getEta());
                response.put("scheduledEndTime", status.getEndTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                response.put("timeOption", timeOption);
                response.put("timeHours", timeHours);

                log.info("Task {} started successfully with option {} ({} hours), ETA: {}",
                        taskName, timeOption, timeHours, status.getEta());

            } else {
                status.setStatus("error");
                status.setEta("NULL");
                status.setLastError(errorMessage != null ? errorMessage : "task launch fail");

                response.put("success", false);
                response.put("message", "task launch fail: " + status.getLastError());
                log.error("Failed to start task {}: {}", taskName, status.getLastError());
            }

        } catch (Exception e) {
            log.error("Unexpected error starting task {}: {}", taskName, e.getMessage(), e);

            response.put("success", false);
            response.put("message", "system error: " + e.getMessage());

            //更新任务状态为错误
            TaskStatus status = taskStatusMap.get(taskName);
            if (status != null) {
                status.setStatus("error");
                status.setLastError(e.getMessage());
            }
        }

        return response;
    }

    //停止任务
    public Map<String, Object> stopTask(String taskName) {
        Map<String, Object> response = new HashMap<>();

        try {
            TaskConfig.Task task = taskConfig.getTasks().get(taskName);
            if (task == null) {
                response.put("success", false);
                response.put("message", "task 404:( : " + taskName);
                log.error("Task not found: {}", taskName);
                return response;
            }

            //更新任务状态为stopped
            TaskStatus status = taskStatusMap.get(taskName);
            if (status == null) {
                status = new TaskStatus();
                status.setName(taskName);
                taskStatusMap.put(taskName, status);
            }
            status.setStatus("stopping");

            log.info("Stopping task: {}", taskName);

            //执行停止命令
            boolean success = false;
            String errorMessage = null;

            try {
                Map<String, List<String>> shutdownCommands = task.getShutdownCommand();
                /*
                if (shutdownCommands == null || shutdownCommands.isEmpty()) {
                    //如果没有配置停止命令，尝试使用通用停止命令
                    shutdownCommands = createDefaultShutdownCommands(task);
                }
                 */

                success = sshService.executeCommands(shutdownCommands);
            } catch (Exception e) {
                errorMessage = e.getMessage();
                log.error("Error stopping task {}: {}", taskName, errorMessage);
            }

            if (success) {
                status.setStatus("stopped");
                status.setEta("NULL");
                status.setAutoStopped(false);

                //从redis删除任务信息
                String redisKey = "task:" + taskName + ":endtime";
                redisService.deleteValue(redisKey);

                String taskInfoKey = "task:" + taskName + ":info";
                redisService.deleteValue(taskInfoKey);

                response.put("success", true);
                response.put("message", "task stopped succeed!");

                log.info("Task {} stopped successfully", taskName);

            } else {
                status.setStatus("error");
                status.setLastError(errorMessage != null ? errorMessage : "Stop task execute fail!");

                response.put("success", false);
                response.put("message", "Failed to stop task: " + status.getLastError());
                log.error("Failed to stop task {}: {}", taskName, status.getLastError());
            }

        } catch (Exception e) {
            log.error("Unexpected error stopping task {}: {}", taskName, e.getMessage(), e);

            response.put("success", false);
            response.put("message", "Unexpected system error : " + e.getMessage());
        }

        return response;
    }

    //创建默认停止命令
    private Map<String, List<String>> createDefaultShutdownCommands(TaskConfig.Task task) {
        Map<String, List<String>> commands = new HashMap<>();

        // 为每个配置了启动命令的主机创建停止命令
        if (task.getCommand() != null) {
            for (String hostname : task.getCommand().keySet()) {
                List<String> stopCommands = new ArrayList<>();
                stopCommands.add("pkill -f " + task.getCommand().get(hostname).toString());
                stopCommands.add("sleep 2");
                commands.put(hostname, stopCommands);
            }
        }

        return commands;
    }

    //强制停止任务
    /*
    public Map<String, Object> forceStopTask(String taskName) {
        Map<String, Object> response = new HashMap<>();

        try {
            TaskConfig.Task task = taskConfig.getTasks().get(taskName);
            if (task == null) {
                response.put("success", false);
                response.put("message", "task 404: " + taskName);
                return response;
            }

            //使用强制停止命令
            Map<String, List<String>> forceStopCommands = createForceStopCommands(task);

            TaskStatus status = taskStatusMap.get(taskName);
            if (status == null) {
                status = new TaskStatus();
                status.setName(taskName);
                taskStatusMap.put(taskName, status);
            }
            status.setStatus("stopping");

            boolean success = sshService.executeCommands(forceStopCommands);

            if (success) {
                status.setStatus("stopped");
                status.setEta("NULL");

                //清理redis
                redisService.deleteValue("task:" + taskName + ":endtime");
                redisService.deleteValue("task:" + taskName + ":info");

                response.put("success", true);
                response.put("message", "task kill ok!");

                log.warn("Task {} was force stopped", taskName);
            } else {
                response.put("success", false);
                response.put("message", "byd task kill fail");
            }

        } catch (Exception e) {
            log.error("Error force stopping task {}: {}", taskName, e.getMessage());
            response.put("success", false);
            response.put("message", "error: " + e.getMessage());
        }

        return response;
    }

     */

    //创建强制停止命令
    /*
    private Map<String, List<String>> createForceStopCommands(TaskConfig.Task task) {
        Map<String, List<String>> commands = new HashMap<>();

        if (task.getCommand() != null) {
            for (String hostname : task.getCommand().keySet()) {
                List<String> forceCommands = Arrays.asList(
                        "pkill -9 -f " + task.getCommand().get(hostname).toString(),
                        "killall -9 java", //java被针对了捏
                        "sleep 3"
                );
                commands.put(hostname, forceCommands);
            }
        }

        return commands;
    }

     */

    //从系统状态更新任务状态
    private void updateTaskStatusFromSystem(String taskName, TaskConfig.Task task, TaskStatus status) {
        try {
            boolean isRunning = sshService.checkPort(task.getTelnet());

            if (isRunning) {
                status.setStatus("running");

                // 检查Redis中是否有计划结束时间
                String redisKey = "task:" + taskName + ":endtime";
                String endTimeStr = redisService.getValue(redisKey);

                if (endTimeStr != null) {
                    try {
                        LocalDateTime endTime = LocalDateTime.parse(endTimeStr);
                        status.setEndTime(endTime);
                        status.setEta(calculateETA(endTime));
                    } catch (Exception e) {
                        log.warn("Error parsing end time for task {}: {}", taskName, e.getMessage());
                        status.setEta("NULL");
                    }
                } else {
                    status.setEta("unknown");
                }
            } else {
                status.setStatus("stopped");
                status.setEta("NULL");
            }

        } catch (Exception e) {
            log.error("Error checking status for task {}: {}", taskName, e.getMessage());
            status.setStatus("error");
            status.setLastError(e.getMessage());
        }
    }

    //计算eta
    private String calculateETA(LocalDateTime endTime) {
        if (endTime == null) {
            return "NULL";
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(endTime)) {
            return "00:00";
        }

        long seconds = java.time.Duration.between(now, endTime).getSeconds();
        if (seconds <= 0) {
            return "00:00";
        }

        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;

        return String.format("%02d:%02d", hours, minutes);
    }

    //从telnet配置中提取主机
    /*
    private String extractHostFromTelnet(String telnet) {
        if (telnet == null || telnet.trim().isEmpty()) {
            return null;
        }

        String[] parts = telnet.split(":");
        if (parts.length != 2) {
            log.error("Invalid telnet format: {}", telnet);
            return null;
        }

        return parts[0];
    }

     */

    //定时检查任务状态和ETA
    @Scheduled(fixedRate = 60000)
    public void scheduledStatusUpdate() {
        if (taskConfig.getTasks() == null || taskConfig.getTasks().isEmpty()) {
            return;
        }

        log.debug("Scheduled task status update started");

        taskConfig.getTasks().forEach((name, task) -> {
            try {
                TaskStatus status = taskStatusMap.get(name);
                if (status == null) {
                    status = new TaskStatus();
                    status.setName(name);
                    taskStatusMap.put(name, status);
                }

                //检查端口状态
                boolean isRunning = sshService.checkPort(task.getTelnet());

                //更新状态
                if ("running".equals(status.getStatus())) {
                    if (!isRunning) {
                        status.setStatus("stopped");
                        status.setEta("NULL");
                        status.setAutoStopped(true);
                        log.warn("Task {} stopped unexpectedly", name);
                    } else if (status.getEndTime() != null) {
                        //更新eta
                        status.setEta(calculateETA(status.getEndTime()));

                        //检查是否应该自动停止
                        if (LocalDateTime.now().isAfter(status.getEndTime())) {
                            log.info("Task {} has reached its time limit, auto-stopping...", name);
                            stopTask(name);
                            status.setAutoStopped(true);
                        }
                    }
                } else if (("stopped".equals(status.getStatus()) || "error".equals(status.getStatus())) && isRunning) {
                    status.setStatus("running");
                    log.info("Task {} is now running (detected by port check)", name);
                }

            } catch (Exception e) {
                log.error("Error in scheduled update for task {}: {}", name, e.getMessage());
            }
        });

        log.debug("Scheduled task status update completed");
    }

    //系统启动时恢复任务状态
    /*
    @jakarta.annotation.PostConstruct
    public void restoreTaskStatus() {
        log.info("Restoring task status from system...");

        if (taskConfig.getTasks() == null || taskConfig.getTasks().isEmpty()) {
            log.warn("No tasks configured, skipping status restoration");
            return;
        }

        taskConfig.getTasks().forEach((name, task) -> {
            try {
                //检查redis中是否有未完成的任务
                String redisKey = "task:" + name + ":endtime";
                String endTimeStr = redisService.getValue(redisKey);

                TaskStatus status = new TaskStatus();
                status.setName(name);

                //检查任务是否还在运行
                boolean isRunning = sshService.checkPort(task.getTelnet());

                if (isRunning && endTimeStr != null) {
                    try {
                        LocalDateTime endTime = LocalDateTime.parse(endTimeStr);
                        status.setStatus("running");
                        status.setEndTime(endTime);
                        status.setEta(calculateETA(endTime));
                        log.info("Restored running task {} with ETA: {}", name, status.getEta());
                    } catch (Exception e) {
                        log.warn("Error parsing end time for task {}, marking as running without ETA", name);
                        status.setStatus("running");
                        status.setEta("unknown");
                    }
                } else if (isRunning) {
                    status.setStatus("running");
                    status.setEta("unknown");
                    log.info("Restored running task {} without scheduled end time", name);
                } else {
                    status.setStatus("stopped");
                    status.setEta("NULL");
                    log.info("Restored stopped task {}", name);
                }

                taskStatusMap.put(name, status);

            } catch (Exception e) {
                log.error("Error restoring task {} status: {}", name, e.getMessage());
            }
        });

        log.info("Task status restoration completed");
    }

     */

    //用这个来重启任务
    /*
    public Map<String, Object> restartTask(String taskName, Integer timeOption) {
        Map<String, Object> response = new HashMap<>();

        try {
            TaskConfig.Task task = taskConfig.getTasks().get(taskName);
            if (task == null) {
                response.put("success", false);
                response.put("message", "task 404: " + taskName);
                log.error("Task not found for restart: {}", taskName);
                return response;
            }

            //验证时间选项
            if (timeOption == null || !taskConfig.isValidTimeOption(timeOption)) {
                response.put("success", false);
                response.put("message", "Invalid time option for restart");
                log.warn("Invalid time option for restart: {} for task: {}", timeOption, taskName);
                return response;
            }

            //根据选项id获取实际小时数
            Integer timeHours = taskConfig.getHoursByOption(timeOption);
            if (timeHours == null) {
                response.put("success", false);
                response.put("message", "Time option mapping error for restart option");
                log.error("Time option mapping error for restart option: {}", timeOption);
                return response;
            }

            log.info("Restarting task {} with time option {} ({} hours)", taskName, timeOption, timeHours);

            //先停止任务
            Map<String, Object> stopResult = stopTask(taskName);
            if (!Boolean.TRUE.equals(stopResult.get("success"))) {
                //如果停止失败，尝试强制停止
                log.warn("Normal stop failed, trying force stop for task: {}", taskName);
                stopResult = forceStopTask(taskName);

                if (!Boolean.TRUE.equals(stopResult.get("success"))) {
                    response.put("success", false);
                    response.put("message", "can't stop running task!: " + stopResult.get("message"));
                    return response;
                }
            }

            //等一段时间让进程完全停止
            try {
                log.debug("Waiting for processes to fully stop...");
                Thread.sleep(5000); //等待5秒
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                response.put("success", false);
                response.put("message", "restart has boooom");
                return response;
            }

            //启动任务
            Map<String, Object> startResult = startTask(taskName, timeOption);

            if (Boolean.TRUE.equals(startResult.get("success"))) {
                response.put("success", true);
                response.put("message", "task restart succeed");
                response.put("timeOption", timeOption);
                response.put("timeHours", timeHours);
                response.put("eta", startResult.get("eta"));
                response.put("scheduledEndTime", startResult.get("scheduledEndTime"));

                log.info("Task {} restarted successfully with option {} ({} hours)",
                        taskName, timeOption, timeHours);
            } else {
                response.put("success", false);
                response.put("message", "restart fail: " + startResult.get("message"));
                log.error("Failed to restart task {}: {}", taskName, startResult.get("message"));
            }

        } catch (Exception e) {
            log.error("Unexpected error restarting task {}: {}", taskName, e.getMessage(), e);
            response.put("success", false);
            response.put("message", "an error in restarting task: " + e.getMessage());
        }

        return response;
    }

     */

    //获取单个任务的详细信息
    public Map<String, Object> getTaskDetails(String taskName) {
        Map<String, Object> details = new HashMap<>();

        try {
            TaskConfig.Task task = taskConfig.getTasks().get(taskName);
            if (task == null) {
                details.put("error", "Task not found");
                return details;
            }

            TaskStatus status = taskStatusMap.get(taskName);
            if (status == null) {
                status = new TaskStatus();
                status.setName(taskName);
                status.setStatus("unknown");
                taskStatusMap.put(taskName, status);
            }

            details.put("name", taskName);
            details.put("id", task.getId());
            details.put("telnet", task.getTelnet());
            details.put("status", status.getStatus());
            details.put("eta", status.getEta());
            details.put("startTime", status.getStartTime());
            details.put("endTime", status.getEndTime());
            details.put("lastError", status.getLastError());
            details.put("autoStopped", status.isAutoStopped());

            details.put("commandCount", task.getCommand() != null ? task.getCommand().size() : 0);
            details.put("shutdownCommandCount", task.getShutdownCommand() != null ? task.getShutdownCommand().size() : 0);

        } catch (Exception e) {
            log.error("Error getting task details for {}: {}", taskName, e.getMessage());
            details.put("error", e.getMessage());
        }

        return details;
    }
}