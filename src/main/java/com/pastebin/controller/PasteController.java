package com.pastebin.controller;

import com.pastebin.dto.CreatePasteRequest;
import com.pastebin.dto.CreatePasteResponse;
import com.pastebin.dto.PasteResponse;
import com.pastebin.entity.Paste;
import com.pastebin.service.PasteService;
import com.pastebin.util.KeyGenerator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Paste 相关接口
 */
@RestController
@RequestMapping("/pastes")
@RequiredArgsConstructor
@Slf4j
public class PasteController {

    private final PasteService pasteService;

    /**
     * 创建 Paste
     * POST /api/pastes
     */
    @PostMapping
    public ResponseEntity<CreatePasteResponse> createPaste(@Valid @RequestBody CreatePasteRequest request) {
        log.info("收到创建 Paste 请求，内容长度: {}, 语法: {}, 阅后即焚: {}", 
                request.getContent().length(), 
                request.getSyntax(), 
                request.getIsBurnAfterReading());

        // 生成唯一短码
        String key = KeyGenerator.generate();

        // 计算过期时间
        LocalDateTime expireAt = null;
        if (request.getExpireMinutes() != null && request.getExpireMinutes() > 0) {
            expireAt = LocalDateTime.now().plusMinutes(request.getExpireMinutes());
        }

        // 构建实体
        Paste paste = Paste.builder()
                .key(key)
                .content(request.getContent())
                .syntax(request.getSyntax() != null ? request.getSyntax() : "plaintext")
                .isBurnAfterReading(request.getIsBurnAfterReading() != null ? request.getIsBurnAfterReading() : false)
                .expireAt(expireAt)
                .build();

        // 保存
        pasteService.save(paste);

        log.info("Paste 创建成功，key: {}", key);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CreatePasteResponse.builder()
                        .key(key)
                        .url("/" + key)
                        .build());
    }

    /**
     * 获取 Paste
     * GET /api/pastes/{key}
     */
    @GetMapping("/{key}")
    public ResponseEntity<?> getPaste(@PathVariable String key) {
        log.info("获取 Paste，key: {}", key);

        // 使用阅后即焚方法获取（如果设置了阅后即焚，会自动删除）
        Optional<Paste> pasteOpt = pasteService.getAndBurn(key);

        if (pasteOpt.isEmpty()) {
            log.warn("Paste 不存在或已过期/删除，key: {}", key);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("内容不存在或已过期"));
        }

        Paste paste = pasteOpt.get();

        // 检查是否过期
        if (paste.getExpireAt() != null && paste.getExpireAt().isBefore(LocalDateTime.now())) {
            log.warn("Paste 已过期，key: {}", key);
            return ResponseEntity.status(HttpStatus.GONE)
                    .body(new ErrorResponse("内容已过期"));
        }

        return ResponseEntity.ok(PasteResponse.builder()
                .key(paste.getKey())
                .content(paste.getContent())
                .syntax(paste.getSyntax())
                .isBurnAfterReading(paste.getIsBurnAfterReading())
                .expireAt(paste.getExpireAt())
                .createdAt(paste.getCreatedAt())
                .build());
    }

    /**
     * 获取 Raw 纯文本内容
     * GET /api/pastes/{key}/raw
     * 返回纯文本，适合 curl、wget 等命令行工具直接获取
     */
    @GetMapping(value = "/{key}/raw", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getRawPaste(@PathVariable String key) {
        log.info("获取 Raw Paste，key: {}", key);

        // 注意：Raw 模式不触发阅后即焚，使用普通查询
        Optional<Paste> pasteOpt = pasteService.findByKey(key);

        if (pasteOpt.isEmpty()) {
            log.warn("Paste 不存在，key: {}", key);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Error: Content not found or expired");
        }

        Paste paste = pasteOpt.get();

        // 检查是否过期
        if (paste.getExpireAt() != null && paste.getExpireAt().isBefore(LocalDateTime.now())) {
            log.warn("Paste 已过期，key: {}", key);
            return ResponseEntity.status(HttpStatus.GONE)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Error: Content has expired");
        }

        // 阅后即焚的内容不允许 Raw 访问（防止绕过销毁机制）
        if (Boolean.TRUE.equals(paste.getIsBurnAfterReading())) {
            log.warn("阅后即焚内容不支持 Raw 模式，key: {}", key);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Error: Burn-after-reading content cannot be accessed in raw mode");
        }

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(paste.getContent());
    }

    /**
     * 下载 Paste 为文件
     * GET /api/pastes/{key}/download
     * 返回文件下载响应
     */
    @GetMapping("/{key}/download")
    public ResponseEntity<byte[]> downloadPaste(@PathVariable String key) {
        log.info("下载 Paste，key: {}", key);

        // 下载模式也不触发阅后即焚
        Optional<Paste> pasteOpt = pasteService.findByKey(key);

        if (pasteOpt.isEmpty()) {
            log.warn("Paste 不存在，key: {}", key);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        Paste paste = pasteOpt.get();

        // 检查是否过期
        if (paste.getExpireAt() != null && paste.getExpireAt().isBefore(LocalDateTime.now())) {
            log.warn("Paste 已过期，key: {}", key);
            return ResponseEntity.status(HttpStatus.GONE).build();
        }

        // 阅后即焚的内容不允许下载
        if (Boolean.TRUE.equals(paste.getIsBurnAfterReading())) {
            log.warn("阅后即焚内容不支持下载，key: {}", key);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // 根据语法类型确定文件扩展名
        String extension = getFileExtension(paste.getSyntax());
        String filename = key + extension;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", filename);

        return ResponseEntity.ok()
                .headers(headers)
                .body(paste.getContent().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * 根据语法类型获取文件扩展名
     */
    private String getFileExtension(String syntax) {
        if (syntax == null) return ".txt";
        return switch (syntax.toLowerCase()) {
            case "java" -> ".java";
            case "python" -> ".py";
            case "javascript" -> ".js";
            case "typescript" -> ".ts";
            case "cpp", "c++" -> ".cpp";
            case "csharp", "c#" -> ".cs";
            case "go" -> ".go";
            case "rust" -> ".rs";
            case "php" -> ".php";
            case "ruby" -> ".rb";
            case "html" -> ".html";
            case "css" -> ".css";
            case "json" -> ".json";
            case "xml" -> ".xml";
            case "markdown" -> ".md";
            case "sql" -> ".sql";
            case "shell", "bash" -> ".sh";
            case "yaml" -> ".yml";
            default -> ".txt";
        };
    }

    /**
     * 错误响应
     */
    record ErrorResponse(String message) {}
}
