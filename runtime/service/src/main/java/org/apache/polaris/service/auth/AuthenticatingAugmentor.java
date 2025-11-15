/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.polaris.service.auth;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.iceberg.exceptions.ServiceFailureException;
import org.apache.polaris.core.auth.PolarisPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A custom {@link SecurityIdentityAugmentor} that, after Quarkus OIDC or Internal Auth extracted
 * and validated the principal credentials, augments the {@link SecurityIdentity} by authenticating
 * the principal and setting a {@link PolarisPrincipal} as the identity's principal.
 */
@ApplicationScoped
public class AuthenticatingAugmentor implements SecurityIdentityAugmentor {

  private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticatingAugmentor.class);
  
  public static final int PRIORITY = 1000;

  private final Authenticator authenticator;

  @Inject
  public AuthenticatingAugmentor(Authenticator authenticator) {
    this.authenticator = authenticator;
  }

  @Override
  public int priority() {
    return PRIORITY;
  }

  @Override
  public Uni<SecurityIdentity> augment(
      SecurityIdentity identity, AuthenticationRequestContext context) {
    if (identity.isAnonymous()) {
      LOGGER.debug("Identity is anonymous, skipping augmentation");
      return Uni.createFrom().item(identity);
    }
    LOGGER.debug("Augmenting identity with principal: {}, credentials: {}", 
        identity.getPrincipal() != null ? identity.getPrincipal().getName() : "null",
        identity.getCredentials());
    PolarisCredential authInfo = extractPolarisCredential(identity);
    return context.runBlocking(() -> authenticatePolarisPrincipal(identity, authInfo));
  }

  private PolarisCredential extractPolarisCredential(SecurityIdentity identity) {
    PolarisCredential credential = identity.getCredential(PolarisCredential.class);
    if (credential == null) {
      LOGGER.error("No PolarisCredential found in SecurityIdentity. Available credentials: {}", 
          identity.getCredentials());
      throw new AuthenticationFailedException("No token credential available");
    }
    LOGGER.debug("Extracted PolarisCredential for principal: {}", credential.getPrincipalName());
    return credential;
  }

  private SecurityIdentity authenticatePolarisPrincipal(
      SecurityIdentity identity, PolarisCredential polarisCredential) {
    try {
      LOGGER.debug("Authenticating principal: {}", polarisCredential.getPrincipalName());
      PolarisPrincipal polarisPrincipal = authenticator.authenticate(polarisCredential);
      LOGGER.debug("Successfully authenticated principal: {} with roles: {}", 
          polarisPrincipal.getName(), polarisPrincipal.getRoles());
      QuarkusSecurityIdentity.Builder builder =
          QuarkusSecurityIdentity.builder()
              .setAnonymous(false)
              .setPrincipal(polarisPrincipal)
              .addRoles(polarisPrincipal.getRoles())
              .addCredentials(identity.getCredentials())
              .addAttributes(identity.getAttributes())
              .addPermissionChecker(identity::checkPermission);
      // Also include the Polaris principal properties as attributes of the identity
      polarisPrincipal.getProperties().forEach(builder::addAttribute);
      // Store principal name as an attribute for easy access in CallContext producer
      builder.addAttribute("polaris.principal.name", polarisPrincipal.getName());
      SecurityIdentity result = builder.build();
      LOGGER.debug("Built SecurityIdentity with PolarisPrincipal: {}", result.getPrincipal().getName());
      return result;
    } catch (ServiceFailureException e) {
      // Let ServiceFailureException bubble up to be handled by IcebergExceptionMapper
      // This will result in 503 Service Unavailable instead of 401 Unauthorized
      LOGGER.error("Service failure during authentication", e);
      throw e;
    } catch (RuntimeException e) {
      LOGGER.error("Authentication failed for principal: {}", polarisCredential.getPrincipalName(), e);
      throw new AuthenticationFailedException(e);
    }
  }
}
