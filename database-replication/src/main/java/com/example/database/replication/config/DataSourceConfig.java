package com.example.database.replication.config;

import com.example.database.replication.routing.DataSourceType;
import com.example.database.replication.routing.RoutingDataSource;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.Map;

/**
 * Unified datasource configuration using AbstractRoutingDataSource.
 * Routes between writer and reader datasources based on {@link com.example.database.replication.routing.DataSourceContextHolder}.
 *
 * <p>Default routing is to READER datasource. Use {@link com.example.database.replication.routing.UseWriter}
 * annotation on service methods to route to WRITER datasource.
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        basePackages = "com.example.database.replication.repository",
        entityManagerFactoryRef = "entityManagerFactory",
        transactionManagerRef = "transactionManager"
)
public class DataSourceConfig {

    @Bean
    @ConfigurationProperties("spring.datasource.writer")
    public DataSource writerDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    @Bean
    @ConfigurationProperties("spring.datasource.reader")
    public DataSource readerDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    @Bean
    @Primary
    public DataSource routingDataSource(
            @Qualifier("writerDataSource") DataSource writerDataSource,
            @Qualifier("readerDataSource") DataSource readerDataSource
    ) {
        var routingDataSource = new RoutingDataSource();

        Map<Object, Object> targetDataSources = Map.of(
                DataSourceType.WRITER, writerDataSource,
                DataSourceType.READER, readerDataSource
        );

        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.setDefaultTargetDataSource(readerDataSource);

        return routingDataSource;
    }

    @Bean
    @Primary
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("routingDataSource") DataSource dataSource
    ) {
        var properties = Map.of("hibernate.hbm2ddl.auto", "validate");
        return builder
                .dataSource(dataSource)
                .packages("com.example.database.replication.entity")
                .persistenceUnit("routing")
                .properties(properties)
                .build();
    }

    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(
            @Qualifier("entityManagerFactory") EntityManagerFactory entityManagerFactory
    ) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
