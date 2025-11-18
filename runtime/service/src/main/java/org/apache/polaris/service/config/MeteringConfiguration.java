package org.apache.polaris.service.config;

import io.quarkus.runtime.annotations.StaticInitSafe;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.util.Optional;

/**
 * Configuration for Polaris metering integration with external billing systems.
 * 
 * This configuration enables Polaris to check user token balances before vending
 * AWS credentials, ensuring users have sufficient credits to access data.
 */
@StaticInitSafe
@ConfigMapping(prefix = "polaris.metering")
public interface MeteringConfiguration {
  
  /**
   * Enable or disable metering checks.
   * When disabled, all requests are allowed through without balance checks.
   * Default: false (disabled)
   */
  @WithDefault("false")
  boolean enabled();
  
  /**
   * Base URL of the admin portal API.
   * Example: "https://admin-portal.company.com"
   * Required when metering is enabled.
   */
  String apiBaseUrl();
  
  /**
   * Optional API key for authenticating with the admin portal.
   * If provided, will be sent as "Authorization: Bearer {apiKey}" header.
   */
  Optional<String> apiKey();
  
  /**
   * HTTP request timeout in milliseconds.
   * Default: 5000 (5 seconds)
   */
  @WithDefault("5000")
  int timeoutMs();
  
  /**
   * Maximum number of retries for failed API calls.
   * Default: 3
   */
  @WithDefault("3")
  int maxRetries();
  
  /**
   * Minimum number of tokens required for balance check.
   * This is the amount passed to the ensure_sufficient API endpoint.
   * Default: 1.0
   */
  @WithDefault("1.0")
  double minimumTokensRequired();
}

