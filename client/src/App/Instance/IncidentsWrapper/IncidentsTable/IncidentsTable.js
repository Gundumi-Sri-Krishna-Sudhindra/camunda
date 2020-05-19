/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import Table from 'modules/components/Table';
import Button from 'modules/components/Button';
import ColumnHeader from '../../../Instances/ListPanel/List/ColumnHeader';
import Modal, {SIZES} from 'modules/components/Modal';
import {TransitionGroup} from 'modules/components/Transition';
import {IncidentOperation} from 'modules/components/Operations';

import {formatDate} from 'modules/utils/date';
import {withRouter} from 'react-router-dom';

import * as Styled from './styled';
const {THead, TBody, TH, TR, TD} = Table;

class IncidentsTable extends React.Component {
  static propTypes = {
    incidents: PropTypes.array.isRequired,
    selectedFlowNodeInstanceIds: PropTypes.array,
    sorting: PropTypes.object.isRequired,
    onIncidentSelection: PropTypes.func.isRequired,
    onSort: PropTypes.func.isRequired,
    match: PropTypes.shape({
      params: PropTypes.shape({
        id: PropTypes.string.isRequired,
      }).isRequired,
    }).isRequired,
  };

  static defaultProps = {
    forceSpinner: false,
    selectedFlowNodeInstanceIds: [],
  };

  state = {
    isModalVisibile: false,
  };

  toggleModal = ({content, title}) => {
    this.setState((prevState) => ({
      isModalVisibile: !prevState.isModalVisibile,
      modalContent: content ? content : null,
      modalTitle: title ? title : null,
    }));
  };

  renderModal = () => {
    return (
      <Modal
        onModalClose={this.toggleModal}
        isVisible={this.state.isModalVisibile}
        size={SIZES.BIG}
      >
        <Modal.Header>{this.state.modalTitle}</Modal.Header>
        <Modal.Body>
          <Modal.BodyText>{this.state.modalContent}</Modal.BodyText>
        </Modal.Body>
        <Modal.Footer>
          <Modal.PrimaryButton title="Close Modal" onClick={this.toggleModal}>
            Close
          </Modal.PrimaryButton>
        </Modal.Footer>
      </Modal>
    );
  };

  handleMoreButtonClick = (incident, e) => {
    e.stopPropagation();
    this.toggleModal({
      content: incident.errorMessage,
      title: `Flow Node "${incident.flowNodeName}" Error`,
    });
  };

  handleIncidentSelection = ({flowNodeInstanceId, flowNodeId}) => {
    const {selectedFlowNodeInstanceIds, onIncidentSelection} = this.props;
    const {id: instanceId} = this.props.match.params;

    let newSelection;

    const isTheOnlySelectedIncident =
      selectedFlowNodeInstanceIds.length === 1 &&
      selectedFlowNodeInstanceIds[0] === flowNodeInstanceId;

    if (isTheOnlySelectedIncident) {
      newSelection = {id: instanceId, activityId: null};
    } else {
      newSelection = {id: flowNodeInstanceId, activityId: flowNodeId};
    }

    onIncidentSelection(newSelection);
  };

  render() {
    const {incidents, sorting, selectedFlowNodeInstanceIds} = this.props;
    const isJobIdPresent = (incidents) =>
      !Boolean(incidents.find((item) => Boolean(item.jobId)));

    const {id: instanceId} = this.props.match.params;
    return (
      <>
        <Table>
          <THead>
            <TR>
              <Styled.FirstTH>
                <Styled.Fake />
                <ColumnHeader
                  sortKey="errorType"
                  label="Incident Type"
                  sorting={sorting}
                  onSort={this.props.onSort}
                />
              </Styled.FirstTH>
              <TH>
                <ColumnHeader
                  sortKey="flowNodeName"
                  label="Flow Node"
                  sorting={sorting}
                  onSort={this.props.onSort}
                />
              </TH>
              <TH>
                <ColumnHeader
                  sortKey="jobId"
                  label="Job Id"
                  sorting={sorting}
                  onSort={this.props.onSort}
                  disabled={isJobIdPresent(incidents)}
                />
              </TH>
              <TH>
                <ColumnHeader
                  sortKey="creationTime"
                  label="Creation Time"
                  sorting={sorting}
                  onSort={this.props.onSort}
                />
              </TH>
              <TH>
                <ColumnHeader label="Error Message" />
              </TH>
              <TH>
                <ColumnHeader label="Operations" />
              </TH>
            </TR>
          </THead>

          <TBody>
            <TransitionGroup component={null}>
              {incidents.map((incident, index) => {
                return (
                  <Styled.Transition
                    key={incident.id}
                    timeout={{enter: 500, exit: 200}}
                    mountOnEnter
                    unmountOnExit
                  >
                    <Styled.IncidentTR
                      data-test={`tr-incident-${incident.id}`}
                      isSelected={selectedFlowNodeInstanceIds.includes(
                        incident.flowNodeInstanceId
                      )}
                      onClick={this.handleIncidentSelection.bind(
                        this,
                        incident
                      )}
                    >
                      <TD>
                        <Styled.FirstCell>
                          <Styled.Index>{index + 1}</Styled.Index>
                          {incident.errorType}
                        </Styled.FirstCell>
                      </TD>
                      <TD>
                        <div>{incident.flowNodeName}</div>
                      </TD>
                      <TD>
                        <div>{incident.jobId || '--'}</div>
                      </TD>
                      <TD>
                        <div>{formatDate(incident.creationTime)}</div>
                      </TD>
                      <TD>
                        <Styled.Flex>
                          <Styled.ErrorMessageCell>
                            {incident.errorMessage}
                          </Styled.ErrorMessageCell>
                          {incident.errorMessage.length >= 58 && (
                            <Button
                              size="small"
                              onClick={this.handleMoreButtonClick.bind(
                                this,
                                incident
                              )}
                            >
                              More...
                            </Button>
                          )}
                        </Styled.Flex>
                      </TD>
                      <TD>
                        <IncidentOperation
                          instanceId={instanceId}
                          incident={incident}
                          showSpinner={incident.hasActiveOperation}
                        />
                      </TD>
                    </Styled.IncidentTR>
                  </Styled.Transition>
                );
              })}
            </TransitionGroup>
          </TBody>
        </Table>
        {this.renderModal()}
      </>
    );
  }
}

export default withRouter(IncidentsTable);
