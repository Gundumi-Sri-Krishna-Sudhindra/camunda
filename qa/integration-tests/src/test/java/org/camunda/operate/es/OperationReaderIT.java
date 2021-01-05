/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.es;

import java.util.ArrayList;
import java.util.List;
import org.camunda.operate.entities.IncidentState;
import org.camunda.operate.entities.OperateEntity;
import org.camunda.operate.entities.OperationState;
import org.camunda.operate.entities.listview.WorkflowInstanceForListViewEntity;
import org.camunda.operate.entities.listview.WorkflowInstanceState;
import org.camunda.operate.util.ElasticsearchTestRule;
import org.camunda.operate.util.OperateIntegrationTest;
import org.camunda.operate.util.TestUtil;
import org.camunda.operate.webapp.rest.dto.VariableDto;
import org.camunda.operate.webapp.rest.dto.incidents.IncidentDto;
import org.camunda.operate.webapp.rest.dto.incidents.IncidentResponseDto;
import org.camunda.operate.webapp.rest.dto.listview.ListViewRequestDto;
import org.camunda.operate.webapp.rest.dto.listview.ListViewResponseDto;
import org.camunda.operate.webapp.rest.dto.listview.ListViewWorkflowInstanceDto;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.test.web.servlet.MvcResult;
import com.fasterxml.jackson.core.type.TypeReference;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.operate.util.TestUtil.createIncident;
import static org.camunda.operate.util.TestUtil.createVariable;
import static org.camunda.operate.util.TestUtil.createWorkflowInstance;
import static org.camunda.operate.webapp.rest.WorkflowInstanceRestService.WORKFLOW_INSTANCE_URL;
import static org.mockito.Mockito.when;

/**
 * Tests retrieval of operation taking into account current user name.
 */
public class OperationReaderIT extends OperateIntegrationTest {

  private static final String QUERY_LIST_VIEW_URL = WORKFLOW_INSTANCE_URL;

  private static final Long WORKFLOW_KEY_DEMO_PROCESS = 42L;

  private static String WORKFLOW_INSTANCE_ID_1;
  private static String WORKFLOW_INSTANCE_ID_2;
  private static String WORKFLOW_INSTANCE_ID_3;
  private static final String USER_1 = "user1";
  private static final String USER_2 = "user2";
  private static final String USER_3 = "user3";
  private static final String USER_4 = "user4";
  private static final String VARNAME_1 = "var1";
  private static final String VARNAME_2 = "var2";
  private static final String VARNAME_3 = "var3";
  private static final long INCIDENT_1 = 1;
  private static final long INCIDENT_2 = 2;
  private static final long INCIDENT_3 = 3;

  @Rule
  public ElasticsearchTestRule elasticsearchTestRule = new ElasticsearchTestRule();

  @Before
  public void before() {
    super.before();
    createData(WORKFLOW_KEY_DEMO_PROCESS);
  }

  @Test
  public void testWorfklowInstanceQuery() throws Exception {
    when(userService.getCurrentUsername()).thenReturn(USER_1);

    ListViewRequestDto workflowInstanceQueryDto = TestUtil.createGetAllRunningRequest();
    MvcResult mvcResult = postRequest(queryWorkflowInstances(), workflowInstanceQueryDto);
    ListViewResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    List<ListViewWorkflowInstanceDto> workflowInstances = response.getWorkflowInstances();
    assertThat(workflowInstances).hasSize(3);
    assertThat(workflowInstances).filteredOn("id", WORKFLOW_INSTANCE_ID_1)
        .allMatch(wi -> wi.isHasActiveOperation() == true &&
            wi.getOperations().size() == 2);
    assertThat(workflowInstances).filteredOn("id", WORKFLOW_INSTANCE_ID_2)
        .allMatch(wi -> wi.isHasActiveOperation() == true &&
            wi.getOperations().size() == 1);
    assertThat(workflowInstances).filteredOn("id", WORKFLOW_INSTANCE_ID_3)
        .allMatch(wi -> wi.isHasActiveOperation() == false &&
            wi.getOperations().size() == 0);
  }

  @Test
  public void testQueryIncidentsByWorkflowInstanceId() throws Exception {
    when(userService.getCurrentUsername()).thenReturn(USER_1);

    MvcResult mvcResult = getRequest(queryIncidentsByWorkflowInstanceId(WORKFLOW_INSTANCE_ID_1));
    IncidentResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    List<IncidentDto> incidents = response.getIncidents();
    assertThat(incidents).hasSize(3);
    assertThat(incidents).filteredOn("id", INCIDENT_1)
        .allMatch(inc -> inc.isHasActiveOperation() == true &&
            inc.getLastOperation() != null);
    assertThat(incidents).filteredOn("id", INCIDENT_2)
        .allMatch(inc -> inc.isHasActiveOperation() == true &&
            inc.getLastOperation() != null);
    assertThat(incidents).filteredOn("id", INCIDENT_3)
        .allMatch(inc -> inc.isHasActiveOperation() == false &&
            inc.getLastOperation() == null);
  }

