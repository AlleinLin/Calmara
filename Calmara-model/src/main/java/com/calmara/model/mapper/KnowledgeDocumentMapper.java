package com.calmara.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.calmara.model.entity.KnowledgeDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocument> {

    @Select("SELECT * FROM knowledge_document WHERE category = #{category} ORDER BY created_at DESC")
    List<KnowledgeDocument> findByCategory(String category);

    @Select("SELECT DISTINCT category FROM knowledge_document WHERE category IS NOT NULL")
    List<String> findAllCategories();

    @Select("SELECT COUNT(*) FROM knowledge_document WHERE category = #{category}")
    Long countByCategory(String category);

    @Select("SELECT * FROM knowledge_document WHERE title LIKE CONCAT('%', #{keyword}, '%') OR content LIKE CONCAT('%', #{keyword}, '%')")
    List<KnowledgeDocument> searchByKeyword(String keyword);
}
