package org.windy.guildshelter.persistence;

import org.windy.guildshelter.domain.layout.LayoutConfig;

import java.nio.file.Path;
import java.util.Locale;

/** 按配置(storage.type)选择并构建存储后端：sqlite | mysql | flatfile。 */
public final class StorageFactory {

    private StorageFactory() {
    }

    /**
     * @param settings      存储配置
     * @param dataFolder    插件数据目录（sqlite 的 db 文件 / flatfile 的数据文件放这里）
     * @param fallbackLayout 老数据缺布局快照时回退（一般传当前 config 布局）
     */
    public static Storage create(StorageSettings settings, Path dataFolder, LayoutConfig fallbackLayout) {
        String type = settings.type() == null ? "sqlite" : settings.type().trim().toLowerCase(Locale.ROOT);
        return switch (type) {
            case "mysql" -> mysql(settings, fallbackLayout);
            case "flatfile", "file", "yaml" -> new FlatFileStorage(dataFolder.resolve("data"), fallbackLayout);
            default -> sqlite(dataFolder, fallbackLayout); // sqlite 兜底
        };
    }

    private static Storage sqlite(Path dataFolder, LayoutConfig fallbackLayout) {
        String url = "jdbc:sqlite:" + dataFolder.resolve("database.db").toString().replace('\\', '/');
        SqlDialect dialect = new SqliteDialect();
        JdbcDatabase db = new JdbcDatabase(url, null, null, null, "PRAGMA busy_timeout=5000", dialect);
        return new JdbcStorage(db, dialect, fallbackLayout);
    }

    private static Storage mysql(StorageSettings s, LayoutConfig fallbackLayout) {
        String url = "jdbc:mysql://" + s.mysqlHost() + ":" + s.mysqlPort() + "/" + s.mysqlDatabase()
                + "?useSSL=false&characterEncoding=utf8&serverTimezone=UTC&allowPublicKeyRetrieval=true";
        SqlDialect dialect = new MysqlDialect();
        JdbcDatabase db = new JdbcDatabase(url, "com.mysql.cj.jdbc.Driver",
                s.mysqlUser(), s.mysqlPassword(), null, dialect);
        return new JdbcStorage(db, dialect, fallbackLayout);
    }
}
