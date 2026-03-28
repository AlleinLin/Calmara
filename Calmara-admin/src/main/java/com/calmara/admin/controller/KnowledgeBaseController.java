package com.calmara.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.calmara.admin.service.KnowledgeBaseService;
import com.calmara.common.Result;
import com.calmara.model.entity.KnowledgeDocument;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin/knowledge-base")
public class KnowledgeBaseController {

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @PostMapping("/import/json")
    public Result<Integer> importFromJson(@RequestBody String jsonContent) {
        log.info("从JSON导入知识库");
        
        int count = knowledgeBaseService.importFromJson(jsonContent);
        
        return Result.success(count);
    }

    @PostMapping("/import/file")
    public Result<Integer> importFromFile(@RequestParam("file") MultipartFile file) {
        log.info("从文件导入知识库: {}", file.getOriginalFilename());
        
        int count = knowledgeBaseService.importFromFile(file);
        
        return Result.success(count);
    }

    @PostMapping("/batch")
    public Result<Integer> batchImport(@RequestBody List<KnowledgeDocument> documents) {
        log.info("批量导入{}个文档", documents.size());
        
        int count = knowledgeBaseService.batchImport(documents);
        
        return Result.success(count);
    }

    @PostMapping("/add")
    public Result<Boolean> addDocument(@RequestBody KnowledgeDocument document) {
        log.info("添加文档: {}", document.getTitle());
        
        boolean success = knowledgeBaseService.addDocument(document);
        
        return Result.success(success);
    }

    @PutMapping("/update/{id}")
    public Result<Boolean> updateDocument(
            @PathVariable Long id,
            @RequestBody KnowledgeDocument document) {
        
        log.info("更新文档: id={}", id);
        
        boolean success = knowledgeBaseService.updateDocument(id, document);
        
        return Result.success(success);
    }

    @DeleteMapping("/delete/{id}")
    public Result<Boolean> deleteDocument(@PathVariable Long id) {
        log.info("删除文档: id={}", id);
        
        boolean success = knowledgeBaseService.deleteDocument(id);
        
        return Result.success(success);
    }

    @GetMapping("/get/{id}")
    public Result<KnowledgeDocument> getDocument(@PathVariable Long id) {
        KnowledgeDocument doc = knowledgeBaseService.getDocument(id);
        
        if (doc == null) {
            return Result.error(404, "文档不存在");
        }
        
        return Result.success(doc);
    }

    @GetMapping("/list")
    public Result<IPage<KnowledgeDocument>> listDocuments(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String category) {
        
        log.info("查询文档列表: page={}, size={}, category={}", page, size, category);
        
        IPage<KnowledgeDocument> result = knowledgeBaseService.listDocuments(page, size, category);
        
        return Result.success(result);
    }

    @GetMapping("/categories")
    public Result<List<String>> listCategories() {
        List<String> categories = knowledgeBaseService.listCategories();
        
        return Result.success(categories);
    }

    @GetMapping("/stats")
    public Result<KnowledgeBaseService.KnowledgeStats> getStats() {
        KnowledgeBaseService.KnowledgeStats stats = knowledgeBaseService.getStats();
        
        return Result.success(stats);
    }

    @PostMapping("/incremental-update")
    public Result<Integer> incrementalUpdate(@RequestBody List<KnowledgeDocument> documents) {
        log.info("增量更新{}个文档", documents.size());
        
        int count = knowledgeBaseService.incrementalUpdate(documents);
        
        return Result.success(count);
    }

    @PostMapping("/validate")
    public Result<ValidationResult> validateDocuments(@RequestBody List<KnowledgeDocument> documents) {
        log.info("验证{}个文档", documents.size());
        
        ValidationResult result = new ValidationResult();
        result.setTotal(documents.size());
        
        int valid = 0;
        int invalid = 0;
        
        for (KnowledgeDocument doc : documents) {
            if (doc.getTitle() != null && !doc.getTitle().isBlank()
                    && doc.getContent() != null && !doc.getContent().isBlank()) {
                valid++;
            } else {
                invalid++;
            }
        }
        
        result.setValid(valid);
        result.setInvalid(invalid);
        
        return Result.success(result);
    }

    @Data
    public static class ValidationResult {
        private Integer total;
        private Integer valid;
        private Integer invalid;
    }
}
