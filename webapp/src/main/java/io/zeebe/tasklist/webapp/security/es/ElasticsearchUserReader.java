/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.webapp.security.es;

import static io.zeebe.tasklist.webapp.security.TasklistURIs.SSO_AUTH_PROFILE;

import io.zeebe.tasklist.webapp.graphql.entity.UserDTO;
import io.zeebe.tasklist.webapp.security.UserReader;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@Profile("!" + SSO_AUTH_PROFILE)
public class ElasticsearchUserReader implements UserReader {

  @Autowired private UserStorage userStorage;

  @Override
  public UserDTO getCurrentUser() {
    final SecurityContext context = SecurityContextHolder.getContext();
    final Authentication authentication = context.getAuthentication();
    return UserDTO.createFrom((User) authentication.getPrincipal());
  }

  @Override
  public List<UserDTO> getUsersByUsernames(List<String> usernames) {
    return UserDTO.createFrom(userStorage.getUsersByUsernames(usernames));
  }
}