  @Test
  public void testGetVariables() throws Exception {
    when(userService.getCurrentUsername()).thenReturn(USER_3);

    MvcResult mvcResult = getRequest(queryVariables(WORKFLOW_INSTANCE_ID_2));
    List<VariableDto> variables = mockMvcTestRule.listFromResponse(mvcResult, VariableDto.class);

    assertThat(variables).hasSize(3);

    assertThat(variables).filteredOn("name", VARNAME_1)
        .allMatch(inc -> inc.isHasActiveOperation() == false);
    assertThat(variables).filteredOn("name", VARNAME_2)
        .allMatch(inc -> inc.isHasActiveOperation() == true);
    assertThat(variables).filteredOn("name", VARNAME_3)
        .allMatch(inc -> inc.isHasActiveOperation() == true);
  }

  @Test
  public void testQueryWorkflowInstanceById() throws Exception {
    when(userService.getCurrentUsername()).thenReturn(USER_4);

    MvcResult mvcResult = getRequest(queryWorkflowInstanceById(WORKFLOW_INSTANCE_ID_3));
    ListViewWorkflowInstanceDto workflowInstance = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    assertThat(workflowInstance.getOperations()).hasSize(2);
    assertThat(workflowInstance.isHasActiveOperation()).isTrue();

  }

  private String queryWorkflowInstances() {
    return QUERY_LIST_VIEW_URL;
  }

  private String queryIncidentsByWorkflowInstanceId(String workflowInstanceId) {
    return String.format("%s/%s/incidents", WORKFLOW_INSTANCE_URL, workflowInstanceId);
  }

  private String queryVariables(String workflowInstanceId) {
    return String.format("%s/%s/variables?scopeId=%s", WORKFLOW_INSTANCE_URL, workflowInstanceId, workflowInstanceId);
  }

  private String queryWorkflowInstanceById(String workflowInstanceId) {
    return String.format("%s/%s", WORKFLOW_INSTANCE_URL, workflowInstanceId);
  }

  /**
   */
  protected void createData(Long workflowKey) {

    List<OperateEntity> entities = new ArrayList<>();

    WorkflowInstanceForListViewEntity inst = createWorkflowInstance(WorkflowInstanceState.ACTIVE, workflowKey);
    WORKFLOW_INSTANCE_ID_1 = String.valueOf(inst.getKey());
    entities.add(createIncident(IncidentState.ACTIVE, INCIDENT_1, Long.valueOf(WORKFLOW_INSTANCE_ID_1)));
    entities.add(TestUtil.createOperationEntity(inst.getWorkflowInstanceKey(), INCIDENT_1, null, USER_1));
    entities.add(createIncident(IncidentState.ACTIVE, INCIDENT_2, Long.valueOf(WORKFLOW_INSTANCE_ID_1)));
    entities.add(TestUtil.createOperationEntity(inst.getWorkflowInstanceKey(), INCIDENT_2, null, USER_1));
    entities.add(createIncident(IncidentState.ACTIVE, INCIDENT_3, Long.valueOf(WORKFLOW_INSTANCE_ID_1)));
    entities.add(TestUtil.createOperationEntity(inst.getWorkflowInstanceKey(), INCIDENT_3, null, USER_2));
    entities.add(inst);

    inst = createWorkflowInstance(WorkflowInstanceState.ACTIVE, workflowKey);
    long workflowInstanceKey = inst.getKey();
    WORKFLOW_INSTANCE_ID_2 = String.valueOf(inst.getKey());
    entities.add(createVariable(workflowInstanceKey, workflowInstanceKey, VARNAME_1, "value"));
    entities.add(TestUtil.createOperationEntity(inst.getWorkflowInstanceKey(), null, VARNAME_1, USER_1));
    entities.add(createVariable(workflowInstanceKey, workflowInstanceKey, VARNAME_2, "value"));
    entities.add(TestUtil.createOperationEntity(inst.getWorkflowInstanceKey(), null, VARNAME_2, USER_3));
    entities.add(createVariable(workflowInstanceKey, workflowInstanceKey, VARNAME_3, "value"));
    entities.add(TestUtil.createOperationEntity(inst.getWorkflowInstanceKey(), null, VARNAME_3, USER_3));
    entities.add(inst);

    inst = createWorkflowInstance(WorkflowInstanceState.ACTIVE, workflowKey);
    WORKFLOW_INSTANCE_ID_3 = String.valueOf(inst.getKey());
    entities.add(TestUtil.createOperationEntity(inst.getWorkflowInstanceKey(), null, null, USER_4));
    entities.add(TestUtil.createOperationEntity(inst.getWorkflowInstanceKey(), null, null, OperationState.COMPLETED, USER_4, false));
    entities.add(inst);

    elasticsearchTestRule.persistNew(entities.toArray(new OperateEntity[entities.size()]));

  }

}
