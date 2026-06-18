package org.windy.guildshelter.domain.port;

import java.nio.file.Path;
import java.util.List;

/**
 * mod 世界数据搬运端口：处理存在世界目录 .dat 文件里的 mod 数据。
 *
 * <p>chunk 级复制只搬 TileEntity NBT，但某些 mod（如 RS2）把实际数据存在
 * 世界目录的 SavedData .dat 文件里，TE 只存 UUID 引用。搬家时需要把对应的
 * .dat 文件也搬过去。
 *
 * <p>每个需要特殊处理的 mod 实现此接口，由 {@link ModDataMoverRegistry} 统一调度。
 */
public interface ModDataMover {

    /** 该 mod 的 mod ID（如 "refinedstorage"）。 */
    String modId();

    /**
     * 把源世界中与搬家区域相关的 mod 数据搬到目标世界。
     *
     * @param srcWorldDir  源世界目录（如 world/guild_old/）
     * @param dstWorldDir  目标世界目录（如 world/guild_new/）
     * @param srcWorldName 源世界名
     * @param dstWorldName 目标世界名
     * @param srcMinCX     源区域最小 chunk X
     * @param srcMinCZ     源区域最小 chunk Z
     * @param srcMaxCX     源区域最大 chunk X
     * @param srcMaxCZ     源区域最大 chunk Z
     * @return 搬运结果描述（空 = 无需处理或成功）
     */
    String moveData(Path srcWorldDir, Path dstWorldDir, String srcWorldName, String dstWorldName,
                    int srcMinCX, int srcMinCZ, int srcMaxCX, int srcMaxCZ);
}
