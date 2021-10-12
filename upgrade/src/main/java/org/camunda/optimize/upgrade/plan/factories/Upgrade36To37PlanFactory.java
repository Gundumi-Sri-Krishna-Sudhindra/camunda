/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.plan.factories;

import org.camunda.optimize.service.es.schema.index.DashboardIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleDecisionReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.camunda.optimize.upgrade.plan.UpgradeExecutionDependencies;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.camunda.optimize.upgrade.steps.schema.UpdateIndexStep;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Upgrade36To37PlanFactory implements UpgradePlanFactory {

  @Override
  public UpgradePlan createUpgradePlan(final UpgradeExecutionDependencies dependencies) {
    return UpgradePlanBuilder.createUpgradePlan()
      .fromVersion("3.6.0")
      .toVersion("3.7.0")
      .addUpgradeSteps(migrateReportFilters())
      .addUpgradeStep(migrateDashboardDateFilters())
      .build();
  }

  private static UpgradeStep migrateDashboardDateFilters() {
    // @formatter:off
    final String dashboardFilterMigrationScript =
      "def filters = ctx._source.availableFilters;" +
      "if (filters != null) {" +
        "for (def filter : filters) {" +
          "if (\"startDate\".equals(filter.type)) {" +
            "filter.type = \"instanceStartDate\";" +
          "}" +
          "if (\"endDate\".equals(filter.type)) {" +
            "filter.type = \"instanceEndDate\";" +
          "}" +
        "}" +
      "}";
    // @formatter:on
    return new UpdateIndexStep(new DashboardIndex(), dashboardFilterMigrationScript);
  }

  private static List<UpgradeStep> migrateReportFilters() {
    // @formatter:off
    final String reportFilterMigrationScript =
      "if (ctx._source.data != null) {" +
        "def filters = ctx._source.data.filter;" +
        "if (filters != null) {" +
          "for (def filter : filters) {" +
            "if (\"startDate\".equals(filter.type)) {" +
              "filter.type = \"instanceStartDate\";" +
            "}" +
            "if (\"endDate\".equals(filter.type)) {" +
              "filter.type = \"instanceEndDate\";" +
            "}" +
          "}" +
        "}" +
      "}";
    // @formatter:on
    return Stream.of(new SingleProcessReportIndex(), new SingleDecisionReportIndex())
      .map(index -> new UpdateIndexStep(index, reportFilterMigrationScript))
      .collect(Collectors.toList());
  }


}
