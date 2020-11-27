/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {
  render,
  screen,
  waitForElementToBeRemoved,
} from '@testing-library/react';

import {FlowNodeInstanceLog} from './index';
import {
  mockSuccessResponseForActivityTree,
  mockFailedResponseForActivityTree,
  mockSuccessResponseForDiagram,
} from './index.setup';
import {flowNodeInstanceStore} from 'modules/stores/flowNodeInstance';
import {currentInstanceStore} from 'modules/stores/currentInstance';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {singleInstanceDiagramStore} from 'modules/stores/singleInstanceDiagram';
import {rest} from 'msw';
import {mockServer} from 'modules/mockServer';

jest.mock('modules/utils/bpmn');

describe('FlowNodeInstanceLog', () => {
  beforeAll(async () => {
    mockServer.use(
      rest.get('/api/workflow-instances/:id', (_, res, ctx) =>
        res.once(
          ctx.json({id: '1', state: 'ACTIVE', workflowName: 'workflowName'})
        )
      )
    );

    await currentInstanceStore.init(1);
  });

  afterAll(() => {
    currentInstanceStore.reset();
  });

  afterEach(() => {
    flowNodeInstanceStore.reset();
    singleInstanceDiagramStore.reset();
  });

  it('should render skeleton when instance tree is not loaded', async () => {
    mockServer.use(
      rest.post('/api/activity-instances', (_, res, ctx) =>
        res.once(ctx.json(mockSuccessResponseForActivityTree))
      ),
      rest.get('/api/workflows/:workflowId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockSuccessResponseForDiagram))
      )
    );

    render(<FlowNodeInstanceLog />, {wrapper: ThemeProvider});

    await singleInstanceDiagramStore.fetchWorkflowXml(1);
    flowNodeInstanceStore.fetchInstanceExecutionHistory(1);

    expect(screen.getByTestId('flownodeInstance-skeleton')).toBeInTheDocument();

    await waitForElementToBeRemoved(
      screen.getByTestId('flownodeInstance-skeleton')
    );
  });

  it('should render skeleton when instance diagram is not loaded', async () => {
    mockServer.use(
      rest.post('/api/activity-instances', (_, res, ctx) =>
        res.once(ctx.json(mockSuccessResponseForActivityTree))
      ),
      rest.get('/api/workflows/:workflowId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockSuccessResponseForDiagram))
      )
    );

    render(<FlowNodeInstanceLog />, {wrapper: ThemeProvider});

    await flowNodeInstanceStore.fetchInstanceExecutionHistory(1);
    singleInstanceDiagramStore.fetchWorkflowXml(1);

    expect(screen.getByTestId('flownodeInstance-skeleton')).toBeInTheDocument();

    await waitForElementToBeRemoved(
      screen.getByTestId('flownodeInstance-skeleton')
    );
  });

  it('should display error when instance tree data could not be fetched', async () => {
    mockServer.use(
      rest.post('/api/activity-instances', (_, res, ctx) =>
        res.once(ctx.status(500), ctx.json(mockFailedResponseForActivityTree))
      ),
      rest.get('/api/workflows/:workflowId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockSuccessResponseForDiagram))
      )
    );

    render(<FlowNodeInstanceLog />, {wrapper: ThemeProvider});

    await singleInstanceDiagramStore.fetchWorkflowXml(1);
    await flowNodeInstanceStore.fetchInstanceExecutionHistory(1);
    expect(
      screen.getByText('Activity Instances could not be fetched')
    ).toBeInTheDocument();
  });

  it('should display error when instance diagram could not be fetched', async () => {
    mockServer.use(
      rest.post('/api/activity-instances', (_, res, ctx) =>
        res.once(ctx.json(mockSuccessResponseForActivityTree))
      ),
      rest.get('/api/workflows/:workflowId/xml', (_, res, ctx) =>
        res.once(ctx.status(500), ctx.text(''))
      )
    );

    render(<FlowNodeInstanceLog />, {wrapper: ThemeProvider});

    await singleInstanceDiagramStore.fetchWorkflowXml(1);
    await flowNodeInstanceStore.fetchInstanceExecutionHistory(1);
    expect(
      screen.getByText('Activity Instances could not be fetched')
    ).toBeInTheDocument();
  });

  it('should render flow node instances tree', async () => {
    mockServer.use(
      rest.post('/api/activity-instances', (_, res, ctx) =>
        res.once(ctx.json(mockSuccessResponseForActivityTree))
      ),
      rest.get('/api/workflows/:workflowId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockSuccessResponseForDiagram))
      )
    );

    render(<FlowNodeInstanceLog />, {wrapper: ThemeProvider});

    await singleInstanceDiagramStore.fetchWorkflowXml(1);
    await flowNodeInstanceStore.fetchInstanceExecutionHistory(1);
    expect(screen.getAllByText('workflowName').length).toBeGreaterThan(0);
  });
});
