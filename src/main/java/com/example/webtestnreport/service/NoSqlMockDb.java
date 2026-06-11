package com.example.webtestnreport.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.*;

@Service
public class NoSqlMockDb {
    private final ObjectMapper mapper = new ObjectMapper();
    private final String dbDirectory = "./data/nosql";

    public NoSqlMockDb() {
        File dir = new File(dbDirectory);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    private File getCollectionFile(String collection) {
        return new File(dbDirectory, collection + ".json");
    }

    private synchronized ArrayNode readCollection(String collection) {
        File file = getCollectionFile(collection);
        if (!file.exists()) {
            return mapper.createArrayNode();
        }
        try {
            JsonNode node = mapper.readTree(file);
            if (node.isArray()) {
                return (ArrayNode) node;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return mapper.createArrayNode();
    }

    private synchronized void writeCollection(String collection, ArrayNode arrayNode) {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(getCollectionFile(collection), arrayNode);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized Map<String, Object> insert(String collection, String jsonDoc) {
        try {
            JsonNode docNode = mapper.readTree(jsonDoc);
            if (!docNode.isObject()) {
                throw new IllegalArgumentException("NoSQL document must be a JSON object");
            }
            ObjectNode objNode = (ObjectNode) docNode;
            if (!objNode.has("_id")) {
                objNode.put("_id", UUID.randomUUID().toString());
            }
            
            ArrayNode arrayNode = readCollection(collection);
            arrayNode.add(objNode);
            writeCollection(collection, arrayNode);
            
            return mapper.convertValue(objNode, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to insert document: " + e.getMessage(), e);
        }
    }

    public synchronized List<Map<String, Object>> find(String collection, String queryJson) {
        try {
            JsonNode queryNode = mapper.readTree(queryJson);
            ArrayNode arrayNode = readCollection(collection);
            List<Map<String, Object>> results = new ArrayList<>();
            
            for (JsonNode doc : arrayNode) {
                if (matches(doc, queryNode)) {
                    results.add(mapper.convertValue(doc, new TypeReference<Map<String, Object>>() {}));
                }
            }
            return results;
        } catch (Exception e) {
            throw new RuntimeException("Failed to query collection: " + e.getMessage(), e);
        }
    }

    public synchronized long delete(String collection, String queryJson) {
        try {
            JsonNode queryNode = mapper.readTree(queryJson);
            ArrayNode arrayNode = readCollection(collection);
            ArrayNode newArrayNode = mapper.createArrayNode();
            long deletedCount = 0;
            
            for (JsonNode doc : arrayNode) {
                if (matches(doc, queryNode)) {
                    deletedCount++;
                } else {
                    newArrayNode.add(doc);
                }
            }
            if (deletedCount > 0) {
                writeCollection(collection, newArrayNode);
            }
            return deletedCount;
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete from collection: " + e.getMessage(), e);
        }
    }

    private boolean matches(JsonNode doc, JsonNode query) {
        if (query.isNull() || query.isEmpty()) {
            return true; // Empty query matches all
        }
        if (!query.isObject() || !doc.isObject()) {
            return false;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = query.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String key = field.getKey();
            JsonNode queryVal = field.getValue();
            if (!doc.has(key)) {
                return false;
            }
            JsonNode docVal = doc.get(key);
            if (!docVal.equals(queryVal)) {
                return false;
            }
        }
        return true;
    }
}
