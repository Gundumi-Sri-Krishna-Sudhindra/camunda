/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  makeObservable,
  when,
  IReactionDisposer,
  action,
  computed,
  override,
  observable,
} from 'mobx';
import {DiagramModel} from 'bpmn-moddle';
import {fetchProcessXML} from 'modules/api/diagram';
import {parseDiagramXML} from 'modules/utils/bpmn';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {logger} from 'modules/logger';
import {NetworkReconnectionHandler} from './networkReconnectionHandler';
import {isFlowNode} from 'modules/utils/flowNodes';
import {NON_APPENDABLE_FLOW_NODES} from 'modules/constants';
import {modificationsStore} from './modifications';
import {isAttachedToAnEventBasedGateway} from 'modules/bpmn-js/utils/isAttachedToAnEventBasedGateway';
import {processInstanceDetailsStatisticsStore} from './processInstanceDetailsStatistics';
import {isWithinMultiInstance} from 'modules/bpmn-js/utils/isWithinMultiInstance';
import {BusinessObject} from 'bpmn-js/lib/NavigatedViewer';

type State = {
  diagramModel: DiagramModel | null;
  xml: string | null;
  status: 'initial' | 'first-fetch' | 'fetching' | 'fetched' | 'error';
};

const DEFAULT_STATE: State = {
  diagramModel: null,
  xml: null,
  status: 'initial',
};

class ProcessInstanceDetailsDiagram extends NetworkReconnectionHandler {
  state: State = {
    ...DEFAULT_STATE,
  };
  processXmlDisposer: null | IReactionDisposer = null;

  constructor() {
    super();
    makeObservable(this, {
      state: observable,
      startFetch: action,
      handleFetchSuccess: action,
      areDiagramDefinitionsAvailable: computed,
      hasCalledProcessInstances: computed,
      flowNodes: computed,
      businessObjects: computed,
      processBusinessObject: computed,
      cancellableFlowNodes: computed,
      appendableFlowNodes: computed,
      modifiableFlowNodes: computed,
      nonModifiableFlowNodes: computed,
      handleFetchFailure: action,
      reset: override,
    });
  }

  init() {
    this.processXmlDisposer = when(
      () => processInstanceDetailsStore.state.processInstance !== null,
      () => {
        const processId =
          processInstanceDetailsStore.state.processInstance?.processId;

        if (processId !== undefined) {
          this.fetchProcessXml(processId);
        }
      }
    );
  }

  get businessObjects(): {[flowNodeId: string]: BusinessObject} {
    if (this.state.diagramModel === null) {
      return {};
    }

    return Object.entries(this.state.diagramModel.elementsById).reduce(
      (flowNodes, [flowNodeId, businessObject]) => {
        if (isFlowNode(businessObject)) {
          return {...flowNodes, [flowNodeId]: businessObject};
        } else {
          return flowNodes;
        }
      },
      {}
    );
  }

  get processBusinessObject(): BusinessObject | undefined {
    const bpmnProcessId =
      processInstanceDetailsStore.state.processInstance?.bpmnProcessId;

    if (bpmnProcessId === undefined) {
      return undefined;
    }

    return this.state.diagramModel?.elementsById[bpmnProcessId];
  }

  fetchProcessXml = this.retryOnConnectionLost(
    async (processId: ProcessInstanceEntity['processId']) => {
      this.startFetch();
      try {
        const response = await fetchProcessXML(processId);

        if (response.ok) {
          const xml = await response.text();
          this.handleFetchSuccess(xml, await parseDiagramXML(xml));
        } else {
          this.handleFetchFailure();
        }
      } catch (error) {
        this.handleFetchFailure(error);
      }
    }
  );

  startFetch = () => {
    if (this.state.status === 'initial') {
      this.state.status = 'first-fetch';
    } else {
      this.state.status = 'fetching';
    }
  };

