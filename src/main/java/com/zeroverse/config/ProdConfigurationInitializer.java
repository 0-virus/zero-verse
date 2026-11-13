package com.zeroverse.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/** Runs the production configuration boundary before any datasource or Flyway bean is created. */
public final class ProdConfigurationInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        ProdConfigurationValidator.validateEnvironment(applicationContext.getEnvironment());
    }
}
