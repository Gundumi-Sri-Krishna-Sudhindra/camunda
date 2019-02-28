package org.camunda.optimize.service.importing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.importing.SimpleUserTaskInstanceDto;
import org.camunda.optimize.dto.optimize.importing.UserOperationDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.util.OptimizeDateTimeFormatterFactory;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.mapper.ObjectMapperFactory;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import java.io.IOException;
import java.sql.SQLException;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROC_INSTANCE_TYPE;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.not;


public class UserTaskImportIT {

  private static final String START_EVENT = "startEvent";
  private static final String END_EVENT = "endEvent";
  private static final String USER_TASK_1 = "userTask1";
  private static final String USER_TASK_2 = "userTask2";

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  public EngineDatabaseRule engineDatabaseRule = new EngineDatabaseRule();

  private ObjectMapper objectMapper;

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule).around(engineDatabaseRule);

  @Before
  public void setUp() throws Exception {
    if (objectMapper == null) {
      objectMapper = new ObjectMapperFactory(
        new OptimizeDateTimeFormatterFactory().getObject(),
        new ConfigurationService()
      ).createOptimizeMapper();
    }
  }

  @Test
  public void userTasksAreImported() throws IOException {
    // given
    deployAndStartTwoUserTasksProcess();
    engineRule.finishAllUserTasks();
    engineRule.finishAllUserTasks();

    // when
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // then
    SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(PROC_INSTANCE_TYPE);
    assertThat(idsResp.getHits().getTotalHits(), is(1L));
    for (SearchHit searchHitFields : idsResp.getHits()) {
      final ProcessInstanceDto processInstanceDto = objectMapper.readValue(
        searchHitFields.getSourceAsString(), ProcessInstanceDto.class
      );
      assertThat(processInstanceDto.getUserTasks().size(), is(2));
      assertThat(
        processInstanceDto.getUserTasks().stream().map(SimpleUserTaskInstanceDto::getActivityId).collect(toList()),
        containsInAnyOrder(USER_TASK_1, USER_TASK_2)

      );
      processInstanceDto.getUserTasks().stream().forEach(simpleUserTaskInstanceDto -> {
        assertThat(simpleUserTaskInstanceDto.getId(), is(notNullValue()));
        assertThat(simpleUserTaskInstanceDto.getActivityId(), is(notNullValue()));
        assertThat(simpleUserTaskInstanceDto.getActivityInstanceId(), is(notNullValue()));
        assertThat(simpleUserTaskInstanceDto.getStartDate(), is(notNullValue()));
        assertThat(simpleUserTaskInstanceDto.getEndDate(), is(notNullValue()));
        assertThat(simpleUserTaskInstanceDto.getDueDate(), is(nullValue()));
        assertThat(simpleUserTaskInstanceDto.getDeleteReason(), is(notNullValue()));
        assertThat(simpleUserTaskInstanceDto.getTotalDurationInMs(), is(notNullValue()));
        assertThat(simpleUserTaskInstanceDto.getIdleDurationInMs(), is(notNullValue()));
        assertThat(simpleUserTaskInstanceDto.getWorkDurationInMs(), is(notNullValue()));
        assertThat(simpleUserTaskInstanceDto.getUserOperations(), is(notNullValue()));
      });
    }
  }

  @Test
  public void onlyCompletedUserTasksAreImported() throws IOException {
    // given
    deployAndStartTwoUserTasksProcess();
    engineRule.finishAllUserTasks();

    // when
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // then
    SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(PROC_INSTANCE_TYPE);
    assertThat(idsResp.getHits().getTotalHits(), is(1L));
    for (SearchHit searchHitFields : idsResp.getHits()) {
      final ProcessInstanceDto processInstanceDto = objectMapper.readValue(
        searchHitFields.getSourceAsString(), ProcessInstanceDto.class
      );
      assertThat(processInstanceDto.getUserTasks().size(), is(1));
      assertThat(
        processInstanceDto.getUserTasks().stream().map(SimpleUserTaskInstanceDto::getActivityId).collect(toList()),
        containsInAnyOrder(USER_TASK_1)
      );
    }
  }

  @Test
  public void onlyUserTasksRelatedToProcessInstancesAreImported() throws IOException {
    // given
    deployAndStartTwoUserTasksProcess();
    final UUID independentUserTaskId = engineRule.createIndependentUserTask();
    engineRule.finishAllUserTasks();

    // when
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // then
    SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(PROC_INSTANCE_TYPE);
    assertThat(idsResp.getHits().getTotalHits(), is(1L));
    for (SearchHit searchHitFields : idsResp.getHits()) {
      final ProcessInstanceDto processInstanceDto = objectMapper.readValue(
        searchHitFields.getSourceAsString(), ProcessInstanceDto.class
      );
      assertThat(processInstanceDto.getUserTasks().size(), is(1));
      assertThat(
        processInstanceDto.getUserTasks().stream().map(SimpleUserTaskInstanceDto::getActivityId).collect(toList()),
        containsInAnyOrder(USER_TASK_1)
      );
      assertThat(
        processInstanceDto.getUserTasks().stream().map(SimpleUserTaskInstanceDto::getId).collect(toList()),
        not(containsInAnyOrder(independentUserTaskId))
      );
    }
  }

  @Test
  public void noSideEffectsByOtherProcessInstanceUserTasks() throws IOException {
    // given
    final ProcessInstanceEngineDto processInstanceDto1 = deployAndStartTwoUserTasksProcess();
    engineRule.finishAllUserTasks();
    engineRule.finishAllUserTasks();

    final ProcessInstanceEngineDto processInstanceDto2 = deployAndStartTwoUserTasksProcess();
    // only first task finished
    engineRule.finishAllUserTasks();

    // when
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // then
    SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(PROC_INSTANCE_TYPE);
    assertThat(idsResp.getHits().getTotalHits(), is(2L));
    for (SearchHit searchHitFields : idsResp.getHits()) {
      final ProcessInstanceDto persistedProcessInstanceDto = objectMapper.readValue(
        searchHitFields.getSourceAsString(), ProcessInstanceDto.class
      );
      if (persistedProcessInstanceDto.getProcessInstanceId().equals(processInstanceDto1.getId())) {
        assertThat(persistedProcessInstanceDto.getUserTasks().size(), is(2));
        assertThat(
          persistedProcessInstanceDto.getUserTasks()
            .stream()
            .map(SimpleUserTaskInstanceDto::getActivityId)
            .collect(toList()),
          containsInAnyOrder(USER_TASK_1, USER_TASK_2)
        );
      } else {
        assertThat(persistedProcessInstanceDto.getUserTasks().size(), is(1));
        assertThat(
          persistedProcessInstanceDto.getUserTasks()
            .stream()
            .map(SimpleUserTaskInstanceDto::getActivityId)
            .collect(toList()),
          containsInAnyOrder(USER_TASK_1)
        );
      }
    }
  }

  @Test
  public void userOperationsAreImported() throws IOException {
    // given
    deployAndStartTwoUserTasksProcess();
    engineRule.finishAllUserTasks();
    engineRule.finishAllUserTasks();

    // when
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // then
    final SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(PROC_INSTANCE_TYPE);
    assertThat(idsResp.getHits().getTotalHits(), is(1L));
    for (SearchHit searchHitFields : idsResp.getHits()) {
      final ProcessInstanceDto persistedProcessInstanceDto = objectMapper.readValue(
        searchHitFields.getSourceAsString(), ProcessInstanceDto.class
      );
      persistedProcessInstanceDto.getUserTasks()
        .forEach(userTask -> {
          assertThat(userTask.getUserOperations().size(), is(2));
          assertThat(
            userTask.getUserOperations().stream().map(UserOperationDto::getType).collect(toList()),
            containsInAnyOrder("Claim", "Complete")
          );
          userTask.getUserOperations().stream().forEach(userOperationDto -> {
            assertThat(userOperationDto.getId(), is(notNullValue()));
            assertThat(userOperationDto.getUserId(), is(notNullValue()));
            assertThat(userOperationDto.getTimestamp(), is(notNullValue()));
            assertThat(userOperationDto.getType(), is(notNullValue()));
            assertThat(userOperationDto.getProperty(), is(notNullValue()));
            assertThat(userOperationDto.getNewValue(), is(notNullValue()));
          });
        });
    }
  }

  @Test
  public void onlyUserOperationsRelatedToProcessInstancesAreImported() throws IOException {
    // given
    deployAndStartTwoUserTasksProcess();
    engineRule.createIndependentUserTask();
    engineRule.finishAllUserTasks();

    // when
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // then
    SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(PROC_INSTANCE_TYPE);
    assertThat(idsResp.getHits().getTotalHits(), is(1L));
    for (SearchHit searchHitFields : idsResp.getHits()) {
      final ProcessInstanceDto processInstanceDto = objectMapper.readValue(
        searchHitFields.getSourceAsString(), ProcessInstanceDto.class
      );
      assertThat(processInstanceDto.getUserTasks().size(), is(1));
      processInstanceDto.getUserTasks()
        .forEach(userTask -> assertThat(userTask.getUserOperations().size(), is(2)));
    }
  }

  @Test
  public void defaultIdleTimeOnNoClaimOperation() throws IOException {
    // given
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartTwoUserTasksProcess();
    engineRule.completeUserTaskWithoutClaim(processInstanceDto.getId());

    // when
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // then
    final SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(PROC_INSTANCE_TYPE);
    assertThat(idsResp.getHits().getTotalHits(), is(1L));
    for (SearchHit searchHitFields : idsResp.getHits()) {
      final ProcessInstanceDto persistedProcessInstanceDto = objectMapper.readValue(
        searchHitFields.getSourceAsString(),
        ProcessInstanceDto.class
      );
      persistedProcessInstanceDto.getUserTasks()
        .forEach(userTask -> {
          assertThat(userTask.getIdleDurationInMs(), is(0L));
        });
    }
  }

  @Test
  public void idleTimeMetricIsCalculatedOnClaimOperationImport() throws IOException {
    // given
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartTwoUserTasksProcess();
    engineRule.finishAllUserTasks();
    final long idleDuration = 500;
    changeUserTaskIdleDuration(processInstanceDto, idleDuration);

    // when
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // then
    final SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(PROC_INSTANCE_TYPE);
    assertThat(idsResp.getHits().getTotalHits(), is(1L));
    for (SearchHit searchHitFields : idsResp.getHits()) {
      final ProcessInstanceDto persistedProcessInstanceDto = objectMapper.readValue(
        searchHitFields.getSourceAsString(),
        ProcessInstanceDto.class
      );
      persistedProcessInstanceDto.getUserTasks()
        .forEach(userTask -> {
          assertThat(userTask.getIdleDurationInMs(), is(idleDuration));
        });
    }
  }


  @Test
  public void defaultWorkTimeOnNoClaimOperation() throws IOException {
    // given
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartTwoUserTasksProcess();
    engineRule.completeUserTaskWithoutClaim(processInstanceDto.getId());

    // when
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // then
    final SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(PROC_INSTANCE_TYPE);
    assertThat(idsResp.getHits().getTotalHits(), is(1L));
    for (SearchHit searchHitFields : idsResp.getHits()) {
      final ProcessInstanceDto persistedProcessInstanceDto = objectMapper.readValue(
        searchHitFields.getSourceAsString(),
        ProcessInstanceDto.class
      );
      persistedProcessInstanceDto.getUserTasks()
        .forEach(userTask -> {
          assertThat(userTask.getWorkDurationInMs(), is(userTask.getTotalDurationInMs()));
        });
    }
  }


  @Test
  public void workTimeMetricIsCalculatedOnClaimOperationImport() throws IOException {
    // given
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartTwoUserTasksProcess();
    engineRule.finishAllUserTasks();
    final long workDuration = 500;
    changeUserTaskWorkDuration(processInstanceDto, workDuration);

    // when
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // then
    final SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(PROC_INSTANCE_TYPE);
    assertThat(idsResp.getHits().getTotalHits(), is(1L));
    for (SearchHit searchHitFields : idsResp.getHits()) {
      final ProcessInstanceDto persistedProcessInstanceDto = objectMapper.readValue(
        searchHitFields.getSourceAsString(),
        ProcessInstanceDto.class
      );
      persistedProcessInstanceDto.getUserTasks()
        .forEach(userTask -> {
          assertThat(userTask.getWorkDurationInMs(), is(workDuration));
        });
    }
  }

  private void changeUserTaskIdleDuration(final ProcessInstanceEngineDto processInstanceDto, final long idleDuration) {
    engineRule.getHistoricTaskInstances(processInstanceDto.getId())
      .forEach(historicUserTaskInstanceDto -> {
        try {
          engineDatabaseRule.changeUserTaskClaimOperationTimestamp(
            processInstanceDto.getId(),
            historicUserTaskInstanceDto.getId(),
            historicUserTaskInstanceDto.getStartTime().plus(idleDuration, ChronoUnit.MILLIS)
          );
        } catch (SQLException e) {
          throw new OptimizeIntegrationTestException(e);
        }
      });
  }

  private void changeUserTaskWorkDuration(final ProcessInstanceEngineDto processInstanceDto, final long workDuration) {
    engineRule.getHistoricTaskInstances(processInstanceDto.getId())
      .forEach(historicUserTaskInstanceDto -> {
        if (historicUserTaskInstanceDto.getEndTime() != null) {
          try {
            engineDatabaseRule.changeUserTaskClaimOperationTimestamp(
              processInstanceDto.getId(),
              historicUserTaskInstanceDto.getId(),
              historicUserTaskInstanceDto.getEndTime().minus(workDuration, ChronoUnit.MILLIS)
            );
          } catch (SQLException e) {
            throw new OptimizeIntegrationTestException(e);
          }
        }
      });
  }

  private ProcessInstanceEngineDto deployAndStartTwoUserTasksProcess() {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .startEvent(START_EVENT)
      .userTask(USER_TASK_1)
      .userTask(USER_TASK_2)
      .endEvent(END_EVENT)
      .done();
    return engineRule.deployAndStartProcess(processModel);
  }

  private SearchResponse getSearchResponseForAllDocumentsOfType(String elasticsearchType) throws IOException {
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(matchAllQuery())
      .size(100);

    SearchRequest searchRequest = new SearchRequest()
      .indices(getOptimizeIndexAliasForType(elasticsearchType))
      .types(elasticsearchType)
      .source(searchSourceBuilder);

    return elasticSearchRule.getEsClient().search(searchRequest, RequestOptions.DEFAULT);
  }


}
