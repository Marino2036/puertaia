package com.marino.puertaia.cloud;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Configuration
public class DataSourceConfig {
    @Bean
    public DataSource dataSource(@Value("${puertaia.database-url}") String raw) throws Exception {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("DATABASE_URL no configurada");
        if (raw.startsWith("jdbc:postgresql://")) {
            HikariDataSource ds = new HikariDataSource();
            ds.setJdbcUrl(raw);
            ds.setMaximumPoolSize(5);
            ds.setMinimumIdle(0);
            return ds;
        }

        String normalized = raw.replaceFirst("^postgres://", "postgresql://");
        URI uri = new URI(normalized);
        String[] ui = uri.getUserInfo() == null ? new String[0] : uri.getUserInfo().split(":", 2);
        String user = ui.length > 0 ? URLDecoder.decode(ui[0], StandardCharsets.UTF_8) : "";
        String pass = ui.length > 1 ? URLDecoder.decode(ui[1], StandardCharsets.UTF_8) : "";
        int port = uri.getPort() > 0 ? uri.getPort() : 5432;
        String db = uri.getPath() == null ? "/neondb" : uri.getPath();
        String query = uri.getQuery();
        if (query == null || query.isBlank()) query = "sslmode=require";
        else if (!query.contains("sslmode=")) query += "&sslmode=require";

        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:postgresql://" + uri.getHost() + ":" + port + db + "?" + query);
        ds.setUsername(user);
        ds.setPassword(pass);
        ds.setMaximumPoolSize(5);
        ds.setMinimumIdle(0);
        ds.setConnectionTimeout(10000);
        return ds;
    }
}
