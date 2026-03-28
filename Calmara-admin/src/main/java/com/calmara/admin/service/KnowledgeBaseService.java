package com.calmara.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.calmara.agent.rag.Document;
import com.calmara.agent.rag.VectorStore;
import com.calmara.model.entity.KnowledgeDocument;
import com.calmara.model.mapper.KnowledgeDocumentMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KnowledgeBaseService {

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private KnowledgeDocumentMapper documentMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Transactional
    public int importFromJson(String jsonContent) {
        log.info("开始从JSON导入知识库");
        
        try {
            KnowledgeDocument[] documents = objectMapper.readValue(jsonContent, KnowledgeDocument[].class);
            
            int successCount = 0;
            for (KnowledgeDocument doc : documents) {
                try {
                    if (doc.getTitle() == null || doc.getContent() == null) {
                        log.warn("文档缺少必要字段，跳过: {}", doc.getId());
                        continue;
                    }
                    
                    doc.setCreatedAt(LocalDateTime.now());
                    doc.setUpdatedAt(LocalDateTime.now());
                    
                    documentMapper.insert(doc);
                    
                    Document vectorDoc = new Document(doc.getTitle(), doc.getContent());
                    vectorStore.addDocument(vectorDoc);
                    
                    successCount++;
                } catch (Exception e) {
                    log.error("导入文档失败: {}", doc.getId(), e);
                }
            }
            
            log.info("成功导入{}个文档", successCount);
            return successCount;
            
        } catch (Exception e) {
            log.error("解析JSON失败", e);
            throw new RuntimeException("导入失败: " + e.getMessage());
        }
    }

    @Transactional
    public int batchImport(List<KnowledgeDocument> documents) {
        log.info("批量导入{}个文档", documents.size());
        
        int successCount = 0;
        for (KnowledgeDocument doc : documents) {
            try {
                if (doc.getTitle() == null || doc.getContent() == null) {
                    continue;
                }
                
                doc.setCreatedAt(LocalDateTime.now());
                doc.setUpdatedAt(LocalDateTime.now());
                
                documentMapper.insert(doc);
                
                Document vectorDoc = new Document(doc.getTitle(), doc.getContent());
                vectorStore.addDocument(vectorDoc);
                
                successCount++;
            } catch (Exception e) {
                log.error("导入文档失败: {}", doc.getId(), e);
            }
        }
        
        return successCount;
    }

    @Transactional
    public boolean addDocument(KnowledgeDocument document) {
        log.info("添加文档: {}", document.getTitle());
        
        try {
            document.setCreatedAt(LocalDateTime.now());
            document.setUpdatedAt(LocalDateTime.now());
            
            documentMapper.insert(document);
            
            Document vectorDoc = new Document(document.getTitle(), document.getContent());
            vectorStore.addDocument(vectorDoc);
            
            return true;
        } catch (Exception e) {
            log.error("添加文档失败", e);
            return false;
        }
    }

    @Transactional
    public boolean updateDocument(Long id, KnowledgeDocument document) {
        log.info("更新文档: id={}", id);
        
        KnowledgeDocument existing = documentMapper.selectById(id);
        if (existing == null) {
            return false;
        }
        
        document.setId(id);
        document.setUpdatedAt(LocalDateTime.now());
        document.setCreatedAt(existing.getCreatedAt());
        
        documentMapper.updateById(document);
        
        Document vectorDoc = new Document(document.getTitle(), document.getContent());
        vectorStore.addDocument(vectorDoc);
        
        return true;
    }

    @Transactional
    public boolean deleteDocument(Long id) {
        log.info("删除文档: id={}", id);
        
        int deleted = documentMapper.deleteById(id);
        return deleted > 0;
    }

    public KnowledgeDocument getDocument(Long id) {
        return documentMapper.selectById(id);
    }

    public IPage<KnowledgeDocument> listDocuments(int page, int size, String category) {
        Page<KnowledgeDocument> pageParam = new Page<>(page, size);
        
        QueryWrapper<KnowledgeDocument> wrapper = new QueryWrapper<>();
        if (category != null && !category.isEmpty()) {
            wrapper.eq("category", category);
        }
        wrapper.orderByDesc("created_at");
        
        return documentMapper.selectPage(pageParam, wrapper);
    }

    public List<String> listCategories() {
        return documentMapper.selectList(null).stream()
                .map(KnowledgeDocument::getCategory)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    public int importFromFile(MultipartFile file) {
        log.info("从文件导入知识库: {}", file.getOriginalFilename());
        
        try {
            String content = new BufferedReader(
                    new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));
            
            return importFromJson(content);
        } catch (Exception e) {
            log.error("文件导入失败", e);
            throw new RuntimeException("文件导入失败: " + e.getMessage());
        }
    }

    public KnowledgeStats getStats() {
        Long total = documentMapper.selectCount(null);
        
        Map<String, Long> categoryCount = documentMapper.selectList(null).stream()
                .collect(Collectors.groupingBy(
                        doc -> doc.getCategory() != null ? doc.getCategory() : "未分类",
                        Collectors.counting()
                ));
        
        KnowledgeStats stats = new KnowledgeStats();
        stats.setTotalDocuments(total.intValue());
        stats.setCategoryDistribution(categoryCount);
        stats.setVectorStoreDocuments(vectorStore.getDocumentCount());
        
        return stats;
    }

    @Transactional
    public int incrementalUpdate(List<KnowledgeDocument> documents) {
        log.info("增量更新{}个文档", documents.size());
        
        int updateCount = 0;
        for (KnowledgeDocument doc : documents) {
            if (doc.getId() != null) {
                KnowledgeDocument existing = documentMapper.selectById(doc.getId());
                if (existing != null) {
                    updateDocument(doc.getId(), doc);
                    updateCount++;
                    continue;
                }
            }
            
            addDocument(doc);
            updateCount++;
        }
        
        return updateCount;
    }

    @Data
    public static class KnowledgeStats {
        private Integer totalDocuments;
        private Map<String, Long> categoryDistribution;
        private Integer vectorStoreDocuments;
    }
}
