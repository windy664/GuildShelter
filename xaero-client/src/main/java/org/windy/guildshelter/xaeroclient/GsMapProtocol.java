package org.windy.guildshelter.xaeroclient;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * {@code guildshelter:map} 字节协议的客户端编解码（纯 Java，与服务端 MapClaimChannel 严格对齐，见 PLAN_XAERO.md §3）。
 * 把网络收发与 NeoForge API 解耦：解析只动 {@link GsMapClaimData}，结果文本回传给调用方显示。
 */
public final class GsMapProtocol {

    // S→C
    public static final byte PLOTS = 0x01;
    public static final byte CLEAR = 0x02;
    public static final byte RESULT = 0x20;
    // C→S
    public static final byte CLAIM = 0x10;

    private GsMapProtocol() {}

    /**
     * 处理一个服务端下行包。PLOTS/CLEAR 直接更新 {@link GsMapClaimData}；RESULT 返回要显示的文本（否则 null）。
     */
    public static String handleIncoming(byte[] bytes) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            byte type = in.readByte();
            switch (type) {
                case PLOTS -> {
                    String dim = in.readUTF();
                    in.readInt(); // originChunkX（客户端用世界 chunk 绝对坐标，origin 仅备用）
                    in.readInt();
                    int count = in.readInt();
                    Map<Long, GsMapClaimData.Entry> entries = new HashMap<>();
                    for (int i = 0; i < count; i++) {
                        int cx = in.readInt();
                        int cz = in.readInt();
                        int argb = in.readInt();
                        byte kind = in.readByte();
                        String label = in.readUTF();
                        entries.put(GsMapClaimData.key(cx, cz), new GsMapClaimData.Entry(argb, kind, label));
                    }
                    GsMapClaimData.get().replaceDimension(dim, entries);
                    GuildShelterXaeroClient.LOGGER.info("[gsmap] PLOTS parsed: dim={}, {} entries", dim, entries.size());
                    return null;
                }
                case CLEAR -> {
                    GsMapClaimData.get().clearDimension(in.readUTF());
                    return null;
                }
                case RESULT -> {
                    in.readByte(); // ok 标志（v1 文本已含色，不另用）
                    return in.readUTF();
                }
                default -> {
                    return null;
                }
            }
        } catch (IOException e) {
            return null;
        }
    }

    /** 编码一个圈地点击请求（C→S CLAIM）。 */
    public static byte[] encodeClaim(int chunkX, int chunkZ) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bos)) {
            out.writeByte(CLAIM);
            out.writeInt(chunkX);
            out.writeInt(chunkZ);
        } catch (IOException ignored) {
            // ByteArrayOutputStream 不会抛
        }
        return bos.toByteArray();
    }
}
