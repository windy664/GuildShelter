package org.windy.guildshelter.domain.model;

/**
 * 公会营地的一个自定义传送点（成员点 / 访客点）。位置随营地世界（{@code gw.worldName()}），
 * 故只存坐标 + 朝向，不带世界引用。未设置时各命令回退到主城安全出生点（{@code WorldManager.safeSpawn}）。
 */
public record CampSpawn(double x, double y, double z, float yaw, float pitch) {
}
