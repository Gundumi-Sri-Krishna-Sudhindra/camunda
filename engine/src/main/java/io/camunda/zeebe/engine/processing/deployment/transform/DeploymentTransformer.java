/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.transform;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapArray;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static java.util.Map.entry;

import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.BpmnFactory;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.BpmnTransformer;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.KeyGenerator;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ZeebeState;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.Process;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentResource;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.io.DirectBufferInputStream;
import org.slf4j.Logger;

public final class DeploymentTransformer {

  private static final Logger LOG = Loggers.PROCESS_PROCESSOR_LOGGER;

  private static final DeploymentResourceTransformer UNKNOWN_RESOURCE =
      new UnknownResourceTransformer();

  private final Map<String, DeploymentResourceTransformer> resourceTransformers;

  private final ProcessRecord processRecord = new ProcessRecord();

  private final BpmnTransformer bpmnTransformer = BpmnFactory.createTransformer();

  private final BpmnValidator validator;
  private final ProcessState processState;
  private final KeyGenerator keyGenerator;
  private final MessageDigest digestGenerator;
  // process id duplicate checking
  private final Map<String, String> processIdToResourceName = new HashMap<>();
  // internal changes during processing
  private RejectionType rejectionType;
  private String rejectionReason;
  private final StateWriter stateWriter;

  public DeploymentTransformer(
      final StateWriter stateWriter,
      final ZeebeState zeebeState,
      final ExpressionProcessor expressionProcessor,
      final KeyGenerator keyGenerator) {
    this.stateWriter = stateWriter;
    processState = zeebeState.getProcessState();
    this.keyGenerator = keyGenerator;
    validator = BpmnFactory.createValidator(expressionProcessor);

    try {
      // We get an alert by LGTM, since MD5 is a weak cryptographic hash function,
      // but it is not easy to exchange this weak algorithm without getting compatibility issues
      // with previous versions. Furthermore it is very unlikely that we get problems on checking
      // the deployments hashes.
      digestGenerator =
          MessageDigest.getInstance("MD5"); // lgtm [java/weak-cryptographic-algorithm]
    } catch (final NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }

    resourceTransformers =
        Map.ofEntries(
            entry(".bpmn", new BpmnResourceTransformer()),
            entry(".xml", new BpmnResourceTransformer()),
            entry(
                ".dmn", new DmnResourceTransformer(keyGenerator, stateWriter, this::getChecksum)));
  }

  public boolean transform(final DeploymentRecord deploymentEvent) {
    final StringBuilder errors = new StringBuilder();
    boolean success = true;
    processIdToResourceName.clear();

    final Iterator<DeploymentResource> resourceIterator = deploymentEvent.resources().iterator();
    if (!resourceIterator.hasNext()) {
      rejectionType = RejectionType.INVALID_ARGUMENT;
      rejectionReason = "Expected to deploy at least one resource, but none given";
      return false;
    }

    while (resourceIterator.hasNext()) {
      final DeploymentResource deploymentResource = resourceIterator.next();
      success &= transformResource(deploymentEvent, errors, deploymentResource);
    }

    if (!success) {
      rejectionType = RejectionType.INVALID_ARGUMENT;
      rejectionReason =
          String.format(
              "Expected to deploy new resources, but encountered the following errors:%s",
              errors.toString());
    }

    return success;
  }

  private boolean transformResource(
      final DeploymentRecord deploymentEvent,
      final StringBuilder errors,
      final DeploymentResource deploymentResource) {
    final String resourceName = deploymentResource.getResourceName();

    final var transformer = getResourceTransformer(resourceName);

    try {
      final var result = transformer.transformResource(deploymentResource, deploymentEvent);

      if (result.isRight()) {
        return true;
      } else {
        final var failureMessage = result.getLeft().getMessage();
        errors.append("\n").append(failureMessage);
        return false;
      }

    } catch (final RuntimeException e) {
      LOG.error("Unexpected error while processing resource '{}'", resourceName, e);
      errors.append("\n'").append(resourceName).append("': ").append(e.getMessage());
    }
    return false;
  }

  private String checkForDuplicateBpmnId(
      final BpmnModelInstance model, final String currentResource) {
    final Collection<Process> processes =
        model.getDefinitions().getChildElementsByType(Process.class);

    for (final Process process : processes) {
      final String previousResource = processIdToResourceName.get(process.getId());
      if (previousResource != null) {
        return String.format(
            "Duplicated process id in resources '%s' and '%s'", previousResource, currentResource);
      }

      processIdToResourceName.put(process.getId(), currentResource);
    }

    return null;
  }

