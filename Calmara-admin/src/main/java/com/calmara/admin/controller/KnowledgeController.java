package com.calmara.admin.controller;

import com.calmara.agent.rag.Document;
import com.calmara.agent.rag.VectorStore;
import com.calmara.common.Result;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@RestController
@RequestMapping("/api/admin/knowledge")
public class KnowledgeController {

    private final VectorStore vectorStore;
    private final Map<Long, Document> documentStore = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    public KnowledgeController(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
        initDefaultKnowledge();
    }

    private void initDefaultKnowledge() {
        addDocumentInternal("焦虑症缓解方法",
                "焦虑是一种常见的情绪反应，可以通过以下方法缓解：\n" +
                "1. 深呼吸练习：缓慢吸气4秒，屏住呼吸4秒，缓慢呼气4秒\n" +
                "2. 正念冥想：专注于当下，观察自己的呼吸和身体感受\n" +
                "3. 身体放松：渐进式肌肉放松，从头到脚逐个部位放松\n" +
                "4. 规律运动：每周3-5次有氧运动，如散步、跑步、游泳\n" +
                "5. 充足睡眠：保持7-9小时的睡眠时间");

        addDocumentInternal("失眠改善方法",
                "失眠是指难以入睡、睡眠质量差或早醒。以下方法可能有助于改善睡眠：\n" +
                "1. 保持规律作息：每天在同一时间睡觉和起床\n" +
                "2. 创造良好的睡眠环境：安静、黑暗、适宜的温度\n" +
                "3. 睡前避免刺激：减少咖啡因、酒精摄入，避免使用电子设备\n" +
                "4. 放松技巧：温水泡脚、听轻音乐、阅读纸质书\n" +
                "5. 如果持续失眠，建议寻求专业帮助");

        addDocumentInternal("抑郁症自助方法",
                "抑郁症是一种常见的心理障碍，以下建议可能有所帮助：\n" +
                "1. 保持社交：与家人朋友保持联系，不要孤立自己\n" +
                "2. 适度运动：运动可以促进多巴胺分泌，改善情绪\n" +
                "3. 建立日常规律：即使没有动力，也要保持基本的生活节奏\n" +
                "4. 记录情绪：写日记有助于识别情绪模式和触发因素\n" +
                "5. 寻求专业帮助：心理咨询师或精神科医生可以提供专业支持");

        addDocumentInternal("危机干预资源",
                "如果您或您认识的人有自杀想法，请立即寻求帮助：\n" +
                "全国心理援助热线：400-161-9995\n" +
                "北京心理危机研究与干预中心：010-82951332\n" +
                "生命热线：400-821-1215\n\n" +
                "请记住：您的生命非常宝贵，无论现在感到多么绝望，总有可以帮助您的人。\n" +
                "请联系身边的亲人朋友，或直接拨打上述热线。");

        addDocumentInternal("压力管理技巧",
                "压力是现代生活中常见的问题，以下方法可以帮助管理压力：\n" +
                "1. 时间管理：合理安排时间，设定优先级\n" +
                "2. 学会说\"不\"：不要承担超出自己能力范围的任务\n" +
                "3. 保持健康生活方式：均衡饮食、规律运动、充足睡眠\n" +
                "4. 寻求支持：与朋友、家人或专业人士交流\n" +
                "5. 放松练习：瑜伽、冥想、深呼吸等");

        log.info("默认知识库初始化完成，共{}个文档", documentStore.size());
    }

    private void addDocumentInternal(String title, String content) {
        Long id = idGenerator.getAndIncrement();
        Document doc = new Document(title, content);
        documentStore.put(id, doc);
        vectorStore.addDocument(doc);
    }

    @GetMapping("/documents")
    public Result<List<DocumentInfo>> listDocuments() {
        log.info("获取知识库文档列表");

        List<DocumentInfo> documents = new ArrayList<>();
        documentStore.forEach((id, doc) -> {
            DocumentInfo info = new DocumentInfo();
            info.setId(id);
            info.setTitle(doc.getId());
            info.setContentPreview(doc.getContent().length() > 100
                    ? doc.getContent().substring(0, 100) + "..."
                    : doc.getContent());
            info.setFullContent(doc.getContent());
            documents.add(info);
        });

        return Result.success(documents);
    }

