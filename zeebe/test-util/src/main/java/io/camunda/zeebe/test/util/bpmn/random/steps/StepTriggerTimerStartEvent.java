/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.bpmn.random.steps;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;

public final class StepTriggerTimerStartEvent extends AbstractExecutionStep
    implements ProcessStartStep {

  private final Duration timeToAdd;

  public StepTriggerTimerStartEvent(final Duration timeToAdd) {
    this.timeToAdd = timeToAdd;
  }

  @Override
  public Map<String, Object> getProcessVariables() {
    return Collections.unmodifiableMap(variables);
  }

  @Override
  protected Map<String, Object> updateVariables(
      final Map<String, Object> variables, final Duration activationDuration) {
    return variables;
  }

  @Override
  public boolean isAutomatic() {
    return false;
  }

  @Override
  public Duration getDeltaTime() {
    return timeToAdd;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + timeToAdd.hashCode();
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }

    final StepTriggerTimerStartEvent that = (StepTriggerTimerStartEvent) o;

    return timeToAdd.equals(that.timeToAdd);
  }
}
