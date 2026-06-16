package org.windy.guildshelter.persistence;

/**
 * 存储后端配置。{@code type} = sqlite | mysql | flatfile;mysql.* 仅 type=mysql 时用。
 */
public record StorageSettings(String type,
                              String mysqlHost, int mysqlPort, String mysqlDatabase,
                              String mysqlUser, String mysqlPassword) {

    public static StorageSettings defaults() {
        return new StorageSettings("sqlite", "localhost", 3306, "guildshelter", "root", "");
    }
}
