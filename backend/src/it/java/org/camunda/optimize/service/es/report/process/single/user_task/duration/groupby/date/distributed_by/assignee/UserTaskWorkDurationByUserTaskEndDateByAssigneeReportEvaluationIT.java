/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.user_task.duration.groupby.date.distributed_by.assignee;

import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;

public class UserTaskWorkDurationByUserTaskEndDateByAssigneeReportEvaluationIT
  extends UserTaskDurationByUserTaskEndDateByAssigneeReportEvaluationIT {

  @Override
  protected UserTaskDurationTime getUserTaskDurationTime() {
    return UserTaskDurationTime.WORK;
  }

  @Override
  protected void changeDuration(final ProcessInstanceEngineDto processInstanceDto, final Double duration) {
    changeUserTaskWorkDuration(processInstanceDto, duration);
  }

  @Override
  protected void changeDuration(final ProcessInstanceEngineDto processInstanceDto,
                                final String userTaskKey,
                                final Double duration) {
    changeUserTaskWorkDuration(processInstanceDto, userTaskKey, duration);
  }
}
