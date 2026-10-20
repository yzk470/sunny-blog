package com.sunnyblog.rag;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.commands.ProtocolCommand;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RagRedisStore {

    private static final ProtocolCommand VADD = () -> "VADD".getBytes(StandardCharsets.UTF_8);
    private static final ProtocolCommand VSIM = () -> "VSIM".getBytes(StandardCharsets.UTF_8);
    private static final ProtocolCommand VREM = () -> "VREM".getBytes(StandardCharsets.UTF_8);

    private final RagProperties properties;

    public record Hit(String chunkId, double score) {
    }

    public void addVector(String databaseId, String chunkId, List<Double> vector) {
        byte[] key = bytes(vectorKey(databaseId));
        byte[] vectorBytes = toBytes(vector);
        try (Jedis jedis = connect()) {
            jedis.sendCommand(VADD,
                    key,
                    bytes("FP32"),
                    vectorBytes,
                    bytes(chunkId));
        }
    }

    public List<Hit> search(String databaseId, List<Double> vector, int count) {
        try (Jedis jedis = connect()) {
            Object raw = jedis.sendCommand(VSIM,
                    bytes(vectorKey(databaseId)),
                    bytes("FP32"),
                    toBytes(vector),
                    bytes("COUNT"),
                    bytes(String.valueOf(count)),
                    bytes("WITHSCORES"));
            return parseHits(raw);
        }
    }

    public void removeVector(String databaseId, String chunkId) {
        try (Jedis jedis = connect()) {
            jedis.sendCommand(VREM, bytes(vectorKey(databaseId)), bytes(chunkId));
        }
    }

    public void deleteVectorSet(String databaseId) {
        try (Jedis jedis = connect()) {
            jedis.del(vectorKey(databaseId));
        }
    }

    public void saveChunk(String databaseId, ChunkMeta meta) {
        try (Jedis jedis = connect()) {
            String key = chunkKey(databaseId, meta.chunkId());
            Map<String, String> map = new LinkedHashMap<>();
            map.put("chunkId", meta.chunkId());
            map.put("fileId", meta.fileId());
            map.put("source", meta.source());
            map.put("content", meta.content());
            map.put("index", String.valueOf(meta.index()));
            map.put("databaseId", databaseId);
            jedis.hset(key, map);
            jedis.sadd(dbChunksKey(databaseId), meta.chunkId());
            jedis.sadd(fileChunksKey(meta.fileId()), meta.chunkId());
        }
    }

    public ChunkMeta getChunk(String databaseId, String chunkId) {
        try (Jedis jedis = connect()) {
            Map<String, String> map = jedis.hgetAll(chunkKey(databaseId, chunkId));
            if (map.isEmpty()) return null;
            return new ChunkMeta(
                    map.get("chunkId"),
                    map.get("fileId"),
                    map.get("source"),
                    map.get("content"),
                    Integer.parseInt(map.getOrDefault("index", "0")),
                    map.getOrDefault("databaseId", databaseId)
            );
        }
    }

    public Set<String> fileChunks(String fileId) {
        try (Jedis jedis = connect()) {
            return jedis.smembers(fileChunksKey(fileId));
        }
    }

    public Set<String> dbChunks(String databaseId) {
        try (Jedis jedis = connect()) {
            return jedis.smembers(dbChunksKey(databaseId));
        }
    }

    public void removeChunkRecords(String databaseId, String chunkId, String fileId) {
        try (Jedis jedis = connect()) {
            jedis.del(chunkKey(databaseId, chunkId));
            jedis.srem(dbChunksKey(databaseId), chunkId);
            if (fileId != null) {
                jedis.srem(fileChunksKey(fileId), chunkId);
            }
        }
    }

    public void removeFileSet(String fileId) {
        try (Jedis jedis = connect()) {
            jedis.del(fileChunksKey(fileId));
        }
    }

    public void clearDatabase(String databaseId) {
        Set<String> chunks = dbChunks(databaseId);
        for (String chunk : chunks) {
            removeVector(databaseId, chunk);
        }
        try (Jedis jedis = connect()) {
            for (String chunk : chunks) {
                jedis.del(chunkKey(databaseId, chunk));
            }
            jedis.del(dbChunksKey(databaseId));
            jedis.del(vectorKey(databaseId));
        }
    }

    private List<Hit> parseHits(Object raw) {
        List<Hit> hits = new ArrayList<>();
        if (!(raw instanceof List<?> list)) return hits;
        for (int i = 0; i + 1 < list.size(); i += 2) {
            String id = asString(list.get(i));
            double score = asDouble(list.get(i + 1));
            if (id != null) hits.add(new Hit(id, score));
        }
        return hits;
    }

    private String asString(Object value) {
        if (value == null) return null;
        if (value instanceof byte[] bytes) return new String(bytes, StandardCharsets.UTF_8);
        return String.valueOf(value);
    }

    private double asDouble(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        try {
            return Double.parseDouble(asString(value));
        } catch (Exception ex) {
            return 0;
        }
    }

    private byte[] toBytes(List<Double> vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.size() * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (Double value : vector) buffer.putFloat(value.floatValue());
        return buffer.array();
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private String vectorKey(String databaseId) {
        return "rag:vs:" + databaseId;
    }

    private String chunkKey(String databaseId, String chunkId) {
        return "rag:chunk:" + databaseId + ":" + chunkId;
    }

    private String dbChunksKey(String databaseId) {
        return "rag:db:" + databaseId + ":chunks";
    }

    private String fileChunksKey(String fileId) {
        return "rag:file:" + fileId + ":chunks";
    }

    private Jedis connect() {
        Jedis jedis = new Jedis(properties.getRedisHost(), properties.getRedisPort(), 10000);
        jedis.ping();
        return jedis;
    }

    public record ChunkMeta(String chunkId, String fileId, String source, String content,
                            int index, String databaseId) {
    }
}
