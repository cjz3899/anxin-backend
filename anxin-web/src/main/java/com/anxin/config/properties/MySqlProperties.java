package com.anxin.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MySQL 配置（对应 yml 中 anxin.mysql 前缀）
 */
@ConfigurationProperties(prefix = "anxin.mysql")
public record MySqlProperties(
        String ip,
        Integer port,
        String db,
        String username,
        String password) {

    /**
     * 组装 JDBC URL
     */
    public String getUrl() {
        return "jdbc:mysql://" + ip + ":" + port + "/" + db
                + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai"
                + "&useSSL=false&allowPublicKeyRetrieval=true";
    }
}
