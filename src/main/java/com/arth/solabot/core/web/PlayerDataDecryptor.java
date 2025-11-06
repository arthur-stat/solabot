package com.arth.solabot.core.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.value.Value;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.util.*;


public final class PlayerDataDecryptor {

    public enum Region {JP, CN, TW, KR, EN}

    // EN key/iv (hex/escaped bytes converted)
    private static final byte[] EN_KEY = new byte[]{
            (byte) 0xDF, 0x38, 0x42, 0x14, (byte) 0xB2, (byte) 0x9A, 0x3A, (byte) 0xDF,
            (byte) 0xBF, 0x1B, (byte) 0xD9, (byte) 0xEE, 0x5B, 0x16, (byte) 0xF8, (byte) 0x84
    };
    private static final byte[] EN_IV = new byte[]{
            0x7E, (byte) 0x85, 0x6C, (byte) 0x90, 0x79, (byte) 0x87, (byte) 0xF8, (byte) 0xAE,
            (byte) 0xC6, (byte) 0xAF, (byte) 0xC0, (byte) 0xC5, 0x47, 0x38, (byte) 0xFC, 0x7E
    };

    // common key/iv for jp/tw/kr/cn
    private static final byte[] COMMON_KEY = "g2fcC0ZczN9MTJ61".getBytes(); // 16 bytes ASCII
    private static final byte[] COMMON_IV = "msx3IV0i9XE5uYZ1".getBytes();  // 16 bytes ASCII

    private static final Map<Region, KeySet> KEYSETS = new EnumMap<>(Region.class);

    static {
        KEYSETS.put(Region.EN, new KeySet(EN_KEY, EN_IV));
        KEYSETS.put(Region.JP, new KeySet(COMMON_KEY, COMMON_IV));
        KEYSETS.put(Region.TW, new KeySet(COMMON_KEY, COMMON_IV));
        KEYSETS.put(Region.KR, new KeySet(COMMON_KEY, COMMON_IV));
        KEYSETS.put(Region.CN, new KeySet(COMMON_KEY, COMMON_IV));
    }

    private static final class KeySet {
        final byte[] key;
        final byte[] iv;

        KeySet(byte[] key, byte[] iv) {
            this.key = key;
            this.iv = iv;
        }
    }

    private final Region region;
    private byte[] plainBytes;
    private ObjectMapper objectMapper;

    private PlayerDataDecryptor(Region region) {
        this.region = Objects.requireNonNull(region, "region");
    }

    /**
     * 静态工厂：按 region 创建实例
     */
    public static PlayerDataDecryptor forRegion(ObjectMapper objectMapper, Region region) {
        PlayerDataDecryptor builder = new PlayerDataDecryptor(region);
        builder.objectMapper = objectMapper;
        return builder;
    }

    /**
     * 注入 Spring 管理的 ObjectMapper。
     * 推荐在调用 decrypt() 前注入。
     */
    public PlayerDataDecryptor withObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
        return this;
    }

    /**
     * 解密原始 cipher bytes（AES/CBC/PKCS5Padding），返回 this 以便链式调用。
     * 假定 IV 固定（来自 region 的 keyset），与示例 Python 代码保持一致。
     *
     * @param cipherBody 原始加密数据 bytes（非 Base64 编码）
     * @return this
     * @throws Exception 发生解密或填充错误时抛出
     */
    public PlayerDataDecryptor decrypt(byte[] cipherBody) throws Exception {
        if (cipherBody == null) throw new IllegalArgumentException("cipherBody == null");
        KeySet ks = KEYSETS.get(region);
        if (ks == null) throw new IllegalStateException("Missing keyset for region: " + region);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        SecretKeySpec sk = new SecretKeySpec(ks.key, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(ks.iv);
        cipher.init(Cipher.DECRYPT_MODE, sk, ivSpec);
        this.plainBytes = cipher.doFinal(cipherBody);
        return this;
    }

    /**
     * 便捷：接受 Base64 编码的密文字符串，解码后再执行 decrypt(byte[]).
     */
    public PlayerDataDecryptor decryptFromBase64(String base64Cipher) throws Exception {
        if (base64Cipher == null) throw new IllegalArgumentException("base64Cipher == null");
        byte[] cipherBytes = Base64.getDecoder().decode(base64Cipher);
        return decrypt(cipherBytes);
    }

    /**
     * 将解密后的 MessagePack bytes 解析为 JsonNode
     *
     * @return JsonNode 表示的 JSON 树
     * @throws Exception 若尚未解密或解析失败
     */
    public JsonNode toJsonNode() throws Exception {
        if (plainBytes == null) throw new IllegalStateException("no plaintext available. Call decrypt(...) first.");
        ObjectMapper mapper = this.objectMapper != null ? this.objectMapper : new ObjectMapper();
        Object javaObj = msgpackToObject(plainBytes);
        return mapper.valueToTree(javaObj);
    }

    /**
     * 获取解密后的 MessagePack 原始 bytes（防止外部修改，返回拷贝）
     */
    public byte[] getPlainBytes() {
        return plainBytes == null ? null : Arrays.copyOf(plainBytes, plainBytes.length);
    }

    /**
     * 重置以便重复使用实例
     */
    public PlayerDataDecryptor reset() {
        this.plainBytes = null;
        return this;
    }

    // ===== MessagePack -> Java 原生对象（Map/List/primitive） =====
    private Object msgpackToObject(byte[] msgpackBytes) throws Exception {
        try (MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(msgpackBytes)) {
            if (!unpacker.hasNext()) return null;
            Value v = unpacker.unpackValue();
            return convertValue(v);
        }
    }

    private Object convertValue(Value v) {
        switch (v.getValueType()) {
            case MAP:
                Map<Object, Object> map = new LinkedHashMap<>();
                for (var entry : v.asMapValue().entrySet()) {
                    Object key = convertValue(entry.getKey());
                    Object val = convertValue(entry.getValue());
                    map.put(key, val);
                }
                return map;
            case ARRAY:
                List<Object> list = new ArrayList<>();
                for (Value e : v.asArrayValue()) list.add(convertValue(e));
                return list;
            case STRING:
                return v.asStringValue().asString();
            case BINARY:
                // 二进制转 Base64 字符串以保证 JSON 可序列化
                return Base64.getEncoder().encodeToString(v.asBinaryValue().asByteArray());
            case INTEGER:
                // 优先 long
                try {
                    return v.asIntegerValue().toLong();
                } catch (Exception ex) {
                    return v.toString();
                }
            case FLOAT:
                return v.asFloatValue().toDouble();
            case BOOLEAN:
                return v.asBooleanValue().getBoolean();
            case NIL:
                return null;
            default:
                return v.toString();
        }
    }

//    public static void main(String[] args) throws Exception {
//        String path = "C:\\Users\\asheo\\Desktop\\工作区\\mysuite.bin";
//        byte[] cipherBytes = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path));
//        ObjectMapper mapper = new ObjectMapper();
//        Decryptor.Region region = Decryptor.Region.JP;
//
//        JsonNode node = Decryptor.forRegion(region)
//                .withObjectMapper(mapper)
//                .decrypt(cipherBytes)
//                .toJsonNode();
//
//        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node));
//    }
}
