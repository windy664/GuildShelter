package org.windy.guildshelter.adapter.bukkit;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.windy.guildshelter.domain.model.PlayerRef;
import org.windy.guildshelter.domain.port.EconomyPort;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * Vault Economy 的 Bukkit 适配器。运行时检测 Vault 是否存在并已注册经济实现。
 * 不存在时 {@link #tryCreate} 返回 null，price flag 不生效。
 */
public final class VaultEconomy implements EconomyPort {

    private final Economy economy;

    private VaultEconomy(Economy economy) {
        this.economy = economy;
    }

    /**
     * 尝试创建：Vault 插件存在 + 已注册 Economy 实现 → 返回实例；否则返回 null。
     */
    public static VaultEconomy tryCreate(Logger logger) {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return null;
        }
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            logger.warning("Vault 已安装但未检测到经济插件（如 CMI/XConomy），price flag 不生效。");
            return null;
        }
        logger.info("Vault 经济已对接: " + rsp.getProvider().getName());
        return new VaultEconomy(rsp.getProvider());
    }

    @Override
    public boolean has(PlayerRef player, double amount) {
        return economy.has(offline(player), amount);
    }

    @Override
    public boolean withdraw(PlayerRef player, double amount) {
        EconomyResponse r = economy.withdrawPlayer(offline(player), amount);
        return r.transactionSuccess();
    }

    @Override
    public void deposit(PlayerRef player, double amount) {
        economy.depositPlayer(offline(player), amount);
    }

    @Override
    public String format(double amount) {
        return economy.format(amount);
    }

    private static OfflinePlayer offline(PlayerRef ref) {
        return Bukkit.getOfflinePlayer(ref.uuid());
    }
}
