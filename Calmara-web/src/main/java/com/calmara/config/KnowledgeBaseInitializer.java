package com.calmara.config;

import com.calmara.agent.rag.KnowledgeBaseLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class KnowledgeBaseInitializer implements CommandLineRunner {

    @Autowired
    private KnowledgeBaseLoader knowledgeBaseLoader;

    @Value("${calmara.knowledge.auto-load:true}")
    private boolean autoLoad;

    @Override
    public void run(String... args) throws Exception {
        if (autoLoad) {
            log.info("========================================");
            log.info("开始自动加载中文知识库...");
            log.info("========================================");
            
            try {
                int count = knowledgeBaseLoader.loadKnowledgeBase();
                log.info("========================================");
                log.info("中文知识库加载完成，共{}个文档", count);
                log.info("========================================");
            } catch (Exception e) {
                log.error("知识库加载失败", e);
            }
        } else {
            log.info("知识库自动加载已禁用");
        }
    }
}
