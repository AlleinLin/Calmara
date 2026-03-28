package com.calmara.agent.rag;

import lombok.Data;

import java.util.Map;

@Data
public class Document {

    private String id;
    private String content;
    private Map<String, Object> metadata;

    public Document() {}

    public Document(String content) {
        this.content = content;
    }

    public Document(String id, String content) {
        this.id = id;
        this.content = content;
    }

    public Document(String id, String content, Map<String, Object> metadata) {
        this.id = id;
        this.content = content;
        this.metadata = metadata;
    }
}