  private void transformProcessResource(
      final DeploymentRecord deploymentEvent,
      final DeploymentResource deploymentResource,
      final BpmnModelInstance definition) {
    final Collection<Process> processes =
        definition.getDefinitions().getChildElementsByType(Process.class);

    for (final Process process : processes) {
      if (process.isExecutable()) {
        final String bpmnProcessId = process.getId();
        final DeployedProcess lastProcess =
            processState.getLatestProcessVersionByProcessId(BufferUtil.wrapString(bpmnProcessId));

        final DirectBuffer lastDigest =
            processState.getLatestVersionDigest(wrapString(bpmnProcessId));
        final DirectBuffer resourceDigest =
            new UnsafeBuffer(digestGenerator.digest(deploymentResource.getResource()));

        // adds process record to deployment record
        final var processMetadata = deploymentEvent.processesMetadata().add();
        processMetadata
            .setBpmnProcessId(BufferUtil.wrapString(process.getId()))
            .setChecksum(resourceDigest)
            .setResourceName(deploymentResource.getResourceNameBuffer());

        final var isDuplicate =
            isDuplicateOfLatest(deploymentResource, resourceDigest, lastProcess, lastDigest);
        if (isDuplicate) {
          processMetadata
              .setVersion(lastProcess.getVersion())
              .setKey(lastProcess.getKey())
              .markAsDuplicate();
        } else {
          final var key = keyGenerator.nextKey();
          processMetadata.setKey(key).setVersion(processState.getProcessVersion(bpmnProcessId) + 1);

          processRecord.reset();
          processRecord.wrap(processMetadata, deploymentResource.getResource());
          stateWriter.appendFollowUpEvent(key, ProcessIntent.CREATED, processRecord);
        }
      }
    }
  }

  private boolean isDuplicateOfLatest(
      final DeploymentResource deploymentResource,
      final DirectBuffer resourceDigest,
      final DeployedProcess lastProcess,
      final DirectBuffer lastVersionDigest) {
    return lastVersionDigest != null
        && lastProcess != null
        && lastVersionDigest.equals(resourceDigest)
        && lastProcess.getResourceName().equals(deploymentResource.getResourceNameBuffer());
  }

  private BpmnModelInstance readProcessDefinition(final DeploymentResource deploymentResource) {
    final DirectBuffer resource = deploymentResource.getResourceBuffer();
    final DirectBufferInputStream resourceStream = new DirectBufferInputStream(resource);
    return Bpmn.readModelFromStream(resourceStream);
  }

  public RejectionType getRejectionType() {
    return rejectionType;
  }

  public String getRejectionReason() {
    return rejectionReason;
  }

  private DeploymentResourceTransformer getResourceTransformer(String resourceName) {
    return resourceTransformers.entrySet().stream()
        .filter(entry -> resourceName.endsWith(entry.getKey()))
        .map(Entry::getValue)
        .findFirst()
        .orElse(UNKNOWN_RESOURCE);
  }

  private DirectBuffer getChecksum(final DeploymentResource resource) {
    return wrapArray(digestGenerator.digest(resource.getResource()));
  }

  interface DeploymentResourceTransformer {
    Either<Failure, Void> transformResource(DeploymentResource resource, DeploymentRecord record);
  }

  private class BpmnResourceTransformer implements DeploymentResourceTransformer {

    @Override
    public Either<Failure, Void> transformResource(
        final DeploymentResource resource, final DeploymentRecord record) {

      final BpmnModelInstance definition = readProcessDefinition(resource);
      final String validationError = validator.validate(definition);

      if (validationError == null) {
        // transform the model to avoid unexpected failures that are not covered by the validator
        bpmnTransformer.transformDefinitions(definition);

        final String bpmnIdDuplicateError =
            checkForDuplicateBpmnId(definition, resource.getResourceName());

        if (bpmnIdDuplicateError == null) {
          transformProcessResource(record, resource, definition);
          return Either.right(null);
        } else {
          return Either.left(new Failure(bpmnIdDuplicateError));
        }
      } else {
        final var failureMessage =
            String.format("'%s': %s", resource.getResourceName(), validationError);
        return Either.left(new Failure(failureMessage));
      }
    }
  }

  private static class UnknownResourceTransformer implements DeploymentResourceTransformer {

    @Override
    public Either<Failure, Void> transformResource(
        final DeploymentResource resource, final DeploymentRecord record) {

      final var failureMessage =
          String.format("%n'%s': unknown resource type", resource.getResourceName());
      return Either.left(new Failure(failureMessage));
    }
  }
}
