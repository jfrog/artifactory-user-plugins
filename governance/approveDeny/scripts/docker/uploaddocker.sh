#!/bin/bash

# 1. Create the image
echo 'building docker image'
docker build --tag host.docker.internal:8081/test-docker/test-image:latest . -f Dockerfile

# 2. Login to local Artifactory
echo 'logging into rt'
docker login localhost:8081 -u admin -p password

# 3. Push to local Artifactory
echo 'pushing to local rt'
docker push host.docker.internal:8081/test-docker/test-image:latest

# 4. Remove local image
echo 'remove local image'
docker image rm host.docker.internal:8081/test-docker/test-image
