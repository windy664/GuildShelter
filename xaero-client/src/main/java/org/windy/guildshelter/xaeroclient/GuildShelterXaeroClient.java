package org.windy.guildshelter.xaeroclient;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import xaero.map.highlight.HighlighterRegistry;

/**
 * GuildShelter Xaero 圈地客户端 mod 入口（PLAN_XAERO.md Phase 2，仅客户端）。
 *
 * <p>三件事：①注册 {@code guildshelter:map} 自定义载荷收发；②把 {@link GsMapHighlighter} 注册进 Xaero 地图
 * （经 {@code GsWorldMapSessionMixin} 在 Xaero 锁列表前调 {@link #registerHighlighters}——Xaero 无第三方注册事件，
 * 参考 SathLabs/FTB-Xaero-Compat 的 mixin 做法）；③地图 Shift+左键 → 发 CLAIM（{@link GsMapClaimInput}）。
 */
@Mod(value = "gsmap", dist = Dist.CLIENT)
public final class GuildShelterXaeroClient {

    public static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("gsmap");

    /** 唯一的 highlighter 实例（由 mixin 在 Xaero init 期间注册进每个会话的 registry）。 */
    private static final GsMapHighlighter HIGHLIGHTER = new GsMapHighlighter();

    /** 由 {@code GsWorldMapSessionMixin} 在 {@code HighlighterRegistry.end()} 之前调用——此刻列表仍可写。 */
    public static void registerHighlighters(HighlighterRegistry registry) {
        registry.register(HIGHLIGHTER);
        LOGGER.info("[gsmap] highlighter registered into Xaero HighlighterRegistry");
    }

    /** 通道 = 服务端 Bukkit 插件消息通道 "guildshelter:map"。 */
    public static final CustomPacketPayload.Type<MapPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("guildshelter", "map"));

    public static final StreamCodec<FriendlyByteBuf, MapPayload> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeBytes(payload.data),
            buf -> {
                byte[] b = new byte[buf.readableBytes()];
                buf.readBytes(b);
                return new MapPayload(b);
            });

    /** 裹住原始字节的载荷（内容 = 服务端写的协议字节，解析交 {@link GsMapProtocol}）。 */
    public record MapPayload(byte[] data) implements CustomPacketPayload {
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public GuildShelterXaeroClient(IEventBus modBus) {
        modBus.addListener(this::registerPayloads);
        NeoForge.EVENT_BUS.addListener(GsMapClaimInput::onMousePressed); // Shift+左键地图圈地
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        // optional()：与非 NeoForge（Bukkit/Youer）服务端通信，不强制服务端协商本通道。
        // playToClient：只注册【客户端收】下行包（PLOTS/CLEAR/RESULT）——地图高亮的核心。单向单 handler，
        // 不会"已注册"冲突，也不缺客户端处理器。上行 CLAIM 走 vanilla ServerboundCustomPayloadPacket（见 sendClaim），
        // 该包用同一 TYPE+CODEC 编码，通道 id=guildshelter:map 直达 Bukkit 服务端的 PluginMessageListener。
        event.registrar("1").optional().playToClient(TYPE, CODEC, this::onClient);
    }

    /** 客户端收到服务端下行包：解析更新高亮 / 显示圈地结果。 */
    private void onClient(MapPayload payload, IPayloadContext ctx) {
        byte[] data = payload.data();
        LOGGER.info("[gsmap] received guildshelter:map payload: {} bytes, type={}",
                data.length, data.length > 0 ? data[0] : -1);
        ctx.enqueueWork(() -> {
            String result = GsMapProtocol.handleIncoming(data);
            if (result != null && Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.sendOverlayMessage(Component.literal(result)); // actionbar
            }
        });
    }

    /** 供地图点击输入（Phase 3）调用：请求圈占某 chunk。走 vanilla 连接发包（避开多变的 PacketDistributor API）。 */
    public static void sendClaim(int chunkX, int chunkZ) {
        var conn = Minecraft.getInstance().getConnection();
        if (conn != null) {
            conn.send(new ServerboundCustomPayloadPacket(new MapPayload(GsMapProtocol.encodeClaim(chunkX, chunkZ))));
        }
    }
}
