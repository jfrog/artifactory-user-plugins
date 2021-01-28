#!/bin/bash

artifactoryImage="artifactory-pro"
artifactoryVersion="6.16.0"
dataDir="$(pwd)/.data"
archiveDir="$(pwd)/.archive"
containerName="artifactory"

docker rm -f "${containerName}"

# Prepare data dir
mkdir -p "${dataDir}/etc/plugins"
# Copy plugin file
originalPluginFile="$(pwd)/src/artifactory/groovy/archiveOldArtifacts.groovy"
if [[ ! -f "${originalPluginFile}" ]]; then
  echo "Plugin file [${originalPluginFile}] does not exist!"
  echo "Put the plugin file at [${originalPluginFile}] and I'll put it on the right place for you"
  exit 1
fi
cp --force "${originalPluginFile}" \
           "${dataDir}/etc/plugins/archiveOldArtifacts.groovy"

# Copy license file
licenseFileName="artifactory.lic"
originalLicenseFile="$(pwd)/${licenseFileName}"
licenseFile="${dataDir}/etc/${licenseFileName}"
if [[ ! -f "${originalLicenseFile}" ]]; then
  echo "License file [${originalLicenseFile}] does not exist!"
  echo "Put the file at [${originalLicenseFile}] and I'll put it on the right place for you"
  exit 1
fi
cp --force "${originalLicenseFile}" "${licenseFile}"


# Copy logback file
logbackFileName="logback.xml"
originalLogbackFile="$(pwd)/${logbackFileName}"
logbackFile="${dataDir}/etc/${logbackFileName}"
if [[ ! -f "${originalLogbackFile}" ]]; then
  echo "Logback file [${originalLogbackFile}] does not exist!"
  echo "Put the file at [${originalLogbackFile}] and I'll put it on the right place for you"
  exit 1
fi
cp --force "${originalLogbackFile}" "${logbackFile}"

# Prepare archive dir
rm -rf "${archiveDir}"
mkdir -p "${archiveDir}"

# Start artifactory
archiveDirWithinContainer="/var/opt/jfrog/archive"

docker run \
  --name "${containerName}" \
  --rm \
  --publish 8088:8081 \
  --user "$(id -u):$(id -g)" \
  --env "ARTIFACTORY_DATA_ARCHIVE=${archiveDirWithinContainer}" \
  --volume "${archiveDir}:${archiveDirWithinContainer}" \
  --volume "${dataDir}:/var/opt/jfrog/artifactory/" \
  docker.bintray.io/jfrog/${artifactoryImage}:${artifactoryVersion}

