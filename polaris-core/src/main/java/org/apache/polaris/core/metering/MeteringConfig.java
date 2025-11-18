package org.apache.polaris.core.metering;

import java.util.Optional;

/**
 * Configuration interface for metering integration.
 */
public interface MeteringConfig {
  
  boolean enabled();
  
  String apiBaseUrl();
  
  Optional<String> apiKey();
  
  int timeoutMs();
  
  int maxRetries();
  
  double minimumTokensRequired();
}

