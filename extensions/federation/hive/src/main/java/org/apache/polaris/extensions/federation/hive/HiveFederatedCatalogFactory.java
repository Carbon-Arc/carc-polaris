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
package org.apache.polaris.extensions.federation.hive;

import io.smallrye.common.annotation.Identifier;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.hadoop.conf.Configuration;
import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.hive.HiveCatalog;
import org.apache.polaris.core.catalog.ExternalCatalogFactory;
import org.apache.polaris.core.catalog.GenericTableCatalog;
import org.apache.polaris.core.connection.AuthenticationParametersDpo;
import org.apache.polaris.core.connection.AuthenticationType;
import org.apache.polaris.core.connection.ConnectionConfigInfoDpo;
import org.apache.polaris.core.connection.ConnectionType;
import org.apache.polaris.core.connection.hive.HiveConnectionConfigInfoDpo;
import org.apache.polaris.core.secrets.UserSecretsManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Factory class for creating a Hive catalog handle based on connection configuration. */
@ApplicationScoped
@Identifier(ConnectionType.HIVE_FACTORY_IDENTIFIER)
public class HiveFederatedCatalogFactory implements ExternalCatalogFactory {
  private static final Logger LOGGER = LoggerFactory.getLogger(HiveFederatedCatalogFactory.class);

  @Override
  public Catalog createCatalog(
      ConnectionConfigInfoDpo connectionConfigInfoDpo, UserSecretsManager userSecretsManager) {
    // Currently, Polaris supports Hive federation only via IMPLICIT authentication.
    // Hence, prior to initializing the configuration, ensure that the catalog uses
    // IMPLICIT authentication.
    AuthenticationParametersDpo authenticationParametersDpo =
        connectionConfigInfoDpo.getAuthenticationParameters();
    if (authenticationParametersDpo.getAuthenticationTypeCode()
        != AuthenticationType.IMPLICIT.getCode()) {
      throw new IllegalStateException("Hive federation only supports IMPLICIT authentication.");
    }
    String warehouse = ((HiveConnectionConfigInfoDpo) connectionConfigInfoDpo).getWarehouse();
    
    // Create Hadoop Configuration and set S3A credentials provider for IRSA support
    Configuration conf = new Configuration();
    
    // Configure S3A to use AWS SDK v2 DefaultCredentialsProvider which supports WebIdentityToken (IRSA)
    conf.set("fs.s3a.aws.credentials.provider", "software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider");
    
    // Additional S3A settings
    conf.set("fs.s3a.path.style.access", "false");
    conf.set("fs.s3a.connection.maximum", "100");
    conf.set("fs.s3a.threads.max", "50");
    
    LOGGER.info("Configured Hadoop S3A with DefaultCredentialsProvider for IRSA support");
    
    HiveCatalog hiveCatalog = new HiveCatalog();
    hiveCatalog.setConf(conf);
    hiveCatalog.initialize(
        warehouse, connectionConfigInfoDpo.asIcebergCatalogProperties(userSecretsManager));
    return hiveCatalog;
  }

  @Override
  public GenericTableCatalog createGenericCatalog(
      ConnectionConfigInfoDpo connectionConfig, UserSecretsManager userSecretsManager) {
    // TODO implement
    throw new UnsupportedOperationException(
        "Generic table federation to this catalog is not supported.");
  }
}
