/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
@ConditionalOnExpression(
    "${camunda.tasklist.persistent.sessions.enabled:false}"
        + " or "
        + "${camunda.tasklist.persistentSessionsEnabled:false}")
public class SessionRepositoryConfigurator {

  @Bean("sessionThreadPoolScheduler")
  public ThreadPoolTaskScheduler getTaskScheduler() {
    final ThreadPoolTaskScheduler executor = new ThreadPoolTaskScheduler();
    executor.setPoolSize(5);
    executor.setThreadNamePrefix("tasklist_session_");
    executor.initialize();
    return executor;
  }

  @Bean
  public CookieSerializer cookieSerializer() {
    final DefaultCookieSerializer serializer = new DefaultCookieSerializer();
    serializer.setCookieName(TasklistURIs.COOKIE_JSESSIONID);
    serializer.setUseBase64Encoding(false);
    return serializer;
  }
}
