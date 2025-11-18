package org.apache.polaris.core.metering;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for checking user token balances via metering API.
 * 
 * This service calls the metering API to verify that users have
 * sufficient token balance before Polaris vends AWS credentials.
 * 
 * @note Assumes user's email address is the Polaris principal name.
 */
@Singleton
public class MeteringService {
  private static final Logger LOGGER = LoggerFactory.getLogger(MeteringService.class);
  
  private final MeteringConfig config;
  private final CloseableHttpClient httpClient;
  private final ObjectMapper objectMapper;
  
  @Inject
  public MeteringService(MeteringConfig config) {
    this.config = config;
    this.objectMapper = new ObjectMapper();
    
    // Configure HTTP client with timeouts
    RequestConfig requestConfig = RequestConfig.custom()
        .setConnectionRequestTimeout(Timeout.ofMilliseconds(config.timeoutMs()))
        .setResponseTimeout(Timeout.ofMilliseconds(config.timeoutMs()))
        .build();
    
    this.httpClient = HttpClients.custom()
        .setDefaultRequestConfig(requestConfig)
        .build();
  }
  
  /**
   * Check if user has sufficient token balance.
   * 
   * Calls: POST /api/integration/metering/ensure_sufficient
   * Request body: {"user_email": "user@example.com", "required_tokens": 1.0}
   * 
   * @param principalEmail User's email address (Polaris principal name)
   * @return true if user has sufficient balance, false otherwise
   * @throws MeteringServiceException if API call fails or service is unreachable
   */
  public boolean hasSufficientBalance(String principalEmail) throws MeteringServiceException {
    if (!config.enabled()) {
      LOGGER.debug("Metering disabled, allowing request for principal '{}'", principalEmail);
      return true;
    }
    
    // Skip metering for system/admin principals
    // TODO: Find a way to skip metering checks for metadata endpoints.
    if ("root".equals(principalEmail)) {
      LOGGER.debug("Skipping metering for system principal '{}'", principalEmail);
      return true;
    }

    int attempts = 0;
    Exception lastException = null;
    
    while (attempts < config.maxRetries()) {
      attempts++;
      final int currentAttempt = attempts; // Make effectively final for lambda
      
      try {
        String url = config.apiBaseUrl() + "/api/v1/integration/metering/ensure_sufficient";
        
        // Build request body
        EnsureSufficientRequest requestBody = new EnsureSufficientRequest(
            principalEmail,
            BigDecimal.valueOf(config.minimumTokensRequired())
        );
        
        HttpPost request = new HttpPost(url);
        request.setHeader("Content-Type", "application/json");
        config.apiKey().ifPresent(key -> 
            request.setHeader("x-api-key", key));
        
        String jsonBody = objectMapper.writeValueAsString(requestBody);
        request.setEntity(new StringEntity(jsonBody, ContentType.APPLICATION_JSON));
        
        Boolean result = httpClient.execute(request, httpResponse -> {
          int statusCode = httpResponse.getCode();
          
          if (statusCode == 200) {
            // Success - user has sufficient balance
            LOGGER.debug("Balance check for principal '{}': sufficient (status 200)", principalEmail);
            return true;
          } else if (statusCode == 400) {
            // Bad request - likely insufficient balance or user not found
            LOGGER.warn("Balance check for principal '{}': insufficient or user not found (status 400)", 
                       principalEmail);
            return false;
          } else if (statusCode == 402) {
            // Payment required - explicit insufficient balance
            LOGGER.warn("Balance check for principal '{}': insufficient balance (status 402)", 
                       principalEmail);
            return false;
          } else if (statusCode >= 500) {
            // Server error - throw exception to trigger retry
            String errorMsg = String.format(
                "Metering service API returned server error: %d (attempt %d/%d)", 
                statusCode, currentAttempt, config.maxRetries());
            throw new IOException(errorMsg);
          } else {
            // Other client errors - don't retry
            String errorMsg = String.format(
                "Metering service API returned unexpected status: %d", statusCode);
            try {
              throw new MeteringServiceException(errorMsg);
            } catch (MeteringServiceException e) {
              throw new RuntimeException(e);
            }
          }
        });
        
        LOGGER.info("Balance check for principal '{}': {}", principalEmail, 
                   result ? "sufficient" : "insufficient");
        return result;
        
      } catch (IOException e) {
        lastException = e;
        LOGGER.warn("Failed to check balance for principal '{}' (attempt {}/{}): {}", 
                   principalEmail, attempts, config.maxRetries(), e.getMessage());
        
        if (attempts < config.maxRetries()) {
          try {
            long backoffMs = (long) Math.pow(2, attempts - 1) * 1000;
            Thread.sleep(Math.min(backoffMs, 5000)); // Cap at 5 seconds
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new MeteringServiceException("Interrupted while retrying balance check", ie);
          }
        }
      } catch (Exception e) {
        lastException = e;
        LOGGER.error("Unexpected error checking balance for principal '{}'", principalEmail, e);
        throw new MeteringServiceException("Failed to check balance: " + e.getMessage(), e);
      }
    }
    
    // All retries exhausted
    String errorMsg = String.format(
        "Failed to check balance for principal '%s' after %d attempts", 
        principalEmail, config.maxRetries());
    throw new MeteringServiceException(errorMsg, lastException);
  }
  
  /**
   * Request DTO for ensure_sufficient API endpoint.
   * Fields are public for Jackson serialization.
   */
  private static class EnsureSufficientRequest {
    public final String user_email;
    public final BigDecimal required_tokens;
    
    public EnsureSufficientRequest(String user_email, BigDecimal required_tokens) {
      this.user_email = user_email;
      this.required_tokens = required_tokens;
    }
  }
}

