package com.example.taskmanagement_backend.config;

import org.springframework.boot.web.embedded.tomcat.TomcatConnectorCustomizer;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TomcatConfig {

    /**
     * Customizes Tomcat to allow square brackets in URL parameters
     * This fixes "Invalid character found in the request target" errors
     * when receiving URLs with formats like: ?params[page]=0&params[size]=20
     */
    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatCustomizer() {
        return factory -> {
            factory.addConnectorCustomizers((TomcatConnectorCustomizer) connector -> {
                connector.setProperty("relaxedQueryChars", "[]{}|");
                connector.setProperty("relaxedPathChars", "[]{}|");
            });
        };
    }
}
