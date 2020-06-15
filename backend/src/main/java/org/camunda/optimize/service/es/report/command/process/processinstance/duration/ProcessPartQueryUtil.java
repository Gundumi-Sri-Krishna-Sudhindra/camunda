/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process.processinstance.duration;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.ScriptedMetric;
import org.elasticsearch.search.aggregations.metrics.ScriptedMetricAggregationBuilder;

import java.util.HashMap;
import java.util.Map;

import static org.camunda.optimize.service.es.report.command.util.ElasticsearchAggregationResultMappingUtil.mapToDouble;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;
import static org.elasticsearch.search.aggregations.AggregationBuilders.scriptedMetric;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;


public class ProcessPartQueryUtil {

  private static final String SCRIPT_AGGREGATION = "scriptAggregation";
  private static final String NESTED_AGGREGATION = "nestedAggregation";
  private static final String TERMS_AGGREGATIONS = "termsAggregations";

  private ProcessPartQueryUtil(){}

  public static Double processProcessPartAggregationOperations(Aggregations aggs, AggregationType aggregationType) {
    Terms agg = aggs.get(TERMS_AGGREGATIONS);
    DescriptiveStatistics stats = new DescriptiveStatistics();
    for (Terms.Bucket entry : agg.getBuckets()) {
      Nested nested = entry.getAggregations().get(NESTED_AGGREGATION);
      ScriptedMetric scriptedMetric = nested.getAggregations().get(SCRIPT_AGGREGATION);

      if (scriptedMetric.aggregation() instanceof Number) {
        final Number scriptedResult = (Number) scriptedMetric.aggregation();
        stats.addValue(scriptedResult.longValue());
      }
    }
    return getResultForGivenAggregationType(stats, aggregationType);
  }

  private static Double getResultForGivenAggregationType(DescriptiveStatistics stats, AggregationType aggregationType) {
    switch (aggregationType) {
      case MIN:
        return mapToDouble(stats.getMin());
      case MAX:
        return mapToDouble(stats.getMax());
      case AVERAGE:
        return mapToDouble(stats.getMean());
      case MEDIAN:
        return mapToDouble(stats.getPercentile(50));
      default:
        throw new OptimizeRuntimeException(String.format("Unknown aggregation type [%s]", aggregationType));
    }
  }

  public static BoolQueryBuilder addProcessPartQuery(BoolQueryBuilder boolQueryBuilder,
                                                     String startFlowNodeId,
                                                     String endFlowNodeId) {
    String termPath = ProcessInstanceIndex.EVENTS + "." + ProcessInstanceIndex.ACTIVITY_ID;
    boolQueryBuilder.must(nestedQuery(
      ProcessInstanceIndex.EVENTS,
      termQuery(termPath, startFlowNodeId),
      ScoreMode.None
                          )
    );
    boolQueryBuilder.must(nestedQuery(
      ProcessInstanceIndex.EVENTS,
      termQuery(termPath, endFlowNodeId),
      ScoreMode.None
                          )
    );
    return boolQueryBuilder;
  }

  public static AggregationBuilder createProcessPartAggregation(String startFlowNodeId, String endFlowNodeId) {
    Map<String, Object> params = new HashMap<>();
    params.put("startFlowNodeId", startFlowNodeId);
    params.put("endFlowNodeId", endFlowNodeId);

    ScriptedMetricAggregationBuilder findStartAndEndDatesForEvents = scriptedMetric(SCRIPT_AGGREGATION)
      .initScript(createInitScript())
      .mapScript(createMapScript())
      .combineScript(createCombineScript())
      .reduceScript(getReduceScript())
      .params(params);
    NestedAggregationBuilder searchThroughTheEvents =
      nested(NESTED_AGGREGATION, ProcessInstanceIndex.EVENTS);
    return
      terms(TERMS_AGGREGATIONS)
        .field(ProcessInstanceIndex.PROCESS_INSTANCE_ID)
        .subAggregation(
          searchThroughTheEvents
            .subAggregation(
              findStartAndEndDatesForEvents
            )
        );
  }

  private static Script createInitScript() {
    // @formatter:off
    return new Script(
        "state.starts = [];" +
        "state.ends = []"
    );
    // @formatter:on
  }

  private static Script createMapScript() {
    // @formatter:off
    return new Script(
      "if(doc['events.activityId'].value == params.startFlowNodeId && " +
          "doc['events.startDate'].size() != 0 && doc['events.startDate'].value != null && " +
          "doc['events.startDate'].value.toInstant().toEpochMilli() != 0) {" +
        "long startDateInMillis = doc['events.startDate'].value.toInstant().toEpochMilli();" +
        "state.starts.add(startDateInMillis);" +
      "} else if(doc['events.activityId'].value == params.endFlowNodeId && " +
          "doc['events.endDate'].size() != 0 && doc['events.endDate'].value != null && " +
          "doc['events.endDate'].value.toInstant().toEpochMilli() != 0) {" +
        "long endDateInMillis = doc['events.endDate'].value.toInstant().toEpochMilli();" +
        "state.ends.add(endDateInMillis);" +
      "}"
    );
    // @formatter:on
  }

  private static Script createCombineScript() {
    // @formatter:off
    return new Script(
        "if (!state.starts.isEmpty() && !state.ends.isEmpty()) {" +
        "long minStart = state.starts.stream().min(Long::compareTo).get(); " +
        "List endsLargerMinStart = state.ends.stream().filter(e -> e >= minStart).collect(Collectors.toList());" +
        "if (!endsLargerMinStart.isEmpty()) {" +
          "long closestEnd = endsLargerMinStart.stream()" +
            ".min(Comparator.comparingDouble(v -> Math.abs(v - minStart))).get();" +
          "return closestEnd-minStart;" +
        "}" +
      "}" +
      "return null;"
    );
    // @formatter:on
  }

  private static Script getReduceScript() {
    // @formatter:off
    return new Script(
      "if (states.size() == 1) {" +
        "return states.get(0);" +
      "}" +
      "long sum = 0; " +
      "for (a in states) { " +
        "if (a != null) {" +
          "sum += a " +
        "}" +
      "} " +
      "return sum / Math.max(1, states.size());"
    );
    // @formatter:on
  }
}
