package com.trustify.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Prefix for messages coming **from server to client**
        config.enableSimpleBroker("/topic", "/queue"); // private message delivery

        // Prefix for messages **from client to server**
        config.setApplicationDestinationPrefixes("/app"); // send message
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws") // react connects here
                .setAllowedOriginPatterns("*") // allow frontend connection
                .withSockJS(); // fallback for browsers without native websocket
    }

}
