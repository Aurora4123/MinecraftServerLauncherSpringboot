package fun.aurora.mcserverlauncher.serviceanddao.impl;

import com.jcraft.jsch.*;
import fun.aurora.mcserverlauncher.configs.SshConfig;
import fun.aurora.mcserverlauncher.serviceanddao.SshService;
import jakarta.annotation.Resource;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SshServiceImpl implements SshService {

    @Resource
    private SshConfig sshConfig;

    //SSH连接池
    private final ConcurrentHashMap<String, Session> sessionCache = new ConcurrentHashMap<>();

    //执行多主机命令
    public boolean executeCommands(Map<String, List<String>> commandsByHost) {
        if (commandsByHost == null || commandsByHost.isEmpty()) {
            log.warn("No commands to execute");
            return true; //空军等于成功
        }

        boolean allSuccess = true;

        for (Map.Entry<String, List<String>> entry : commandsByHost.entrySet()) {
            String hostname = entry.getKey();
            List<String> commands = entry.getValue();

            if (!executeCommandsOnHost(hostname, commands)) {
                allSuccess = false;
                log.error("Command execution failed on host: {}", hostname);
            }
        }

        return allSuccess;
    }

    //在特定主机上执行命令
    private boolean executeCommandsOnHost(String hostname, List<String> commands) {
        if (commands == null || commands.isEmpty()) {
            log.debug("No commands for host: {}", hostname);
            return true;
        }

        Session session = null;

        try {
            //获取主机配置
            SshConfig.SshHost hostConfig = sshConfig.getHostConfig(hostname);
            if (hostConfig == null) {
                log.error("SSH host configuration not found: {}", hostname);
                return false;
            }

            session = getSession(hostConfig);
            log.info("Executing {} commands on host: {} ({})", commands.size(), hostname, hostConfig.getAddress());

            for (int i = 0; i < commands.size(); i++) {
                String command = commands.get(i);
                log.debug("Executing command {}/{} on {}: {}", i + 1, commands.size(), hostname, command);

                if (!executeSingleCommand(session, command)) {
                    log.error("Command failed on {}: {}", hostname, command);
                    return false;
                }

                //命令执行间隔
                if (i < commands.size() - 1) {
                    int delay = sshConfig.getExecuteDelay();
                    if (delay > 0) {
                        log.debug("Waiting {}ms before next command on {}", delay, hostname);
                        Thread.sleep(delay);
                    }
                }
            }

            log.info("All commands executed successfully on host: {}", hostname);
            return true;

        } catch (Exception e) {
            log.error("Error executing commands on host {}: {}", hostname, e.getMessage());
            return false;
        }
        //ssh会话保持连接，方便重用，不在这关闭
    }

    //获取ssh会话
    private Session getSession(SshConfig.SshHost hostConfig) throws JSchException {
        String cacheKey = hostConfig.getAddress() + ":" + hostConfig.getPort();
        Session session = sessionCache.get(cacheKey);

        if (session != null && session.isConnected()) {
            return session;
        }

        JSch jsch = new JSch();
        session = jsch.getSession(hostConfig.getUsername(), hostConfig.getAddress(), hostConfig.getPort());

        //设置认证方式
        if (hostConfig.getPrivateKeyPath() != null && !hostConfig.getPrivateKeyPath().isEmpty()) {
            jsch.addIdentity(hostConfig.getPrivateKeyPath());
            session.setConfig("PreferredAuthentications", "publickey");
        } else if (hostConfig.getPassword() != null && !hostConfig.getPassword().isEmpty()) {
            session.setPassword(hostConfig.getPassword());
        } else {
            throw new JSchException("No authentication method configured for host: " + hostConfig.getAddress());
        }

        //配置ssh参数
        session.setConfig("StrictHostKeyChecking", "no");
        session.setConfig("ConnectTimeout", String.valueOf(sshConfig.getConnectTimeout()));

        session.connect(sshConfig.getConnectTimeout());
        sessionCache.put(cacheKey, session);

        log.info("SSH session established to: {}:{}", hostConfig.getAddress(), hostConfig.getPort());
        return session;
    }

    //执行单个命令
    private boolean executeSingleCommand(Session session, String command) {
        ChannelExec channel = null;

        try {
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);

            InputStream in = channel.getInputStream();
            InputStream err = channel.getErrStream();

            channel.connect(sshConfig.getConnectTimeout());

            //读取输出
            String output = readStream(in);
            String error = readStream(err);

            while (!channel.isClosed()) {
                Thread.sleep(100);
            }

            int exitStatus = channel.getExitStatus();

            if (exitStatus != 0) {
                log.error("Command failed with exit code {}: {}", exitStatus, command);
                if (!error.isEmpty()) {
                    log.error("Error output: {}", error);
                }
                return false;
            }

            if (!output.isEmpty()) {
                log.debug("Command output: {}", output);
            }

            return true;

        } catch (Exception e) {
            log.error("Command execution error: {} - {}", command, e.getMessage());
            return false;
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
        }
    }

    //读取流内容
    private String readStream(InputStream in) {
        try {
            StringBuilder output = new StringBuilder();
            byte[] buffer = new byte[1024];
            int bytesRead;

            while (in.available() > 0) {
                bytesRead = in.read(buffer, 0, Math.min(in.available(), buffer.length));
                if (bytesRead > 0) {
                    output.append(new String(buffer, 0, bytesRead));
                }
            }

            return output.toString().trim();
        } catch (Exception e) {
            log.debug("Error reading stream: {}", e.getMessage());
            return "";
        }
    }

    //检测端口是否开放
    public boolean checkPort(String hostPort) {
        try {
            String[] parts = hostPort.split(":");
            if (parts.length != 2) {
                log.error("Invalid host:port format: {}", hostPort);
                return false;
            }

            String host = parts[0];
            int port = Integer.parseInt(parts[1]);

            //暂时使用ssh的nc命令查询，后续考虑换用纯本地的telnet方案
            if (!sshConfig.getHosts().isEmpty()) {
                String firstHostname = sshConfig.getHosts().keySet().iterator().next();
                SshConfig.SshHost hostConfig = sshConfig.getHostConfig(firstHostname);

                if (hostConfig != null) {
                    String command = String.format("nc -z -w 5 %s %d", host, port);
                    return executeSingleCommand(getSession(hostConfig), command);
                }
            }

            log.warn("No SSH hosts configured, using local port check");
            return checkLocalPort(port);

        } catch (Exception e) {
            log.error("Error checking port {}: {}", hostPort, e.getMessage());
            return false;
        }
    }

    //本地端口检查
    private boolean checkLocalPort(int port) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command("nc", "-z", "localhost", String.valueOf(port));

            Process process = processBuilder.start();
            boolean finished = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);

            if (!finished) {
                process.destroy();
                return false;
            }

            return process.exitValue() == 0;

        } catch (Exception e) {
            log.error("Error checking local port {}: {}", port, e.getMessage());
            return false;
        }
    }

    //Ping服务器检测延迟，纯本地实现的
    public Integer pingServer(String ip) {
        log.debug("Using local ICMP ping for: {}", ip);

        try {
            NativeIcmpServiceImpl icmpService = new NativeIcmpServiceImpl();
            Integer pingTime = icmpService.ping(ip);

            if (pingTime != null) {
                log.debug("ICMP ping successful for {}: {}ms", ip, pingTime);
            } else {
                log.debug("ICMP ping failed for: {}", ip);
            }

            return pingTime;

        } catch (Exception e) {
            log.error("ICMP ping error for {}: {}", ip, e.getMessage());
            return null;
        }
    }

    //执行命令并返回结果
    private CommandResult executeCommandWithResult(SshConfig.SshHost hostConfig, String command) {
        CommandResult result = new CommandResult();

        try {
            Session session = getSession(hostConfig);
            ChannelExec channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);

            InputStream in = channel.getInputStream();
            InputStream err = channel.getErrStream();

            channel.connect(sshConfig.getConnectTimeout());

            //读取输出
            String output = readStream(in);
            String error = readStream(err);

            //等待命令执行完
            while (!channel.isClosed()) {
                Thread.sleep(100);
            }

            result.setExitCode(channel.getExitStatus());
            result.setOutput(output);
            result.setError(error);
            result.setSuccess(result.getExitCode() == 0);

            channel.disconnect();

        } catch (Exception e) {
            result.setError(e.getMessage());
            result.setSuccess(false);
        }

        return result;
    }

    //判断是否是本地ip
    private boolean isLocalHost(String ip) {
        return ip == null ||
                ip.equals("localhost") ||
                ip.startsWith("127.") ||
                ip.equals("::1") ||
                ip.equals("0.0.0.0");
    }

    //关闭所有ssh连接
    public void disconnectAll() {
        sessionCache.values().forEach(session -> {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        });
        sessionCache.clear();
        log.info("All SSH connections closed");
    }

    //命令执行结果类
    @Setter
    @Getter
    public static class CommandResult {
        private boolean success = false;
        private int exitCode = -1;
        private String output = "";
        private String error = "";
    }

    //应用关闭时清理资源
    @jakarta.annotation.PreDestroy
    public void cleanup() {
        disconnectAll();
    }

    //测试SSH连接
    /*
    public boolean testConnection(String hostname) {
        try {
            SshConfig.SshHost hostConfig = sshConfig.getHostConfig(hostname);
            if (hostConfig == null) {
                log.error("Host configuration not found: {}", hostname);
                return false;
            }

            Session session = getSession(hostConfig);
            boolean connected = session.isConnected();

            log.info("SSH connection test for {}: {}", hostname, connected ? "SUCCESS" : "FAILED");
            return connected;

        } catch (Exception e) {
            log.error("SSH connection test failed for {}: {}", hostname, e.getMessage());
            return false;
        }
    }
     */

    //获取所有SSH连接状态
    /*
    public Map<String, Boolean> getConnectionStatus() {
        Map<String, Boolean> status = new java.util.HashMap<>();

        sshConfig.getHosts().keySet().forEach(hostname -> {
            try {
                SshConfig.SshHost hostConfig = sshConfig.getHostConfig(hostname);
                if (hostConfig != null) {
                    String cacheKey = hostConfig.getAddress() + ":" + hostConfig.getPort();
                    Session session = sessionCache.get(cacheKey);
                    status.put(hostname, session != null && session.isConnected());
                } else {
                    status.put(hostname, false);
                }
            } catch (Exception e) {
                status.put(hostname, false);
            }
        });

        return status;
    }
     */
}