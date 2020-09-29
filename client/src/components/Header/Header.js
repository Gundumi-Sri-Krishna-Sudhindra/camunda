/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState, useEffect} from 'react';
import {Link, withRouter} from 'react-router-dom';
import classnames from 'classnames';

import {t} from 'translation';
import {getHeader, areSettingsManuallyConfirmed} from 'config';
import {withErrorHandling, withUser} from 'HOC';
import {addNotification, showError} from 'notifications';

import {TelemetrySettings} from './TelemetrySettings';
import HeaderNav from './HeaderNav';
import ChangeLog from './ChangeLog';
import UserMenu from './UserMenu';

import {isEventBasedProcessEnabled} from './service';

import './Header.scss';

export function Header({mightFail, location, noActions, user}) {
  const [config, setConfig] = useState({});
  const [showEventBased, setShowEventBased] = useState(false);
  const [telemetrySettingsOpen, setTelemetrySettingsOpen] = useState(false);

  useEffect(() => {
    mightFail(getHeader(), setConfig, () =>
      addNotification({type: 'error', text: t('navigation.configLoadingError')})
    );

    mightFail(isEventBasedProcessEnabled(), setShowEventBased, showError);

    // automatically open the telemetry settings if settings have not been confirmed
    mightFail(areSettingsManuallyConfirmed(), (confirmed) => {
      if (!confirmed && user?.authorizations.includes('telemetry_administration')) {
        setTelemetrySettingsOpen(true);
      }
    });
  }, [mightFail, user]);

  const name = t('appName');

  return (
    <header
      style={{backgroundColor: config.backgroundColor}}
      role="banner"
      className={classnames('Header', {['text-' + config.textColor]: config.textColor})}
    >
      <Link to="/" replace={location.pathname === '/'} className="appLink" title={name}>
        <img src={config.logo} alt="Logo" />
        <span>{name}</span>
      </Link>
      {!noActions && (
        <>
          <HeaderNav>
            <HeaderNav.Item
              name={t('navigation.homepage')}
              linksTo="/"
              active={['/', '/report/*', '/dashboard/*', '/collection/*']}
              breadcrumbsEntities={['collection', 'dashboard', 'report']}
            />
            <HeaderNav.Item
              name={t('navigation.analysis')}
              linksTo="/analysis"
              active={['/analysis/', '/analysis/*']}
            />
            {showEventBased && (
              <HeaderNav.Item
                name={t('navigation.events')}
                linksTo="/eventBasedProcess/"
                active={['/eventBasedProcess/', '/eventBasedProcess/*']}
                breadcrumbsEntities={['eventBasedProcess']}
              />
            )}
          </HeaderNav>
          <ChangeLog />
          <UserMenu user={user} onTelemetryOpen={() => setTelemetrySettingsOpen(true)} />
        </>
      )}
      {telemetrySettingsOpen && (
        <TelemetrySettings
          open={telemetrySettingsOpen}
          onClose={() => setTelemetrySettingsOpen(false)}
        />
      )}
    </header>
  );
}

export default withUser(withErrorHandling(withRouter(Header)));
