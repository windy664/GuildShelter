package org.windy.guildshelter.adapter.bukkit;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.windy.guildshelter.domain.flag.Flag;
import org.windy.guildshelter.domain.model.GuildWorld;
import org.windy.guildshelter.domain.model.Manor;
import org.windy.guildshelter.domain.model.PlayerRef;
import org.windy.guildshelter.domain.port.GuildRepository;
import org.windy.guildshelter.domain.port.ManorRepository;
import org.windy.guildshelter.domain.rule.LevelRules;

/**
 * PlaceholderAPI 扩展：提供地皮相关信息的占位符。
 *
 * <p>可用占位符：
 * <ul>
 *   <li>%guildshelter_guild% — 公会名</li>
 *   <li>%guildshelter_slot% — 地皮 slot 号</li>
 *   <li>%guildshelter_level% — 庄园等级</li>
 *   <li>%guildshelter_max_level% — 庄园最大等级</li>
 *   <li>%guildshelter_guild_level% — 公会等级</li>
 *   <li>%guildshelter_size% — 地皮尺寸（方块）</li>
 *   <li>%guildshelter_alias% — 地皮别名</li>
 *   <li>%guildshelter_description% — 地皮描述</li>
 *   <li>%guildshelter_members% — 成员数</li>
 *   <li>%guildshelter_capacity% — 成员容量</li>
 *   <li>%guildshelter_trusted% — trusted 人数</li>
 *   <li>%guildshelter_denied% — 黑名单人数</li>
 *   <li>%guildshelter_visits% — 访问次数</li>
 *   <li>%guildshelter_rating% — 平均评分</li>
 *   <li>%guildshelter_done% — 是否完工</li>
 * </ul>
 */
public final class GuildShelterPapi extends PlaceholderExpansion {

    private final ManorRepository manors;
    private final GuildRepository guilds;
    private final LevelRules levels;

    public GuildShelterPapi(ManorRepository manors, GuildRepository guilds, LevelRules levels) {
        this.manors = manors;
        this.guilds = guilds;
        this.levels = levels;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "guildshelter";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Windy";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.4";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";
        Manor manor = manors.findByOwnerAnywhere(PlayerRef.of(player.getUniqueId())).orElse(null);
        if (manor == null) return "";
        GuildWorld gw = guilds.find(manor.guild()).orElse(null);

        return switch (params.toLowerCase()) {
            case "guild" -> manor.guild().value();
            case "slot" -> String.valueOf(manor.slot());
            case "level" -> String.valueOf(manor.level());
            case "max_level" -> String.valueOf(levels.manorMaxLevel());
            case "guild_level" -> gw != null ? String.valueOf(gw.guildLevel()) : "?";
            case "size" -> {
                int side = gw != null ? gw.layout().plotChunksByLevel(manor.level()) * 16 : 0;
                yield side + "x" + side;
            }
            case "alias" -> {
                String a = Flag.ALIAS.resolveString(manor.flags());
                yield a.isBlank() ? manor.guild().value() + "#" + manor.slot() : a;
            }
            case "description" -> Flag.DESCRIPTION.resolveString(manor.flags());
            case "members" -> String.valueOf(manors.findAll(manor.guild()).size());
            case "capacity" -> gw != null ? String.valueOf(levels.maxMembers(gw.guildLevel())) : "?";
            case "trusted" -> String.valueOf(manor.coBuilders().size());
            case "denied" -> String.valueOf(manor.denied().size());
            case "visits" -> String.valueOf(manors.getVisitCount(manor.guild(), manor.slot()));
            case "rating" -> {
                double avg = manors.getAverageRating(manor.guild(), manor.slot());
                yield avg > 0 ? String.format("%.1f", avg) : "-";
            }
            case "done" -> Flag.DONE.resolveBool(manor.flags()) ? "✔" : "🔨";
            default -> null;
        };
    }
}
