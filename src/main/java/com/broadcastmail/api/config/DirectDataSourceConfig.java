package com.broadcastmail.api.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@Configuration
@EnableConfigurationProperties(DirectDataSourceProperties.class)
public class DirectDataSourceConfig {

    // Declaring directDataSource makes Boot skip its own pool, so the main one must be declared too —
    // otherwise JPA and every request share the 2-connection LISTEN pool.
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource dataSource(DataSourceProperties dataSourceProperties) {
        return dataSourceProperties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean(name = "directDataSource")
    public DataSource directDataSource(DirectDataSourceProperties props) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(props.url());
        ds.setUsername(props.username());
        ds.setPassword(props.password());
        ds.setDriverClassName(props.driverClassName());
        if (props.hikari() != null) {
            ds.setMaximumPoolSize(props.hikari().maximumPoolSize());
            ds.setPoolName(props.hikari().poolName());
        }
        return ds;
    }
}
