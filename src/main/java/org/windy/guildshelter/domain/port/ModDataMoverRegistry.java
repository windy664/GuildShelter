package org.windy.guildshelter.domain.port;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * mod 数据搬运注册表：管理所有 {@link ModDataMover}，搬家时统一调度。
 *
 * <p>每个需要搬运 .dat 文件的 mod 注册一个 ModDataMover 实现。
 * 搬家时按注册顺序依次调用，任一失败不影响其他 mod。
 */
public final class ModDataMoverRegistry {

    private final List<ModDataMover> movers = new ArrayList<>();

    public void register(ModDataMover mover) {
        movers.add(mover);
    }

    public List<ModDataMover> all() {
        return List.copyOf(movers);
    }

    /**
     * 执行所有 mod 数据搬运。
     *
     * @return 每个 mod 的结果描述
     */
    public List<String> moveAll(Path srcWorldDir, Path dstWorldDir,
                                String srcWorldName, String dstWorldName,
                                int srcMinCX, int srcMinCZ, int srcMaxCX, int srcMaxCZ) {
        List<String> results = new ArrayList<>();
        for (ModDataMover mover : movers) {
            try {
                String result = mover.moveData(srcWorldDir, dstWorldDir, srcWorldName, dstWorldName,
                        srcMinCX, srcMinCZ, srcMaxCX, srcMaxCZ);
                if (result != null && !result.isEmpty()) {
                    results.add(result);
                }
            } catch (Exception e) {
                results.add("§c⚠ " + mover.modId() + " 数据搬运失败: " + e.getMessage());
            }
        }
        return results;
    }
}
