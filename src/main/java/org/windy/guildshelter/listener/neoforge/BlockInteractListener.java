package org.windy.guildshelter.listener.neoforge;

import com.bekvon.bukkit.residence.Residence;
import com.bekvon.bukkit.residence.protection.ClaimedResidence;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.windy.guildshelter.util.PlayerUtils;

public class BlockInteractListener {

    private static final Logger LOGGER = LogManager.getLogger();


    // 左键点击方块事件
    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent.LeftClickBlock event) {
        net.minecraft.world.entity.player.Player forgePlayer = event.getEntity();
        String playerName = forgePlayer.getName().getString();

        // 将 Forge 玩家对象转换为 Bukkit 玩家对象
        org.bukkit.entity.Player bukkitPlayer = PlayerUtils.getBukkitPlayer(forgePlayer);


        // 如果 Player 不存在，直接返回
        if (bukkitPlayer == null) {
            LOGGER.info("Player " + playerName + " not found.");
            return;
        }

        // 检查实体的名字是否包含特定的子字符串
        if (playerName.contains("AS-FAKEPLAYER") ||
                playerName.contains("[MINECRAFT]") ||
                playerName.contains("[MEKANISM]") ||
                playerName.contains("[IF]") ||
                playerName.contains("[IntegratedTunnels]") ||
                playerName.contains("KILLER JOE") ||
                playerName.contains("[DEPOLYER]") ||
                playerName.contains("[XU2FAKEPLAYER]") ||
                playerName.contains("[MODULAR ROUTERS]")) {
            // 如果玩家名字匹配条件，返回，不处理事件
            LOGGER.info("假玩家" + playerName + " 触发了 PlayerInteractEvent");
            return;
        }

        // 获取领地管理器并根据玩家位置获取对应的领地
        ClaimedResidence residence = Residence.getInstance().getResidenceManager().getByLoc(bukkitPlayer);

        // 如果当前位置不在任何领地内，则不进行任何操作
        if (residence == null) {
            return;
        }

        // 检查玩家是否可以在该领地内使用方块
        if (residence.getPermissions().playerHas(playerName, "use", true)) {
            return;
        }

        // 如果权限不足，取消事件
        event.setCanceled(true);
    }


    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent.RightClickBlock event) {
        LOGGER.info("PlayerInteractEvent 触发");
    }
    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent.EntityInteract event) {
        LOGGER.info("PlayerInteractEvent 触发");
    }
}
