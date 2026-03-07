package io.github.kamill7779.qforge.question.controller;

import io.github.kamill7779.qforge.question.entity.ExamParseQuestion;
import io.github.kamill7779.qforge.question.entity.ExamParseTask;
import io.github.kamill7779.qforge.question.service.ExamParseCommandService;
import io.github.kamill7779.qforge.question.service.ExamParseConfirmService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/exam-parse")
public class ExamParseController {

    private final ExamParseCommandService commandService;
    private final ExamParseConfirmService confirmService;

    public ExamParseController(ExamParseCommandService commandService,
                                ExamParseConfirmService confirmService) {
        this.commandService = commandService;
        this.confirmService = confirmService;
    }

    /**
     * 上传文件、创建解析任务。
     */
    @PostMapping("/tasks")
    public ResponseEntity<Map<String, Object>> createTask(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "hasAnswerHint", defaultValue = "false") boolean hasAnswerHint,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser) {

        ExamParseTask task = commandService.createTask(files, hasAnswerHint, requestUser);

        Map<String, Object> body = new HashMap<>();
        body.put("taskUuid", task.getTaskUuid());
        body.put("status", task.getStatus());
        body.put("fileCount", task.getFileCount());
        body.put("message", "任务已提交，请通过 WebSocket 监听进度");

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }

    /**
     * 获取当前用户全部解析任务列表。
     */
    @GetMapping("/tasks")
    public ResponseEntity<List<ExamParseTask>> listTasks(
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser) {
        return ResponseEntity.ok(commandService.listTasks(requestUser));
    }

    /**
     * 查询任务状态 + 进度 + 已解析题目列表。
     */
    @GetMapping("/tasks/{taskUuid}")
    public ResponseEntity<Map<String, Object>> getTask(
            @PathVariable("taskUuid") String taskUuid,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser) {

        ExamParseTask task = commandService.getTask(taskUuid, requestUser);
        List<ExamParseQuestion> questions = commandService.listQuestions(taskUuid);

        Map<String, Object> body = new HashMap<>();
        body.put("task", task);
        body.put("questions", questions);

        return ResponseEntity.ok(body);
    }

    /**
     * 前端编辑单道拆题结果。
     */
    @PutMapping("/tasks/{taskUuid}/questions/{seqNo}")
    public ResponseEntity<ExamParseQuestion> updateQuestion(
            @PathVariable("taskUuid") String taskUuid,
            @PathVariable("seqNo") int seqNo,
            @RequestBody Map<String, String> updates,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser) {

        commandService.getTask(taskUuid, requestUser); // 权限校验
        ExamParseQuestion updated = commandService.updateQuestion(taskUuid, seqNo, updates);
        return ResponseEntity.ok(updated);
    }

    /**
     * 批量确认 → 为所有 PENDING 题目创建正式 question + answer + 资产记录。
     */
    @PostMapping("/tasks/{taskUuid}/confirm")
    public ResponseEntity<Map<String, Object>> confirmTask(
            @PathVariable("taskUuid") String taskUuid,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser) {

        int confirmedCount = confirmService.confirm(taskUuid, requestUser);

        Map<String, Object> body = new HashMap<>();
        body.put("taskUuid", taskUuid);
        body.put("confirmedCount", confirmedCount);
        body.put("message", confirmedCount + " 道题目已确认入库");

        return ResponseEntity.ok(body);
    }

    /**
     * 单题确认入库。
     */
    @PostMapping("/tasks/{taskUuid}/questions/{seqNo}/confirm")
    public ResponseEntity<Map<String, Object>> confirmSingleQuestion(
            @PathVariable("taskUuid") String taskUuid,
            @PathVariable("seqNo") int seqNo,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser) {

        String questionUuid = confirmService.confirmSingle(taskUuid, seqNo, requestUser);

        Map<String, Object> body = new HashMap<>();
        body.put("taskUuid", taskUuid);
        body.put("seqNo", seqNo);
        body.put("questionUuid", questionUuid);
        body.put("message", "第 " + seqNo + " 题已确认入库");

        return ResponseEntity.ok(body);
    }

    /**
     * 单题跳过。
     */
    @PostMapping("/tasks/{taskUuid}/questions/{seqNo}/skip")
    public ResponseEntity<Map<String, Object>> skipQuestion(
            @PathVariable("taskUuid") String taskUuid,
            @PathVariable("seqNo") int seqNo,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser) {

        confirmService.skipQuestion(taskUuid, seqNo, requestUser);

        Map<String, Object> body = new HashMap<>();
        body.put("taskUuid", taskUuid);
        body.put("seqNo", seqNo);
        body.put("message", "第 " + seqNo + " 题已跳过");

        return ResponseEntity.ok(body);
    }

    /**
     * 恢复已跳过的题目为 PENDING 状态。
     */
    @PostMapping("/tasks/{taskUuid}/questions/{seqNo}/unskip")
    public ResponseEntity<Map<String, Object>> unskipQuestion(
            @PathVariable("taskUuid") String taskUuid,
            @PathVariable("seqNo") int seqNo,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser) {

        confirmService.unskipQuestion(taskUuid, seqNo, requestUser);

        Map<String, Object> body = new HashMap<>();
        body.put("taskUuid", taskUuid);
        body.put("seqNo", seqNo);
        body.put("message", "第 " + seqNo + " 题已恢复");

        return ResponseEntity.ok(body);
    }

    /**
     * 取消/删除解析任务及暂存数据。
     */
    @DeleteMapping("/tasks/{taskUuid}")
    public ResponseEntity<Void> deleteTask(
            @PathVariable("taskUuid") String taskUuid,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser) {

        commandService.deleteTask(taskUuid, requestUser);
        return ResponseEntity.noContent().build();
    }
}
