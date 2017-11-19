package org.camunda.optimize.plugin.adapter.variable;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.variable.VariableRetrievalDto;
import org.camunda.optimize.plugin.ImportAdapterProvider;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/it/it-applicationContext.xml"})
public class VariableImportAdapterPluginIT {

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  private ConfigurationService configurationService;
  private ImportAdapterProvider pluginProvider;

  @Before
  public void setup() throws IOException {
    configurationService = embeddedOptimizeRule.getConfigurationService();
    pluginProvider = embeddedOptimizeRule.getApplicationContext().getBean(ImportAdapterProvider.class);
  }

  @After
  public void resetBasePackage() {
    configurationService.setVariableImportPluginBasePackages(new ArrayList<>());
    pluginProvider.resetAdapters();
  }

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  @Test
  public void variableImportCanBeAdaptedByPlugin() throws Exception {
    // given
    addVariableImportPluginBasePackagesToConfiguration("org.camunda.optimize.plugin.adapter.variable.util1");
    pluginProvider.resetAdapters();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", 1);
    variables.put("var2", 1);
    variables.put("var3", 1);
    variables.put("var4", 1);
    String processDefinitionId = deploySimpleServiceTaskWithVariables(variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<VariableRetrievalDto> variablesResponseDtos = getVariables(processDefinitionId);

    //then only half the variables are added to Optimize
    assertThat(variablesResponseDtos.size(), is(2));
  }

  @Test
  public void variableImportCanBeAdaptedBySeveralPlugins() throws Exception {
    // given
    addVariableImportPluginBasePackagesToConfiguration(
      "org.camunda.optimize.plugin.adapter.variable.util1",
      "org.camunda.optimize.plugin.adapter.variable.util2");
    pluginProvider.resetAdapters();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", "bar");
    variables.put("var2", "bar");
    variables.put("var3", "bar");
    variables.put("var4", "bar");
    String processDefinitionId = deploySimpleServiceTaskWithVariables(variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<VariableRetrievalDto> variablesResponseDtos = getVariables(processDefinitionId);

    //then only half the variables are added to Optimize
    assertThat(variablesResponseDtos.size(), is(2));
  }

  @Test
  public void adapterWithoutDefaultConstructorIsNotAdded() throws Exception {
    // given
    addVariableImportPluginBasePackagesToConfiguration("org.camunda.optimize.plugin.adapter.variable.error1");
    pluginProvider.resetAdapters();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", 1);
    variables.put("var2", 1);
    String processDefinitionId = deploySimpleServiceTaskWithVariables(variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<VariableRetrievalDto> variablesResponseDtos = getVariables(processDefinitionId);

    //then only half the variables are added to Optimize
    assertThat(variablesResponseDtos.size(), is(2));
  }

  @Test
  public void notExistingAdapterDoesNotStopImportProcess() throws Exception {
    // given
    addVariableImportPluginBasePackagesToConfiguration("foo.bar");
    pluginProvider.resetAdapters();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", 1);
    variables.put("var2", 1);
    String processDefinitionId = deploySimpleServiceTaskWithVariables(variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<VariableRetrievalDto> variablesResponseDtos = getVariables(processDefinitionId);

    //then only half the variables are added to Optimize
    assertThat(variablesResponseDtos.size(), is(2));
  }

  @Test
  public void adapterWithDefaultConstructorThrowingErrorDoesNotStopImportProcess() throws Exception {
    // given
    addVariableImportPluginBasePackagesToConfiguration("org.camunda.optimize.plugin.adapter.variable.error2");
    pluginProvider.resetAdapters();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", 1);
    variables.put("var2", 1);
    String processDefinitionId = deploySimpleServiceTaskWithVariables(variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<VariableRetrievalDto> variablesResponseDtos = getVariables(processDefinitionId);

    //then only half the variables are added to Optimize
    assertThat(variablesResponseDtos.size(), is(2));
  }

  @Test
  public void adapterCanBeUsedToEnrichVariableImport() throws Exception {
    // given
    addVariableImportPluginBasePackagesToConfiguration("org.camunda.optimize.plugin.adapter.variable.util3");
    pluginProvider.resetAdapters();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", 1);
    variables.put("var2", 1);
    String processDefinitionId = deploySimpleServiceTaskWithVariables(variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<VariableRetrievalDto> variablesResponseDtos = getVariables(processDefinitionId);

    //then extra variable is added to Optimize
    assertThat(variablesResponseDtos.size(), is(3));
  }

  @Test
  public void invalidPluginVariablesAreNotAddedToVariableImport() throws Exception {
    // given
    addVariableImportPluginBasePackagesToConfiguration("org.camunda.optimize.plugin.adapter.variable.util4");
    pluginProvider.resetAdapters();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", 1);
    String processDefinitionId = deploySimpleServiceTaskWithVariables(variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<VariableRetrievalDto> variablesResponseDtos = getVariables(processDefinitionId);

    //then only half the variables are added to Optimize
    assertThat(variablesResponseDtos.size(), is(1));
    assertThat(variablesResponseDtos.get(0).getName(), is("var"));
  }

  private List<VariableRetrievalDto> getVariables(String processDefinitionId) {
    String token = embeddedOptimizeRule.getAuthenticationToken();
    List<VariableRetrievalDto> variablesResponseDtos =
      embeddedOptimizeRule.target("variables/" + processDefinitionId)
        .request()
        .header(HttpHeaders.AUTHORIZATION,"Bearer " + token)
        .get(new GenericType<List<VariableRetrievalDto>>(){});
    return variablesResponseDtos;
  }

  private String deploySimpleServiceTaskWithVariables(Map<String, Object> variables) throws Exception {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess" + System.currentTimeMillis())
      .name("aProcessName" + System.currentTimeMillis())
        .startEvent()
        .serviceTask()
          .camundaExpression("${true}")
        .endEvent()
      .done();
    ProcessInstanceEngineDto procInstance = engineRule.deployAndStartProcessWithVariables(processModel, variables);
    engineRule.waitForAllProcessesToFinish();
    return procInstance.getDefinitionId();
  }

  private void addVariableImportPluginBasePackagesToConfiguration(String... basePackages) {
    List<String> basePackagesList = Arrays.asList(basePackages);
    configurationService.setVariableImportPluginBasePackages(basePackagesList);
  }

}
