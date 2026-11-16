package com.broadcastmail.api.supabase;

import com.broadcastmail.api.common.SecurityUtil;
import com.broadcastmail.api.config.EncryptionProperties;
import com.broadcastmail.common.connection.Connection;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
@RequiredArgsConstructor
public class TenantDataSourceFactory {

    private final EncryptionProperties encryptionProperties;

    public DataSource create(Connection connection) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(SupabaseSql.buildJdbcUrl(connection.getProjectRef()));
        config.setUsername(SupabaseSql.READER_ROLE);
        config.setPassword(SecurityUtil.decrypt(connection.getEncryptedCreds(), encryptionProperties.key()));
        config.setConnectionTimeout(3000);
        config.setMaximumPoolSize(1);
        return new HikariDataSource(config);
    }
}
