/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import ErrorPage from './ErrorPage';

it('displays the error message passed in props', () => {
  const node = shallow(<ErrorPage>This is the error message.</ErrorPage>);

  expect(node).toMatchSnapshot();
});
