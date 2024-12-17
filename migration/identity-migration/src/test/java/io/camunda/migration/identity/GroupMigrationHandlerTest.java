/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.assertArg;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.migration.identity.dto.Group;
import io.camunda.migration.identity.dto.MigrationStatusUpdateRequest;
import io.camunda.migration.identity.midentity.ManagementIdentityClient;
import io.camunda.migration.identity.midentity.ManagementIdentityTransformer;
import io.camunda.security.auth.Authentication.Builder;
import io.camunda.service.GroupServices;
import io.camunda.zeebe.client.api.command.ProblemException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class GroupMigrationHandlerTest {
  private final ManagementIdentityClient managementIdentityClient;

  private final GroupServices groupService;

  private final GroupMigrationHandler migrationHandler;

  public GroupMigrationHandlerTest(
      @Mock final ManagementIdentityClient managementIdentityClient,
      @Mock(answer = Answers.RETURNS_SELF) final GroupServices groupService) {
    this.managementIdentityClient = managementIdentityClient;
    this.groupService = groupService;
    migrationHandler =
        new GroupMigrationHandler(
            new Builder(),
            managementIdentityClient,
            new ManagementIdentityTransformer(),
            groupService);
  }

  @Test
  void stopWhenNoMoreRecords() {
    // given
    when(managementIdentityClient.fetchGroups(anyInt()))
        .thenReturn(List.of(new Group("id1", "t1"), new Group("id2", "t2")))
        .thenReturn(List.of());

    // when
    migrationHandler.migrate();

    // then
    verify(managementIdentityClient, times(2)).fetchGroups(anyInt());
    verify(groupService, times(2)).createGroup(any());
  }

  @Test
  void ignoreWhenGroupAlreadyExists() {
    // given

    when(groupService.createGroup(any()))
        .thenThrow(new ProblemException(0, "Failed with code 409: 'Conflict'", null));
    when(managementIdentityClient.fetchGroups(anyInt()))
        .thenReturn(List.of(new Group("id1", "t1"), new Group("id2", "t2")))
        .thenReturn(List.of());

    // when
    migrationHandler.migrate();

    // then
    verify(managementIdentityClient, times(2)).fetchGroups(anyInt());
    verify(groupService, times(2)).createGroup(any());
    verify(managementIdentityClient, times(2))
        .updateMigrationStatus(
            assertArg(
                migrationStatusUpdateRequests -> {
                  assertThat(migrationStatusUpdateRequests)
                      .describedAs("All migrations have succeeded")
                      .allMatch(MigrationStatusUpdateRequest::success);
                }));
  }

  @Test
  void setErrorWhenGroupCreationHasError() {
    // given
    when(groupService.createGroup(any())).thenThrow(new RuntimeException());
    when(managementIdentityClient.fetchGroups(anyInt()))
        .thenReturn(List.of(new Group("id1", "t1"), new Group("id2", "t2")))
        .thenReturn(List.of());

    // when
    migrationHandler.migrate();

    // then
    verify(managementIdentityClient, times(2))
        .updateMigrationStatus(
            assertArg(
                statusUpdateRequests -> {
                  assertThat(statusUpdateRequests)
                      .describedAs("All migrations have failed")
                      .noneMatch(MigrationStatusUpdateRequest::success);
                }));
    verify(managementIdentityClient, times(2)).fetchGroups(anyInt());
    verify(groupService, times(2)).createGroup(any());
  }
}
