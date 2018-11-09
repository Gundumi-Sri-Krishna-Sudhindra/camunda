import React from 'react';
import {shallow} from 'enzyme';

import ShowInstanceCount from './ShowInstanceCount';

it('should contain a switch that is checked if instance count is shown', () => {
  const node = shallow(<ShowInstanceCount configuration={{showInstanceCount: true}} />);

  expect(node.find('Switch').prop('checked')).toBe(true);

  node.setProps({configuration: {showInstanceCount: false}});

  expect(node.find('Switch').prop('checked')).toBe(false);
});

it('should call the onChange method when toggling the switch', () => {
  const spy = jest.fn();

  const node = shallow(
    <ShowInstanceCount configuration={{showInstanceCount: true}} onChange={spy} />
  );

  node.find('Switch').simulate('change', {target: {checked: false}});

  expect(spy).toHaveBeenCalledWith('showInstanceCount', false);
});
