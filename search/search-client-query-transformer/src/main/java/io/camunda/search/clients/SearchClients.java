/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients;

import io.camunda.search.clients.auth.DocumentAuthorizationQueryStrategy;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.entities.AuthorizationEntity;
import io.camunda.search.entities.DecisionDefinitionEntity;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.entities.DecisionRequirementsEntity;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.FormEntity;
import io.camunda.search.entities.GroupEntity;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.entities.MappingEntity;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.entities.TenantEntity;
import io.camunda.search.entities.UserEntity;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.query.AuthorizationQuery;
import io.camunda.search.query.DecisionDefinitionQuery;
import io.camunda.search.query.DecisionInstanceQuery;
import io.camunda.search.query.DecisionRequirementsQuery;
import io.camunda.search.query.FlowNodeInstanceQuery;
import io.camunda.search.query.FormQuery;
import io.camunda.search.query.GroupQuery;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.MappingQuery;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.RoleQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.TenantQuery;
import io.camunda.search.query.UserQuery;
import io.camunda.search.query.UserTaskQuery;
import io.camunda.search.query.VariableQuery;
import io.camunda.security.auth.SecurityContext;
import java.util.List;

public class SearchClients
    implements AuthorizationSearchClient,
        DecisionDefinitionSearchClient,
        DecisionInstanceSearchClient,
        DecisionRequirementSearchClient,
        FlowNodeInstanceSearchClient,
        FormSearchClient,
        IncidentSearchClient,
        ProcessDefinitionSearchClient,
        ProcessInstanceSearchClient,
        RoleSearchClient,
        TenantSearchClient,
        UserTaskSearchClient,
        UserSearchClient,
        VariableSearchClient,
        MappingSearchClient,
        GroupSearchClient {

  private final DocumentBasedSearchClient searchClient;
  private final ServiceTransformers transformers;
  private final SecurityContext securityContext;

  public SearchClients(final DocumentBasedSearchClient searchClient, final String prefix) {
    this(
        searchClient,
        ServiceTransformers.newInstance(prefix),
        SecurityContext.withoutAuthentication());
  }

  private SearchClients(
      final DocumentBasedSearchClient searchClient,
      final ServiceTransformers transformers,
      final SecurityContext securityContext) {
    this.searchClient = searchClient;
    this.transformers = transformers;
    this.securityContext = securityContext;
  }

  @Override
  public SearchQueryResult<AuthorizationEntity> searchAuthorizations(
      final AuthorizationQuery filter) {
    return getSearchExecutor()
        .search(
            filter, io.camunda.webapps.schema.entities.usermanagement.AuthorizationEntity.class);
  }

  @Override
  public List<AuthorizationEntity> findAllAuthorizations(final AuthorizationQuery filter) {
    return getSearchExecutor()
        .findAll(
            filter, io.camunda.webapps.schema.entities.usermanagement.AuthorizationEntity.class);
  }

  @Override
  public SearchClients withSecurityContext(final SecurityContext securityContext) {
    return new SearchClients(searchClient, transformers, securityContext);
  }

  @Override
  public SearchQueryResult<MappingEntity> searchMappings(final MappingQuery filter) {
    return getSearchExecutor()
        .search(filter, io.camunda.webapps.schema.entities.usermanagement.MappingEntity.class);
  }

  @Override
  public SearchQueryResult<DecisionDefinitionEntity> searchDecisionDefinitions(
      final DecisionDefinitionQuery filter) {
    return getSearchExecutor()
        .search(
            filter,
            io.camunda.webapps.schema.entities.operate.dmn.definition.DecisionDefinitionEntity
                .class);
  }

  @Override
  public SearchQueryResult<DecisionInstanceEntity> searchDecisionInstances(
      final DecisionInstanceQuery filter) {
    return getSearchExecutor()
        .search(
            filter, io.camunda.webapps.schema.entities.operate.dmn.DecisionInstanceEntity.class);
  }

  @Override
  public SearchQueryResult<DecisionRequirementsEntity> searchDecisionRequirements(
      final DecisionRequirementsQuery filter) {
    return getSearchExecutor()
        .search(
            filter,
            io.camunda.webapps.schema.entities.operate.dmn.definition.DecisionRequirementsEntity
                .class);
  }

  @Override
  public SearchQueryResult<FlowNodeInstanceEntity> searchFlowNodeInstances(
      final FlowNodeInstanceQuery filter) {
    return getSearchExecutor()
        .search(filter, io.camunda.webapps.schema.entities.operate.FlowNodeInstanceEntity.class);
  }

  @Override
  public SearchQueryResult<FormEntity> searchForms(final FormQuery filter) {
    return getSearchExecutor()
        .search(filter, io.camunda.webapps.schema.entities.tasklist.FormEntity.class);
  }

  @Override
  public SearchQueryResult<IncidentEntity> searchIncidents(final IncidentQuery filter) {
    return getSearchExecutor()
        .search(filter, io.camunda.webapps.schema.entities.operate.IncidentEntity.class);
  }

  @Override
  public SearchQueryResult<ProcessDefinitionEntity> searchProcessDefinitions(
      final ProcessDefinitionQuery filter) {
    return getSearchExecutor()
        .search(filter, io.camunda.webapps.schema.entities.operate.ProcessEntity.class);
  }

  @Override
  public SearchQueryResult<ProcessInstanceEntity> searchProcessInstances(
      final ProcessInstanceQuery filter) {
    return getSearchExecutor()
        .search(
            filter,
            io.camunda.webapps.schema.entities.operate.listview.ProcessInstanceForListViewEntity
                .class);
  }

  @Override
  public SearchQueryResult<RoleEntity> searchRoles(final RoleQuery filter) {
    return getSearchExecutor()
        .search(filter, io.camunda.webapps.schema.entities.usermanagement.RoleEntity.class);
  }

  @Override
  public List<RoleEntity> findAllRoles(final RoleQuery filter) {
    return getSearchExecutor()
        .findAll(filter, io.camunda.webapps.schema.entities.usermanagement.RoleEntity.class);
  }

  @Override
  public SearchQueryResult<TenantEntity> searchTenants(final TenantQuery filter) {
    return getSearchExecutor()
        .search(filter, io.camunda.webapps.schema.entities.usermanagement.TenantEntity.class);
  }

  @Override
  public SearchQueryResult<GroupEntity> searchGroups(final GroupQuery filter) {
    return new SearchClientBasedQueryExecutor(
            searchClient,
            transformers,
            new DocumentAuthorizationQueryStrategy(this),
            securityContext)
        .search(filter, GroupEntity.class);
  }

  @Override
  public SearchQueryResult<UserEntity> searchUsers(final UserQuery filter) {
    return getSearchExecutor()
        .search(filter, io.camunda.webapps.schema.entities.usermanagement.UserEntity.class);
  }

  @Override
  public SearchQueryResult<UserTaskEntity> searchUserTasks(final UserTaskQuery filter) {
    return getSearchExecutor()
        .search(filter, io.camunda.webapps.schema.entities.tasklist.TaskEntity.class);
  }

  @Override
  public SearchQueryResult<VariableEntity> searchVariables(final VariableQuery filter) {
    return getSearchExecutor()
        .search(filter, io.camunda.webapps.schema.entities.operate.VariableEntity.class);
  }

  private SearchClientBasedQueryExecutor getSearchExecutor() {
    return new SearchClientBasedQueryExecutor(
        searchClient, transformers, new DocumentAuthorizationQueryStrategy(this), securityContext);
  }
}
