package com.example.taskmanagement_backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

/**
 * Elasticsearch configuration for search functionality
 * Supports both local development and production environments
 */
@Configuration
@EnableElasticsearchRepositories(basePackages = "com.example.taskmanagement_backend.search.repositories")
public class SearchElasticsearchConfig extends ElasticsearchConfiguration {

    @Value("${elasticsearch.host:localhost}")
    private String elasticsearchHost;

    @Value("${elasticsearch.port:9200}")
    private int elasticsearchPort;

    @Value("${elasticsearch.username:}")
    private String username;

    @Value("${elasticsearch.password:}")
    private String password;

    @Value("${elasticsearch.ssl.enabled:false}")
    private boolean sslEnabled;

    @Override
    public ClientConfiguration clientConfiguration() {
        var builder = ClientConfiguration.builder()
                .connectedTo(elasticsearchHost + ":" + elasticsearchPort);

        // Add authentication if credentials are provided
        if (!username.isEmpty() && !password.isEmpty()) {
            builder.withBasicAuth(username, password);
        }

        // Enable SSL if configured
        if (sslEnabled) {
            builder.usingSsl();
        }

        // Connection settings
        builder.withConnectTimeout(10000)
               .withSocketTimeout(60000);

        return builder.build();
    }
}
