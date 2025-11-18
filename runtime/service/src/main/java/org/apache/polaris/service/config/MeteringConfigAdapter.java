package org.apache.polaris.service.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import java.util.Optional;
import org.apache.polaris.core.metering.MeteringConfig;

/**
 * Adapter to convert Quarkus MeteringConfiguration to the simple MeteringConfig interface.
 */
@ApplicationScoped
public class MeteringConfigAdapter {
  
  @Inject
  MeteringConfiguration quarkusConfig;
  
  @Produces
  @ApplicationScoped
  public MeteringConfig meteringConfig() {
    return new MeteringConfig() {
      @Override
      public boolean enabled() {
        return quarkusConfig.enabled();
      }
      
      @Override
      public String apiBaseUrl() {
        return quarkusConfig.apiBaseUrl();
      }
      
      @Override
      public Optional<String> apiKey() {
        return quarkusConfig.apiKey();
      }
      
      @Override
      public int timeoutMs() {
        return quarkusConfig.timeoutMs();
      }
      
      @Override
      public int maxRetries() {
        return quarkusConfig.maxRetries();
      }
      
      @Override
      public double minimumTokensRequired() {
        return quarkusConfig.minimumTokensRequired();
      }
    };
  }
}

