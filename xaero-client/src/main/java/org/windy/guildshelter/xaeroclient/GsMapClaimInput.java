package org.windy.guildshelter.xaeroclient;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;

/**
 * 地图点击圈地输入（PLAN_XAERO.md Phase 3）：在 Xaero 世界地图界面 <b>Shift+左键点一个 chunk</b> → 圈占它。
 *
 * <p>不重算屏幕→世界坐标：直接反射读 Xaero 每帧算好的 {@code GuiMap.mouseBlockPosX/Z}（光标下世界方块坐标），
 * {@code >>4} 得 chunk，交 {@link GuildShelterXaeroClient#sendClaim}。服务端裁决后回 actionbar 结果。
 *
 * <p><b>不引用 {@code GuiMap} 类型</b>（它的父类 {@code xaero.lib.client.gui.ScreenBase} 在 Xaero 另一个 lib jar，
 * 编译期没有 → instanceof 会失败）。改用<b>运行时类名匹配 + 反射</b>，运行期玩家装了完整 Xaero 故可用。
 * Shift 用 GLFW 直接探测（MC26 的 {@code Screen.hasShiftDown()} 已不在）。反射失败静默降级、不影响地图操作。
 */
public final class GsMapClaimInput {

    private static final String GUI_MAP = "xaero.map.gui.GuiMap";
    private static boolean reflectTried;
    private static Field mouseX;
    private static Field mouseZ;

    private GsMapClaimInput() {}

    /** NeoForge 屏幕鼠标按下事件（game bus）：命中则发圈地请求并拦截，避免触发地图拖动。 */
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        Screen screen = event.getScreen();
        if (screen == null || !GUI_MAP.equals(screen.getClass().getName())) {
            return; // 仅 Xaero 世界地图界面
        }
        if (event.getButton() != 0 || !shiftDown()) {
            return; // 仅 Shift+左键（避开左键拖动 / 右键菜单）
        }
        initReflect(screen.getClass());
        if (mouseX == null || mouseZ == null) {
            return; // 反射不可用，放行正常地图操作
        }
        try {
            int blockX = mouseX.getInt(screen);
            int blockZ = mouseZ.getInt(screen);
            GuildShelterXaeroClient.sendClaim(blockX >> 4, blockZ >> 4);
            event.setCanceled(true);
        } catch (Throwable ignored) {
            // 反射读取失败：放行
        }
    }

    private static void initReflect(Class<?> guiMapClass) {
        if (reflectTried) {
            return;
        }
        reflectTried = true;
        try {
            mouseX = guiMapClass.getDeclaredField("mouseBlockPosX");
            mouseX.setAccessible(true);
            mouseZ = guiMapClass.getDeclaredField("mouseBlockPosZ");
            mouseZ.setAccessible(true);
        } catch (Throwable t) {
            mouseX = null;
            mouseZ = null;
        }
    }

    private static boolean shiftDown() {
        // MC26：InputConstants.isKeyDown 收 Window 对象（非 long 句柄）；Minecraft.getWindow() 即返回该 Window。
        var window = Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
    }
}
