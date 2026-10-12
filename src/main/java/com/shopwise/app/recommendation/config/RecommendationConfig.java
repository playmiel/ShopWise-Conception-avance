package com.shopwise.app.recommendation.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Active la configuration du module et le reentrainement planifie du modele. */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(RecommendationProperties.class)
public class RecommendationConfig {
}