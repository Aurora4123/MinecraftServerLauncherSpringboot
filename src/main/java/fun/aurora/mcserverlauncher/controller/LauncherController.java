package fun.aurora.mcserverlauncher.controller;

import fun.aurora.mcserverlauncher.serviceanddao.impl.TaskServiceImpl;
import jakarta.annotation.Resource;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/launcher")
@CrossOrigin(origins = "*")
public class LauncherController {

    @Resource
    private TaskServiceImpl taskService;

    //获取任务状态

    @GetMapping
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = taskService.getTasksStatus();
        return ResponseEntity.ok(status);
    }

    //启动任务
    @PostMapping
    public ResponseEntity<Map<String, Object>> startTask(@RequestBody StartRequest request) {
        //直接返回taskService.startTask的结果
        Map<String, Object> response = taskService.startTask(request.getTask(), request.getTimeOption());
        return ResponseEntity.ok(response);
    }

    //停止任务
    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stopTask(@RequestBody StopRequest request) {
        //直接返回taskService.stopTask的结果
        Map<String, Object> response = taskService.stopTask(request.getTask());
        return ResponseEntity.ok(response);
    }

    //强制停止任务
    /*
    @PostMapping("/force-stop")
    public ResponseEntity<Map<String, Object>> forceStopTask(@RequestBody StopRequest request) {
        Map<String, Object> response = taskService.forceStopTask(request.getTask());
        return ResponseEntity.ok(response);
    }
     */

    //重启任务
    /*
    @PostMapping("/restart")
    public ResponseEntity<Map<String, Object>> restartTask(@RequestBody RestartRequest request) {
        Map<String, Object> response = taskService.restartTask(request.getTask(), request.getTimeOption());
        return ResponseEntity.ok(response);
    }

     */

    //获取任务详情
    @GetMapping("/{taskName}")
    public ResponseEntity<Map<String, Object>> getTaskDetails(@PathVariable String taskName) {
        Map<String, Object> details = taskService.getTaskDetails(taskName);
        return ResponseEntity.ok(details);
    }

    //请求类
    @Setter
    @Getter
    public static class StartRequest {
        private String task;
        private Integer timeOption; //读取时间挡位
    }

    @Setter
    @Getter
    public static class RestartRequest {
        private String task;
        private Integer timeOption;
    }

    @Setter
    @Getter
    public static class StopRequest {
        private String task;
    }
}