  handleFetchSuccess = (xml: string, diagramModel: DiagramModel) => {
    this.state.diagramModel = diagramModel;
    this.state.xml = xml;
    this.state.status = 'fetched';
  };

  getFlowNodeName = (flowNodeId: string) => {
    return this.businessObjects[flowNodeId]?.name || flowNodeId;
  };

  getFlowNodeParents = (flowNode?: BusinessObject): string[] => {
    if (
      flowNode?.$parent === undefined ||
      flowNode.$parent.$type === 'bpmn:Process'
    ) {
      return [];
    }

    return [flowNode.$parent.id, ...this.getFlowNodeParents(flowNode.$parent)];
  };

  hasMultipleScopes = (flowNode: BusinessObject): boolean => {
    const scopeCount =
      processInstanceDetailsStatisticsStore.getTotalRunningInstancesForFlowNode(
        flowNode.id
      );

    if (scopeCount > 1) {
      return true;
    }

    if (flowNode.$parent?.$type !== 'bpmn:SubProcess') {
      return false;
    }

    return this.hasMultipleScopes(flowNode.$parent);
  };

  get flowNodes() {
    return Object.values(this.businessObjects).map((flowNode) => {
      const flowNodeState =
        processInstanceDetailsStatisticsStore.state.statistics.find(
          ({activityId}) => activityId === flowNode.id
        );

      return {
        id: flowNode.id,
        isCancellable:
          flowNodeState !== undefined &&
          (flowNodeState.active > 0 || flowNodeState.incidents > 0),
        isAppendable: !NON_APPENDABLE_FLOW_NODES.includes(flowNode.$type),
        hasMultiInstanceParent: isWithinMultiInstance(flowNode),
        isAttachedToAnEventBasedGateway:
          isAttachedToAnEventBasedGateway(flowNode),
        hasMultipleScopes:
          flowNode.$parent !== undefined
            ? this.hasMultipleScopes(flowNode.$parent)
            : false,
      };
    });
  }

  get appendableFlowNodes() {
    return this.flowNodes
      .filter(
        (flowNode) =>
          !flowNode.hasMultiInstanceParent &&
          !flowNode.isAttachedToAnEventBasedGateway &&
          flowNode.isAppendable &&
          !flowNode.hasMultipleScopes
      )
      .map(({id}) => id);
  }

  get cancellableFlowNodes() {
    return this.flowNodes
      .filter(
        (flowNode) => !flowNode.hasMultiInstanceParent && flowNode.isCancellable
      )
      .map(({id}) => id);
  }

  get modifiableFlowNodes() {
    if (modificationsStore.state.status === 'moving-token') {
      return this.appendableFlowNodes.filter(
        (flowNodeId) =>
          flowNodeId !==
          modificationsStore.state.sourceFlowNodeIdForMoveOperation
      );
    } else {
      return Array.from(
        new Set([...this.appendableFlowNodes, ...this.cancellableFlowNodes])
      );
    }
  }

  get nonModifiableFlowNodes() {
    return this.flowNodes
      .filter((flowNode) => !this.modifiableFlowNodes.includes(flowNode.id))
      .map(({id}) => id);
  }

  get areDiagramDefinitionsAvailable() {
    const {status, diagramModel} = this.state;
    return status === 'fetched' && diagramModel !== null;
  }

  get hasCalledProcessInstances() {
    return Object.values(this.businessObjects).some(
      ({$type}) => $type === 'bpmn:CallActivity'
    );
  }

  handleFetchFailure = (error?: unknown) => {
    this.state.status = 'error';

    logger.error('Failed to fetch Diagram XML');
    if (error !== undefined) {
      logger.error(error);
    }
  };

  reset() {
    super.reset();
    this.state = {...DEFAULT_STATE};
    this.processXmlDisposer?.();
  }
}

export const processInstanceDetailsDiagramStore =
  new ProcessInstanceDetailsDiagram();
