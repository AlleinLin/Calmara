package com.calmara.agent.rag.store;

import com.alibaba.fastjson.JSONObject;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.*;
import io.milvus.v2.service.collection.response.*;
import io.milvus.v2.service.vector.request.*;
import io.milvus.v2.service.vector.response.*;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class MilvusVectorStoreBackend implements VectorStoreBackend {

    private static final String PROVIDER_NAME = "Milvus";
    private static final String ID_FIELD = "id";
    private static final String CONTENT_FIELD = "content";
    private static final String EMBEDDING_FIELD = "embedding";
    private static final String METADATA_FIELD = "metadata";

    private final String host;
    private final int port;
    private final String database;
    private final String collectionName;
    private final int dimension;
    private final String indexType;
    private final String metricType;
    private final boolean enabled;

    private MilvusClientV2 client;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean healthy = new AtomicBoolean(false);
    private final AtomicLong successfulOperations = new AtomicLong(0);
    private final AtomicLong failedOperations = new AtomicLong(0);

    public MilvusVectorStoreBackend(String host, int port, String database, String collectionName,
                                    int dimension, String indexType, String metricType, boolean enabled) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.collectionName = collectionName;
        this.dimension = dimension;
        this.indexType = indexType;
        this.metricType = metricType;
        this.enabled = enabled;
    }

    @Override
    public String getName() {
        return PROVIDER_NAME;
    }

    @Override
    public void initialize() {
        if (!enabled) {
            log.warn("Milvus is disabled, skipping initialization");
            return;
        }

        try {
            ConnectConfig.ConnectConfigBuilder builder = ConnectConfig.builder()
                    .uri("http://" + host + ":" + port);

            if (database != null && !database.isEmpty()) {
                builder.dbName(database);
            }

            client = new MilvusClientV2(builder.build());

            ensureCollectionExists();

            initialized.set(true);
            healthy.set(true);
            log.info("Milvus client initialized: host={}:{}, database={}, collection={}",
                    host, port, database, collectionName);

        } catch (Exception e) {
            log.error("Failed to initialize Milvus client", e);
            initialized.set(true);
            healthy.set(false);
        }
    }

    private void ensureCollectionExists() {
        try {
            boolean collectionExists = false;
            try {
                ListCollectionsResp listResp = client.listCollections();
                collectionExists = listResp.getCollectionNames() != null
                        && listResp.getCollectionNames().contains(collectionName);
            } catch (Exception e) {
                log.debug("Failed to list collections, attempting to create: {}", e.getMessage());
            }

            if (collectionExists) {
                log.info("Milvus collection already exists: {}", collectionName);
                return;
            }

            createCollection();

        } catch (Exception e) {
            log.error("Failed to check/create collection", e);
            throw new RuntimeException("Failed to ensure collection exists", e);
        }
    }

    private void createCollection() {
        List<CreateCollectionReq.FieldSchema> fields = new ArrayList<>();

        fields.add(CreateCollectionReq.FieldSchema.builder()
                .name(ID_FIELD)
                .dataType(DataType.VarChar)
                .maxLength(256)
                .isPrimaryKey(true)
                .autoID(false)
                .build());

        fields.add(CreateCollectionReq.FieldSchema.builder()
                .name(CONTENT_FIELD)
                .dataType(DataType.VarChar)
                .maxLength(65535)
                .build());

        fields.add(CreateCollectionReq.FieldSchema.builder()
                .name(EMBEDDING_FIELD)
                .dataType(DataType.FloatVector)
                .dimension(dimension)
                .build());

        fields.add(CreateCollectionReq.FieldSchema.builder()
                .name(METADATA_FIELD)
                .dataType(DataType.JSON)
                .build());

        CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder()
                .fieldSchemaList(fields)
                .build();

        List<IndexParam> indexParams = new ArrayList<>();
        indexParams.add(IndexParam.builder()
                .fieldName(EMBEDDING_FIELD)
                .indexType(IndexParam.IndexType.valueOf(indexType))
                .metricType(IndexParam.MetricType.valueOf(metricType))
                .build());

        CreateCollectionReq request = CreateCollectionReq.builder()
                .collectionName(collectionName)
                .collectionSchema(schema)
                .indexParams(indexParams)
                .build();

        client.createCollection(request);
        log.info("Milvus collection created: {}", collectionName);
    }

    @Override
    public void shutdown() {
        if (client != null) {
            try {
                client.close(0);
            } catch (Exception e) {
                log.warn("Error closing Milvus client", e);
            }
        }
        initialized.set(false);
        healthy.set(false);
        log.info("Milvus backend shutdown");
    }

    @Override
    public void add(String id, String content, float[] embedding, Map<String, Object> metadata) {
        if (!isAvailable()) {
            log.warn("Milvus not available, skipping document add");
            return;
        }

        try {
            List<JSONObject> dataList = new ArrayList<>();
            JSONObject data = new JSONObject();
            data.put(ID_FIELD, id);
            data.put(CONTENT_FIELD, content);
            data.put(EMBEDDING_FIELD, toFloatList(embedding));
            data.put(METADATA_FIELD, metadata != null ? metadata : new HashMap<>());
            dataList.add(data);

            InsertReq request = InsertReq.builder()
                    .collectionName(collectionName)
                    .data(dataList)
                    .build();

            client.insert(request);
            successfulOperations.incrementAndGet();
            log.debug("Document added to Milvus: id={}", id);

        } catch (Exception e) {
            failedOperations.incrementAndGet();
            log.error("Failed to add document to Milvus: id={}, error={}", id, e.getMessage());
        }
    }

    @Override
    public void addBatch(List<String> ids, List<String> contents, List<float[]> embeddings, List<Map<String, Object>> metadatas) {
        if (!isAvailable()) {
            log.warn("Milvus not available, skipping batch add");
            return;
        }

        try {
            List<JSONObject> dataList = new ArrayList<>();
            for (int i = 0; i < ids.size(); i++) {
                JSONObject data = new JSONObject();
                data.put(ID_FIELD, ids.get(i));
                data.put(CONTENT_FIELD, contents.get(i));
                data.put(EMBEDDING_FIELD, toFloatList(embeddings.get(i)));
                data.put(METADATA_FIELD, i < metadatas.size() ? metadatas.get(i) : new HashMap<>());
                dataList.add(data);
            }

            InsertReq request = InsertReq.builder()
                    .collectionName(collectionName)
                    .data(dataList)
                    .build();

            client.insert(request);
            successfulOperations.addAndGet(ids.size());
            log.info("Batch add to Milvus: count={}", ids.size());

        } catch (Exception e) {
            failedOperations.addAndGet(ids.size());
            log.error("Failed to batch add to Milvus: {}", e.getMessage());
        }
    }

    @Override
    public List<SearchResult> search(float[] queryEmbedding, int topK, double similarityThreshold, Map<String, Object> filter) {
        if (!isAvailable()) {
            return List.of();
        }

        try {
            SearchReq.SearchReqBuilder builder = SearchReq.builder()
                    .collectionName(collectionName)
                    .data(List.of(toFloatList(queryEmbedding)))
                    .annsField(EMBEDDING_FIELD)
                    .topK(topK)
                    .outputFields(List.of(ID_FIELD, CONTENT_FIELD, METADATA_FIELD))
                    .consistencyLevel(ConsistencyLevel.BOUNDED);

            if (filter != null && !filter.isEmpty()) {
                builder.filter(buildFilterExpression(filter));
            }

            SearchResp response = client.search(builder.build());

            List<SearchResult> results = new ArrayList<>();
            List<List<SearchResp.SearchResult>> searchResults = response.getSearchResults();

            if (!searchResults.isEmpty()) {
                for (SearchResp.SearchResult sr : searchResults.get(0)) {
                    double score = sr.getDistance();
                    double similarity = "COSINE".equals(metricType) ? score : 1.0 / (1.0 + score);

                    if (similarity >= similarityThreshold) {
                        Map<String, Object> entity = sr.getEntity();
                        String id = (String) entity.get(ID_FIELD);
                        String content = (String) entity.get(CONTENT_FIELD);
                        @SuppressWarnings("unchecked")
                        Map<String, Object> metadata = (Map<String, Object>) entity.get(METADATA_FIELD);

                        results.add(SearchResult.of(id, content, similarity, metadata));
                    }
                }
            }

            successfulOperations.incrementAndGet();
            return results;

        } catch (Exception e) {
            failedOperations.incrementAndGet();
            log.error("Failed to search Milvus: {}", e.getMessage());
            return List.of();
        }
    }

    private String buildFilterExpression(Map<String, Object> filter) {
        List<String> conditions = new ArrayList<>();
        for (Map.Entry<String, Object> entry : filter.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof String) {
                conditions.add(String.format("metadata[\"%s\"] == \"%s\"", key, value));
            } else if (value instanceof Number) {
                conditions.add(String.format("metadata[\"%s\"] == %s", key, value));
            } else {
                conditions.add(String.format("metadata[\"%s\"] == \"%s\"", key, value.toString()));
            }
        }
        return String.join(" and ", conditions);
    }

    @Override
    public boolean delete(String id) {
        if (!isAvailable()) {
            return false;
        }

        try {
            DeleteReq request = DeleteReq.builder()
                    .collectionName(collectionName)
                    .ids(Collections.singletonList(id))
                    .build();

            client.delete(request);
            successfulOperations.incrementAndGet();
            return true;

        } catch (Exception e) {
            failedOperations.incrementAndGet();
            log.error("Failed to delete from Milvus: id={}, error={}", id, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean deleteBatch(List<String> ids) {
        if (!isAvailable()) {
            return false;
        }

        try {
            DeleteReq request = DeleteReq.builder()
                    .collectionName(collectionName)
                    .ids(new ArrayList<>(ids))
                    .build();

            client.delete(request);
            successfulOperations.addAndGet(ids.size());
            return true;

        } catch (Exception e) {
            failedOperations.addAndGet(ids.size());
            log.error("Failed to batch delete from Milvus: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public long count() {
        if (!isAvailable()) {
            return 0;
        }

        try {
            QueryReq request = QueryReq.builder()
                    .collectionName(collectionName)
                    .outputFields(Collections.singletonList("count(*)"))
                    .build();

            QueryResp response = client.query(request);
            List<QueryResp.QueryResult> queryResults = response.getQueryResults();
            if (queryResults != null && !queryResults.isEmpty()) {
                Object count = queryResults.get(0).getEntity().get("count(*)");
                if (count instanceof Long) {
                    return (Long) count;
                } else if (count instanceof Integer) {
                    return ((Integer) count).longValue();
                }
            }
            return 0;

        } catch (Exception e) {
            log.error("Failed to get Milvus document count: {}", e.getMessage());
            return 0;
        }
    }

    @Override
    public boolean isAvailable() {
        return enabled && initialized.get() && healthy.get();
    }

    @Override
    public boolean isHealthy() {
        return healthy.get();
    }

    @Override
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("name", PROVIDER_NAME);
        stats.put("enabled", enabled);
        stats.put("healthy", healthy.get());
        stats.put("initialized", initialized.get());
        stats.put("host", host);
        stats.put("port", port);
        stats.put("database", database);
        stats.put("collectionName", collectionName);
        stats.put("dimension", dimension);
        stats.put("indexType", indexType);
        stats.put("metricType", metricType);
        stats.put("documentCount", count());
        stats.put("successfulOperations", successfulOperations.get());
        stats.put("failedOperations", failedOperations.get());
        return stats;
    }

    @Override
    public Optional<Object> getNativeClient() {
        return Optional.ofNullable(client);
    }

    private List<Float> toFloatList(float[] array) {
        List<Float> list = new ArrayList<>(array.length);
        for (float f : array) {
            list.add(f);
        }
        return list;
    }
}