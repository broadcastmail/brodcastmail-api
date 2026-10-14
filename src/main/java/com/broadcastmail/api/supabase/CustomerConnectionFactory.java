package com.broadcastmail.api.supabase;

import com.broadcastmail.api.common.SecurityUtil;
import com.broadcastmail.api.config.EncryptionProperties;
import com.broadcastmail.common.connection.Connection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.sql.DriverManager;
import java.sql.SQLException;

@Component
@RequiredArgsConstructor
public class CustomerConnectionFactory {

    private final EncryptionProperties encryptionProperties;

    public java.sql.Connection open(Connection connection) throws SQLException {
        String jdbcUrl = SupabaseSql.buildJdbcUrl(connection.getProjectRef());
        String password = SecurityUtil.decrypt(connection.getEncryptedCreds(), encryptionProperties.key());
        return DriverManager.getConnection(jdbcUrl, "broadcastmail_reader", password);
    }
}
