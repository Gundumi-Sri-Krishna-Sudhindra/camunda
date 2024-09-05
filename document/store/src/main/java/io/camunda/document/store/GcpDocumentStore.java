/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import io.camunda.document.api.DocumentCreationRequest;
import io.camunda.document.api.DocumentLink;
import io.camunda.document.api.DocumentMetadataModel;
import io.camunda.document.api.DocumentOperationResponse;
import io.camunda.document.api.DocumentOperationResponse.DocumentErrorCode;
import io.camunda.document.api.DocumentOperationResponse.Failure;
import io.camunda.document.api.DocumentOperationResponse.Success;
import io.camunda.document.api.DocumentReference;
import io.camunda.document.api.DocumentStore;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class GcpDocumentStore implements DocumentStore {

  private final String bucketName;
  private final Storage storage;

  private final ObjectMapper objectMapper;

  public GcpDocumentStore(final String bucketName) {
    this(bucketName, new ObjectMapper());
  }

  public GcpDocumentStore(final String bucketName, final ObjectMapper objectMapper) {
    this.bucketName = bucketName;
    storage = StorageOptions.getDefaultInstance().getService();
    this.objectMapper = objectMapper;
  }

  public GcpDocumentStore(
      final String bucketName, final Storage storage, final ObjectMapper objectMapper) {
    this.bucketName = bucketName;
    this.storage = storage;
    this.objectMapper = objectMapper;
  }

  @Override
  public DocumentOperationResponse<DocumentReference> createDocument(
      final DocumentCreationRequest request) {

    final String documentId =
        Optional.of(request.documentId()).orElse(UUID.randomUUID().toString());
    final Blob existingBlob;
    try {
      existingBlob = storage.get(bucketName, documentId);
    } catch (final Exception e) {
      return new Failure<>(DocumentErrorCode.UNKNOWN_ERROR, e);
    }
    if (existingBlob != null) {
      return new Failure<>(DocumentErrorCode.DOCUMENT_ALREADY_EXISTS);
    }
    final BlobId blobId = BlobId.of(bucketName, request.documentId());
    final var blobInfoBuilder = BlobInfo.newBuilder(blobId);
    try {
      applyMetadata(blobInfoBuilder, request.metadata());
    } catch (final JsonProcessingException e) {
      return new Failure<>("Failed to serialize metadata", DocumentErrorCode.INVALID_INPUT, e);
    }

    try {
      storage.createFrom(blobInfoBuilder.build(), request.contentInputStream());
    } catch (final Exception e) {
      return new Failure<>(DocumentErrorCode.UNKNOWN_ERROR, e);
    }
    final var documentReference = new DocumentReference(request.documentId(), request.metadata());
    return new Success<>(documentReference);
  }

  @Override
  public DocumentOperationResponse<InputStream> getDocument(final String documentId) {
    try {
      final Blob blob = storage.get(bucketName, documentId);
      if (blob == null) {
        return new Failure<>(DocumentErrorCode.DOCUMENT_NOT_FOUND);
      }
      final var inputStream = Channels.newInputStream(blob.reader());
      return new Success<>(inputStream);
    } catch (final Exception e) {
      return new Failure<>(DocumentErrorCode.UNKNOWN_ERROR, e);
    }
  }

  @Override
  public DocumentOperationResponse<Void> deleteDocument(final String documentId) {
    try {
      final boolean result = storage.delete(bucketName, documentId);
      if (!result) {
        return new Failure<>(DocumentErrorCode.DOCUMENT_NOT_FOUND);
      }
      return new Success<>(null);
    } catch (final Exception e) {
      return new Failure<>(DocumentErrorCode.UNKNOWN_ERROR, e);
    }
  }

  @Override
  public DocumentOperationResponse<DocumentLink> createLink(
      final String documentId, final long durationInSeconds) {
    try {
      final Blob blob = storage.get(bucketName, documentId);
      if (blob == null) {
        return new Failure<>(DocumentErrorCode.DOCUMENT_NOT_FOUND);
      }
      final var link = blob.signUrl(durationInSeconds, TimeUnit.SECONDS);
      return new Success<>(
          new DocumentLink(link.toString(), ZonedDateTime.now().plusSeconds(durationInSeconds)));
    } catch (final Exception e) {
      return new Failure<>(DocumentErrorCode.UNKNOWN_ERROR, e);
    }
  }

  private void applyMetadata(
      final BlobInfo.Builder blobInfoBuilder, final DocumentMetadataModel metadata)
      throws JsonProcessingException {
    if (metadata == null) {
      return;
    }
    if (metadata.contentType() != null && !metadata.contentType().isEmpty()) {
      blobInfoBuilder.setContentType(metadata.contentType());
    }
    if (metadata.expiresAt() != null) {
      blobInfoBuilder.setCustomTimeOffsetDateTime(OffsetDateTime.from(metadata.expiresAt()));
    }
    if (metadata.fileName() != null && !metadata.fileName().isEmpty()) {
      blobInfoBuilder.setContentDisposition("attachment; filename=" + metadata.fileName());
    } else {
      blobInfoBuilder.setContentDisposition("attachment");
    }
    if (metadata.additionalProperties() != null && !metadata.additionalProperties().isEmpty()) {
      final Map<String, String> blobMetadata = new HashMap<>();
      final var valueAsString = objectMapper.writeValueAsString(metadata.additionalProperties());
      metadata.additionalProperties().forEach((key, value) -> blobMetadata.put(key, valueAsString));
      blobInfoBuilder.setMetadata(blobMetadata);
    }
  }
}
