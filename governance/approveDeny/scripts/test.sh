#!/bin/bash

rm cool-froggy.jar
curl -u admin:password  http://localhost:8081/artifactory/test-repo/cool-froggy.jar -o cool-froggy.jar

# TODO switch to using jfrog CLI
#export JFROG_CLI_OFFER_CONFIG=false
#jfrog rt dl test-repo/cool-froggy.zip --url http://localhost:8081/artifactory --user admin --password password