    @PostMapping("/documents")
    public Result<Boolean> addDocument(@RequestBody DocumentRequest request) {
        log.info("添加知识库文档: title={}", request.getTitle());

        if (request.getTitle() == null || request.getTitle().isBlank()) {
            return Result.error(400, "标题不能为空");
        }

        if (request.getContent() == null || request.getContent().isBlank()) {
            return Result.error(400, "内容不能为空");
        }

        addDocumentInternal(request.getTitle(), request.getContent());

        return Result.success(true);
    }

    @PutMapping("/documents/{id}")
    public Result<Boolean> updateDocument(
            @PathVariable Long id,
            @RequestBody DocumentRequest request) {

        log.info("更新知识库文档: id={}, title={}", id, request.getTitle());

        Document existingDoc = documentStore.get(id);
        if (existingDoc == null) {
            return Result.error(404, "文档不存在");
        }

        Document updatedDoc = new Document(
                request.getTitle() != null ? request.getTitle() : existingDoc.getId(),
                request.getContent() != null ? request.getContent() : existingDoc.getContent()
        );

        documentStore.put(id, updatedDoc);
        vectorStore.addDocument(updatedDoc);

        return Result.success(true);
    }

    @DeleteMapping("/documents/{id}")
    public Result<Boolean> deleteDocument(@PathVariable Long id) {
        log.info("删除知识库文档: id={}", id);

        Document removed = documentStore.remove(id);
        if (removed == null) {
            return Result.error(404, "文档不存在");
        }

        return Result.success(true);
    }

    @GetMapping("/documents/{id}")
    public Result<DocumentInfo> getDocument(@PathVariable Long id) {
        log.info("获取知识库文档详情: id={}", id);

        Document doc = documentStore.get(id);
        if (doc == null) {
            return Result.error(404, "文档不存在");
        }

        DocumentInfo info = new DocumentInfo();
        info.setId(id);
        info.setTitle(doc.getId());
        info.setFullContent(doc.getContent());
        info.setContentPreview(doc.getContent().length() > 100
                ? doc.getContent().substring(0, 100) + "..."
                : doc.getContent());

        return Result.success(info);
    }

    @PostMapping("/search")
    public Result<List<Document>> searchDocuments(@RequestBody SearchRequest request) {
        log.info("搜索知识库: query={}, topK={}", request.getQuery(), request.getTopK());

        if (request.getQuery() == null || request.getQuery().isBlank()) {
            return Result.error(400, "搜索关键词不能为空");
        }

        int topK = request.getTopK() != null ? request.getTopK() : 5;
        List<Document> results = vectorStore.similaritySearch(request.getQuery(), topK);

        return Result.success(results);
    }

    @GetMapping("/stats")
    public Result<KnowledgeStats> getKnowledgeStats() {
        log.info("获取知识库统计信息");

        KnowledgeStats stats = new KnowledgeStats();
        stats.setTotalDocuments(documentStore.size());
        stats.setTotalCharacters(documentStore.values().stream()
                .mapToLong(d -> d.getContent().length())
                .sum());

        return Result.success(stats);
    }

    @PostMapping("/batch")
    public Result<Boolean> batchAddDocuments(@RequestBody List<DocumentRequest> documents) {
        log.info("批量添加知识库文档: count={}", documents.size());

        for (DocumentRequest doc : documents) {
            if (doc.getTitle() != null && !doc.getTitle().isBlank()
                    && doc.getContent() != null && !doc.getContent().isBlank()) {
                addDocumentInternal(doc.getTitle(), doc.getContent());
            }
        }

        return Result.success(true);
    }

    @Data
    public static class DocumentRequest {
        private String title;
        private String content;
    }

    @Data
    public static class DocumentInfo {
        private Long id;
        private String title;
        private String contentPreview;
        private String fullContent;
    }

    @Data
    public static class SearchRequest {
        private String query;
        private Integer topK;
    }

    @Data
    public static class KnowledgeStats {
        private Integer totalDocuments;
        private Long totalCharacters;
    }
}
