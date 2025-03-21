/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import ForbiddenPage from "src/pages/forbidden/ForbiddenPage";
import AppRoot from "src/components/global/AppRoot.tsx";

const Forbidden: FC = () => {
  return (
    <AppRoot>
      <ForbiddenPage />
    </AppRoot>
  );
};

export default Forbidden;
