#!/bin/bash

MAVEN="$(which mvn)"
cd "$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

if [ -z "${MAVEN}" ]; then
  echo "Maven is not install on this system. Run: 'brew install maven'">&2
  exit 1
fi

if [ -n "$1" ] && [ "$1" == "-nt" ]; then
  mvn install -DskipTests
else
  mvn install
fi

JENA_VERSION=$(cat ~/.m2/repository/org/apache/jena/apache-jena-fuseki/maven-metadata-local.xml | sed -n '/<version>/p;' | sed 's/[[:space:]]//g' | sed "s'</*version>''g")

curl -u"${ARTIFACTORY_CREDENTIALS}" -T ~/.m2/repository/org/apache/jena/apache-jena-fuseki/2.4.0-SNAPSHOT/apache-jena-fuseki-${JENA_VERSION}.tar.gz "http://smlonartifactory001.smoke.lon/artifactory/fuseki/apache-jena-fuseki-${JENA_VERSION}.tar.gz"
