package com.trustify.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {
    // CORS configuration can be added here if needed in the future
//    @Bean
//    public CorsFilter corsFilter() {
//        CorsConfiguration config = new CorsConfiguration();
//        config.setAllowedOrigins(Arrays.asList("http://localhost:3000", "http://localhost:3001","http://localhost:5173", "http://localhost:5174"));
//        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
//       // config.setAllowedHeaders(Arrays.asList("*"));
//        config.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type"));
//        config.setAllowCredentials(true);
//        config.setMaxAge(3600L);
//        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
//        source.registerCorsConfiguration("/**", config);
//
//        return new CorsFilter(source);
//    }
//
//
//    @Bean
//    public CorsConfigurationSource corsConfigurationSource() {
//        CorsConfiguration config = new CorsConfiguration();
//
//        config.addAllowedOrigin("http://localhost:5174");
//        config.setAllowCredentials(true);
//        config.addAllowedHeader("*");
//        config.addAllowedMethod("*");
//
//        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
//        source.registerCorsConfiguration("/**", config);
//
//        return source;
//    }




    // MY PREVIOUS CODE
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        corsConfig.setAllowedOrigins(List.of(
                "http://localhost:3000",
                "http://localhost:5173",
                "http://localhost:5174",
                "http://localhost:3001"

        ));

        //Allow all HTTP methods
        corsConfig.addAllowedMethod("*");

        // Allow all headers
        corsConfig.addAllowedHeader("*");

       // corsConfig.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        //corsConfig.setAllowedHeaders(List.of("*"));
        corsConfig.setAllowCredentials(true);
        //corsConfig.setExposedHeaders(List.of("Content-Type","Authorization"));
       // corsConfig.setAllowedHeaders(List.of("Content-Type","Authorization"));
        corsConfig.setExposedHeaders(List.of("Authorization"));
        corsConfig.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);
        return source;

    }
}
