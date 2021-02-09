#!/bin/bash

rm test.txt

export JFROG_CLI_OFFER_CONFIG=false
jfrog rt dl test-repo/test.txt --url http://localhost:8081/artifactory --user admin --password password

# Equivalent curl command:
#curl -u admin:password  http://localhost:8081/artifactory/test-repo/cool-froggy.jar -o test.txt
