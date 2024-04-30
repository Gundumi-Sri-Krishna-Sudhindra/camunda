/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ContainerVersionsUtil {

  public static final String ZEEBE_CURRENTVERSION_DOCKER_PROPERTY_NAME =
      "zeebe.currentVersion.docker";

  public static final String IDENTITY_CURRENTVERSION_DOCKER_PROPERTY_NAME =
      "identity.currentVersion.docker";
  private static final String VERSIONS_FILE = "/container-versions.properties";

  public static String readProperty(String propertyName) {
    try (InputStream propsFile = ContainerVersionsUtil.class.getResourceAsStream(VERSIONS_FILE)) {
      final Properties props = new Properties();
      props.load(propsFile);
      return props.getProperty(propertyName);
    } catch (IOException e) {
      throw new TasklistRuntimeException(
          "Unable to read the list of supported Zeebe zeebeVersions.", e);
    }
  }
}
