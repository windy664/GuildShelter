package org.windy.guildshelter.domain.port.ui;

import java.util.UUID;

/**
 * 平台中立的菜单观看者句柄：只暴露 UUID + 名字，<b>不</b>暴露 {@code org.bukkit.entity.Player}。
 *
 * <p>这样 {@link UiBackend}/{@link UiActionRouter} 的签名不依赖任何平台玩家类型——
 * 原版后端从 {@code Bukkit.getPlayer(id())} 取回 Player，模组后端从其会话取回玩家，互不污染。
 *
 * @param id   玩家 UUID。
 * @param name 玩家名（仅用于显示/日志）。
 */
public record UiViewer(UUID id, String name) {
}
