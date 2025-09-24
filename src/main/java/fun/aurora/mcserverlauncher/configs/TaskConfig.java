package fun.aurora.mcserverlauncher.configs;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "launcher")
public class TaskConfig {

    private Map<String, Task> tasks;
    private Map<String, Server> ping;

    private Map<Integer, Integer> timeOptions = new LinkedHashMap<>();

    @Setter
    @Getter
    public static class Task {
        private Integer id;

        private Map<String, List<String>> command;
        private Map<String, List<String>> shutdownCommand;

        private String telnet;
        private Integer maxTime;
        private List<Integer> allowedTimes;
    }

    @Setter
    @Getter
    public static class Server {
        private String ip;
    }

    public TaskConfig() {
        timeOptions.put(1, 3);   //1:3时
        timeOptions.put(2, 6);   //2:6时
        timeOptions.put(3, 12);  //3:12时
        timeOptions.put(4, 24);  //4:24时
    }

    //根据选项id获取小时数
    public Integer getHoursByOption(Integer optionId) {
        return timeOptions.get(optionId);
    }

    //验证时间选项是否有效
    public boolean isValidTimeOption(Integer optionId) {
        return timeOptions.containsKey(optionId);
    }

    //获取所有时间选项
    public Map<Integer, Integer> getTimeOptions() {
        return new LinkedHashMap<>(timeOptions); //返回副本
    }
}