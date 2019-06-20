#!/usr/bin/env bash

BASEDIR="$( cd "$(dirname "$0")" ; pwd -P )"

cd ${BASEDIR}/../../
OPTIMIZE_ROOT=`pwd`
SETTINGS_PATH=`find ${OPTIMIZE_ROOT} -iname "settings.xml"`

SETTINGS_COMMAND=$([[ "$1" == "useCISettings" ]] && echo "-s ${SETTINGS_PATH}" || echo "")
echo "Using maven settings command option: ${SETTINGS_COMMAND}"

cd ./backend

## create back end third party
mvn org.codehaus.mojo:license-maven-plugin:1.2:download-licenses -Dlicense.excludedScopes=test,system,provided ${SETTINGS_COMMAND} -B --fail-at-end

## store directory of license file
GET_BACKEND_DIR=`pwd`
LICENSE_DIR=`find ${GET_BACKEND_DIR} -iname "licenses.xml"`

cd ${BASEDIR}

# create the back-end dependencies md file
mvn clean install -DskipTests -Dskip.docker ${SETTINGS_COMMAND} -B --fail-at-end
mvn exec:java -Dexec.mainClass="org.camunda.optimize.MarkDownDependencyCreator" -Dexec.args=${LICENSE_DIR} ${SETTINGS_COMMAND} -B --fail-at-end

# create front-end dependencies md file
cd ../../client
mvn clean install -DskipTests -Dskip.docker ${SETTINGS_COMMAND} -B --fail-at-end

export PATH=$(pwd)/.node/node/:$PATH
./.node/node/yarn/dist/bin/yarn
./.node/node/yarn/dist/bin/yarn run dependencies
cp frontend-dependencies.md ../util/dependency-doc-creation/
cd ${BASEDIR}

