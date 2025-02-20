/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.utils;

import io.camunda.exporter.CamundaExporter;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.zeebe.exporter.ElasticsearchExporter;
import io.camunda.zeebe.exporter.opensearch.OpensearchExporter;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneApplication;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Helper class to configure any {@link TestStandaloneApplication}, with specific secondary storage.
 */
public class MultiDbConfigurator {

  private final TestStandaloneApplication<?> testApplication;

  public MultiDbConfigurator(final TestStandaloneApplication<?> testApplication) {
    this.testApplication = testApplication;
  }

  public void configureElasticsearchSupport(
      final String elasticsearchUrl, final String indexPrefix) {

    final Map<String, Object> elasticsearchProperties = new HashMap<>();

    /* Tasklist */
    elasticsearchProperties.put("camunda.tasklist.elasticsearch.url", elasticsearchUrl);
    elasticsearchProperties.put("camunda.tasklist.zeebeElasticsearch.url", elasticsearchUrl);
    elasticsearchProperties.put("camunda.tasklist.elasticsearch.index-prefix", indexPrefix);
    elasticsearchProperties.put("camunda.tasklist.zeebeElasticsearch.prefix", indexPrefix);

    /* Operate */
    elasticsearchProperties.put("camunda.operate.elasticsearch.url", elasticsearchUrl);
    elasticsearchProperties.put("camunda.operate.zeebeElasticsearch.url", elasticsearchUrl);
    elasticsearchProperties.put("camunda.operate.elasticsearch.index-prefix", indexPrefix);
    elasticsearchProperties.put("camunda.operate.zeebeElasticsearch.prefix", indexPrefix);

    /* Camunda */
    elasticsearchProperties.put(
        "camunda.database.type",
        io.camunda.search.connect.configuration.DatabaseType.ELASTICSEARCH);
    elasticsearchProperties.put("camunda.database.indexPrefix", indexPrefix);
    elasticsearchProperties.put("camunda.database.url", elasticsearchUrl);

    testApplication.withAdditionalProperties(elasticsearchProperties);

    testApplication.withExporter(
        "CamundaExporter",
        cfg -> {
          cfg.setClassName(CamundaExporter.class.getName());
          cfg.setArgs(
              Map.of(
                  "connect",
                  Map.of(
                      "url",
                      elasticsearchUrl,
                      "indexPrefix",
                      indexPrefix,
                      "type",
                      io.camunda.search.connect.configuration.DatabaseType.ELASTICSEARCH),
                  "index",
                  Map.of("prefix", indexPrefix),
                  "bulk",
                  Map.of("size", 1)));
        });

    testApplication.withExporter(
        "ElasticsearchExporter",
        cfg -> {
          cfg.setClassName(ElasticsearchExporter.class.getName());
          cfg.setArgs(
              Map.of(
                  "url", elasticsearchUrl,
                  "index", Map.of("prefix", indexPrefix),
                  "bulk", Map.of("size", 1)));
        });
  }

  public void configureOpenSearchSupport(
      final String opensearchUrl,
      final String indexPrefix,
      final String userName,
      final String userPassword) {

    final Map<String, Object> opensearchProperties = new HashMap<>();

    /* Tasklist */
    opensearchProperties.put("camunda.tasklist.opensearch.url", opensearchUrl);
    opensearchProperties.put("camunda.tasklist.zeebeOpensearch.url", opensearchUrl);
    opensearchProperties.put("camunda.tasklist.opensearch.index-prefix", indexPrefix);
    opensearchProperties.put("camunda.tasklist.zeebeOpensearch.prefix", indexPrefix);
    opensearchProperties.put("camunda.tasklist.opensearch.username", userName);
    opensearchProperties.put("camunda.tasklist.opensearch.password", userPassword);

    /* Operate */
    opensearchProperties.put("camunda.operate.opensearch.url", opensearchUrl);
    opensearchProperties.put("camunda.operate.zeebeOpensearch.url", opensearchUrl);
    opensearchProperties.put("camunda.operate.opensearch.index-prefix", indexPrefix);
    opensearchProperties.put("camunda.operate.zeebeOpensearch.prefix", indexPrefix);
    opensearchProperties.put("camunda.operate.opensearch.username", userName);
    opensearchProperties.put("camunda.operate.opensearch.password", userPassword);

    /* Camunda */
    opensearchProperties.put(
        "camunda.database.type", io.camunda.search.connect.configuration.DatabaseType.OPENSEARCH);
    opensearchProperties.put("camunda.operate.database", "opensearch");
    opensearchProperties.put("camunda.tasklist.database", "opensearch");
    opensearchProperties.put("camunda.database.indexPrefix", indexPrefix);
    opensearchProperties.put("camunda.database.username", userName);
    opensearchProperties.put("camunda.database.password", userPassword);
    opensearchProperties.put("camunda.database.url", opensearchUrl);

    testApplication.withAdditionalProperties(opensearchProperties);

    testApplication.withExporter(
        "CamundaExporter",
        cfg -> {
          cfg.setClassName(CamundaExporter.class.getName());
          cfg.setArgs(
              Map.of(
                  "connect",
                  Map.of(
                      "url",
                      opensearchUrl,
                      "indexPrefix",
                      indexPrefix,
                      "type",
                      io.camunda.search.connect.configuration.DatabaseType.OPENSEARCH,
                      "username",
                      userName,
                      "password",
                      userPassword),
                  "index",
                  Map.of("prefix", indexPrefix),
                  "bulk",
                  Map.of("size", 1)));
        });

    testApplication.withExporter(
        "OpensearchExporter",
        cfg -> {
          cfg.setClassName(OpensearchExporter.class.getName());
          cfg.setArgs(
              Map.of(
                  "url",
                  opensearchUrl,
                  "index",
                  Map.of("prefix", indexPrefix),
                  "bulk",
                  Map.of("size", 1),
                  "authentication",
                  Map.of("username", userName, "password", userPassword)));
        });
  }

  public void configureRDBMSSupport() {
    testApplication.withProperty("camunda.database.type", DatabaseType.RDBMS);
    testApplication.withProperty(
        "spring.datasource.url",
        "jdbc:h2:mem:testdb+" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
    testApplication.withProperty("spring.datasource.driver-class-name", "org.h2.Driver");
    testApplication.withProperty("spring.datasource.username", "sa");
    testApplication.withProperty("spring.datasource.password", "");
    testApplication.withProperty("logging.level.io.camunda.db.rdbms", "DEBUG");
    testApplication.withProperty("logging.level.org.mybatis", "DEBUG");
    testApplication.withExporter(
        "rdbms",
        cfg -> {
          cfg.setClassName("-");
          cfg.setArgs(Map.of("flushInterval", "0"));
        });
  }
}
