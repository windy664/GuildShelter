package org.windy.guildshelter.adapter.provider;

import com.github.shetuan.ShetuanPlugin;
import com.github.shetuan.manager.ClubManager;
import com.github.shetuan.manager.MemberManager;
import com.github.shetuan.model.Club;
import com.github.shetuan.model.ClubMember;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * 自造的 shetuan 接入轮子。shetuan 把 ClubManager / MemberManager 设成 private 字段且无 getter，
 * 这里用反射取出来，对外只暴露 GuildShelter 需要的几个查询方法。
 *
 * <p>反射只用于"读字段"，且<b>每次调用都重读</b>——这样 shetuan 被 {@code /reload} 重建管理器后
 * 我们也能拿到最新实例，不会握着失效引用。读到管理器后都是对 shetuan 公开模型（{@link Club}/
 * {@link ClubMember}）与公开方法的强类型调用。
 *
 * <p>shetuan 若改字段名，会让 {@link #tryCreate} 返回 {@code null}（记一条 warning），不拖垮本插件。
 */
public final class ShetuanAccess {

    private final ShetuanPlugin plugin;
    private final Field clubManagerField;
    private final Field memberManagerField;

    private ShetuanAccess(ShetuanPlugin plugin, Field clubManagerField, Field memberManagerField) {
        this.plugin = plugin;
        this.clubManagerField = clubManagerField;
        this.memberManagerField = memberManagerField;
    }

    /** 取不到（插件没装 / 版本改了字段名 / 管理器尚未初始化）则返回 {@code null}。 */
    public static ShetuanAccess tryCreate(PluginManager plugins, Logger logger) {
        Plugin plugin = plugins.getPlugin("Shetuan");
        if (!(plugin instanceof ShetuanPlugin sp)) {
            return null;
        }
        try {
            Field cmf = ShetuanPlugin.class.getDeclaredField("clubManager");
            Field mmf = ShetuanPlugin.class.getDeclaredField("memberManager");
            cmf.setAccessible(true);
            mmf.setAccessible(true);
            if (cmf.get(sp) == null || mmf.get(sp) == null) {
                logger.warning("[GuildShelter] Shetuan 管理器尚未初始化，社团同步暂不可用。");
                return null;
            }
            return new ShetuanAccess(sp, cmf, mmf);
        } catch (ReflectiveOperationException e) {
            logger.warning("[GuildShelter] 反射读取 Shetuan 管理器失败（版本可能已变）：" + e.getMessage());
            return null;
        }
    }

    public List<Club> allClubs() {
        ClubManager cm = clubManager();
        return cm == null ? List.of() : cm.allClubs();
    }

    /** 玩家所在社团 id（不在任何社团则空）。 */
    public Optional<UUID> clubIdOf(UUID player) {
        ClubManager cm = clubManager();
        return cm == null ? Optional.empty() : cm.getClubIdByMember(player);
    }

    public Optional<Club> clubById(UUID clubId) {
        ClubManager cm = clubManager();
        return cm == null ? Optional.empty() : cm.getClub(clubId);
    }

    public List<ClubMember> membersOf(UUID clubId) {
        MemberManager mm = memberManager();
        return mm == null ? List.of() : mm.membersOf(clubId);
    }

    /** 社团展示名（含颜色 token，仅用于展示/日志）。 */
    public String displayName(Club club) {
        ClubManager cm = clubManager();
        return cm == null ? club.namePlain() : cm.formatClubName(club);
    }

    private ClubManager clubManager() {
        try {
            return (ClubManager) clubManagerField.get(plugin);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    private MemberManager memberManager() {
        try {
            return (MemberManager) memberManagerField.get(plugin);
        } catch (IllegalAccessException e) {
            return null;
        }
    }
}
