package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.schema.type.ProcessDefinitionType.FLOW_NODE_NAMES;
import static org.camunda.optimize.service.es.schema.type.ProcessDefinitionType.PROCESS_DEFINITION_XML;

@Component
public class ProcessDefinitionXmlWriter {

  private final Logger logger = LoggerFactory.getLogger(ProcessDefinitionXmlWriter.class);

  @Autowired
  private TransportClient esclient;
  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private ObjectMapper objectMapper;

  public void importProcessDefinitionXmls(List<ProcessDefinitionOptimizeDto> xmls) {
    logger.debug("writing [{}] process definition XMLs to ES", xmls.size());
    BulkRequestBuilder processDefinitionXmlBulkRequest = esclient.prepareBulk();

    for (ProcessDefinitionOptimizeDto procDefXml : xmls) {
      addImportProcessDefinitionXmlRequest(processDefinitionXmlBulkRequest, procDefXml);
    }

    if (processDefinitionXmlBulkRequest.numberOfActions() > 0 ) {
      BulkResponse response = processDefinitionXmlBulkRequest.get();
      if (response.hasFailures()) {
        logger.warn("There were failures while writing process definition xml information. " +
            "Received error message: {}",
          response.buildFailureMessage()
        );
      }
    } else {
      logger.warn("Cannot import empty list of process definition xmls.");
    }
  }

  private void addImportProcessDefinitionXmlRequest(BulkRequestBuilder bulkRequest,
                                                    ProcessDefinitionOptimizeDto newEntryIfAbsent) {

    Map<String, Object> params = new HashMap<>();
    params.put(FLOW_NODE_NAMES, newEntryIfAbsent.getFlowNodeNames());
    params.put(PROCESS_DEFINITION_XML, newEntryIfAbsent.getBpmn20Xml());

    Script updateScript = new Script(
        ScriptType.INLINE,
        Script.DEFAULT_SCRIPT_LANG,
        "ctx._source.flowNodeNames = params.flowNodeNames; " +
            "ctx._source.bpmn20Xml = params.bpmn20Xml; ",
        params
    );

    String source = null;
    try {
      source = objectMapper.writeValueAsString(newEntryIfAbsent);
    } catch (JsonProcessingException e) {
      logger.error("can't serialize to JSON", e);
    }

    bulkRequest.add(esclient
        .prepareUpdate(
          getOptimizeIndexAliasForType(ElasticsearchConstants.PROC_DEF_TYPE),
          ElasticsearchConstants.PROC_DEF_TYPE,
          newEntryIfAbsent.getId()
        )
        .setScript(updateScript)
        .setUpsert(source, XContentType.JSON)
        .setRetryOnConflict(configurationService.getNumberOfRetriesOnConflict())
    );
  }
}
