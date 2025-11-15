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
package org.apache.polaris.core;

import jakarta.annotation.Nonnull;
import java.util.function.Supplier;
import org.apache.polaris.core.config.PolarisConfigurationStore;
import org.apache.polaris.core.config.RealmConfig;
import org.apache.polaris.core.config.RealmConfigImpl;
import org.apache.polaris.core.context.CallContext;
import org.apache.polaris.core.context.RealmContext;
import org.apache.polaris.core.persistence.BasePersistence;

/**
 * The Call context is allocated each time a new REST request is processed. It contains instances of
 * low-level services required to process that request
 */
public class PolarisCallContext implements CallContext {

  // meta store which is used to persist Polaris entity metadata
  private final BasePersistence metaStore;
  private final PolarisConfigurationStore configurationStore;
  private final RealmContext realmContext;
  private final RealmConfig realmConfig;
  private final Supplier<String> principalNameSupplier;

  public PolarisCallContext(
      @Nonnull RealmContext realmContext,
      @Nonnull BasePersistence metaStore,
      @Nonnull PolarisConfigurationStore configurationStore) {
    this(realmContext, metaStore, configurationStore, () -> null);
  }

  public PolarisCallContext(
      @Nonnull RealmContext realmContext,
      @Nonnull BasePersistence metaStore,
      @Nonnull PolarisConfigurationStore configurationStore,
      String principalName) {
    this(realmContext, metaStore, configurationStore, () -> principalName);
  }

  public PolarisCallContext(
      @Nonnull RealmContext realmContext,
      @Nonnull BasePersistence metaStore,
      @Nonnull PolarisConfigurationStore configurationStore,
      Supplier<String> principalNameSupplier) {
    this.realmContext = realmContext;
    this.metaStore = metaStore;
    this.configurationStore = configurationStore;
    this.realmConfig = new RealmConfigImpl(this.configurationStore, this.realmContext);
    this.principalNameSupplier = principalNameSupplier != null ? principalNameSupplier : () -> null;
  }

  public PolarisCallContext(
      @Nonnull RealmContext realmContext, @Nonnull BasePersistence metaStore) {
    this(realmContext, metaStore, new PolarisConfigurationStore() {}, () -> null);
  }

  public BasePersistence getMetaStore() {
    return metaStore;
  }

  @Override
  public RealmContext getRealmContext() {
    return realmContext;
  }

  @Override
  public RealmConfig getRealmConfig() {
    return realmConfig;
  }

  @Override
  public PolarisCallContext getPolarisCallContext() {
    return this;
  }

  /**
   * Get the principal name associated with this call context.
   * This is evaluated lazily to ensure authentication has completed.
   * 
   * @return the principal name, or null if not set
   */
  public String getPrincipalName() {
    return principalNameSupplier.get();
  }

  @Override
  public PolarisCallContext copy() {
    // The realm context is a request scoped bean injected by CDI,
    // which will be closed after the http request. This copy is currently
    // only used by TaskExecutor right before the task is handled, since the
    // task is executed outside the active request scope, we need to make a
    // copy of the RealmContext to ensure the access during the task executor.
    String realmId = this.realmContext.getRealmIdentifier();
    RealmContext realmContext = () -> realmId;
    // Capture the principal name at copy time
    String capturedPrincipalName = this.principalNameSupplier.get();
    return new PolarisCallContext(realmContext, this.metaStore, this.configurationStore, capturedPrincipalName);
  }
}
