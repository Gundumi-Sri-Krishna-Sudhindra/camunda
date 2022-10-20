/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ElementType, BusinessObject} from 'bpmn-js/lib/NavigatedViewer';
import {isFlowNode, getFlowNodes} from './index';

const createElement = (
  type: ElementType,
  id: string = 'FlowNode'
): BusinessObject => {
  return {
    id,
    name: 'Element',
    $type: type,
    $instanceOf: () => type === 'bpmn:ServiceTask',
  };
};

describe('flowNodes', () => {
  describe('isFlowNode', () => {
    it('should return true for tasks', () => {
      const element = createElement('bpmn:ServiceTask');

      expect(isFlowNode(element)).toBeTruthy();
    });

    it('should return false for Processes', () => {
      const element = createElement('bpmn:Process');

      expect(isFlowNode(element)).toBeFalsy();
    });
  });

  describe('getFlowNodes', () => {
    it('should get flow nodes', () => {
      const Task1 = createElement('bpmn:ServiceTask', 'Task1');
      const Root = createElement('bpmn:Process', 'Root');
      const SequenceFlow1 = createElement('bpmn:SequenceFlow', 'SequenceFlow1');

      const elements = [Task1, Root, SequenceFlow1];

      expect(getFlowNodes(elements)).toEqual([Task1]);
    });

    it('sould get empty objects', () => {
      expect(getFlowNodes([])).toEqual([]);
      expect(getFlowNodes()).toEqual([]);
    });
  });
});
