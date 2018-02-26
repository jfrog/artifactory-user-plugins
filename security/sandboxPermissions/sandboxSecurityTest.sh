#!/bin/bash
echo "SandboxSecurity Artifactory User Plugin Test"


# To Do 
# Need test for conan; docker; ivy  repo

dev1List=("bower-dev-local" "cocoapod-dev-local" "debian-dev-local" "gems-dev-local" "gitlfs-dev-local" "gradle-prod-local" \
          "ivy-dev-local" "maven-data-snapshot-local" "npm-dev-local" "nuget-dev-local" "opkg-dev-local" "pypi-dev-local" "sbt-dev-local" \
          "tomcat-local" "vagrant-dev-local" "yum-dev-local")

deploygroupanduser() {
  
  user=$1
  namespace=$2
  expecterror=$3

  for repo in "${dev1List[@]}"
  do 
	  response=$( 
      curl -u$user:jfrog \
        --write-out %{http_code} \
        --silent \
        --output /dev/null \
        -X PUT http://mill.jfrog.info:12020/artifactory/$repo/$namespace/http-builder-0.5.2.jar)
    if [ ! $response -eq $expecterror ]; then 
      echo "....FAILED - Test 1 - fail to upload: error $response repository $repo"
    fi
    response=$(
      curl -u$user:jfrog \
        --write-out %{http_code} \
        --silent \
        --output /dev/null \
        -X PUT http://mill.jfrog.info:12020/artifactory/$repo/$namespace/org/jfrog/com/folder/abc.jar)
    if [ ! $response -eq $expecterror ]; then
      echo "....FAILED - Test 2 - fail to upload: error $response repository $repo"
    fi
    response=$(
      curl -u$user:jfrog \
        --write-out %{http_code} \
        --silent \
        --output /dev/null \
        -X PUT http://mill.jfrog.info:12020/artifactory/$repo/rootjar.jar)
      if [ ! $response -eq 403 ]; then
        echo "....FAILED - Test 3 - fail: should not be able to upload to root directory;  error $response repository $repo"
      fi
  done
}

deployadmin() {
  
  namespace=$3
  user=$1
  password=$2
  for repo in "${dev1List[@]}" 
  do 
  response=$( 
    curl -u$user:$password \
      --write-out %{http_code} \
      --silent \
      --output /dev/null \
      -X PUT http://mill.jfrog.info:12020/artifactory/$repo/$namespace/admin.jar)
    if [ ! $response -eq 403 ]; then 
        echo "....FAILED - Test 7 - Admin user should not upload: error $response repository $repo"
    fi
    response=$(
      curl -u$user:$password \
        --write-out %{http_code} \
        --silent \
        --output /dev/null \
        -X PUT http://mill.jfrog.info:12020/artifactory/$repo/$namespace/org/jfrog/com/folder/admin.jar)
    if [ ! $response -eq 403 ]; then
        echo "....FAILED - Test 8 - Admin fail to upload: error $response repository $repo"
    fi
    response=$(
      curl -u$user:$password \
        --write-out %{http_code} \
        --silent \
        --output /dev/null \
        -X PUT http://mill.jfrog.info:12020/artifactory/$repo/rootjar.jar)
      if [ ! $response -eq 201 ]; then
          echo "....FAILED - Test 9 - Admin fail: should be able to upload to root directory;  error $response repository $repo"
      fi
  done
}

changeproperty() {
  namespace=$3
  user=$1
  password=$2
  for repo in "${dev1List[@]}"
  do 
    response=$(
      curl -u$user:$password \
        --write-out %{http_code} \
        --silent \
        --output /dev/null \
        -X PUT http://mill.jfrog.info:12020/artifactory/api/storage/$repo/$namespace\?properties\=sandboxPerms.ownerUsers\=nobody)                                 
      if [ ! $response -eq 204 ]; then
          echo "....FAILED - Could not change property.  error $response repository $repo"
      fi

      response=$( 
        curl -u$namespace:jfrog \
          --write-out %{http_code} \
          --silent \
          --output /dev/null \
          -X PUT http://mill.jfrog.info:12020/artifactory/$repo/$namespace/http-builder-0.5.2.jar)
      if [ ! $response -eq 403 ]; then 
        echo "....FAILED - property changed, should not be able to deploy. error $response repository $repo"
      fi

      response=$(
        curl -u$user:$password \
          --write-out %{http_code} \
          --silent \
          --output /dev/null \
          -X PUT http://mill.jfrog.info:12020/artifactory/api/storage/$repo/$namespace\?properties\=sandboxPerms.ownerUsers\=$namespace) 

      response=$( 
        curl -u$user:jfrog \
          --write-out %{http_code} \
          --silent \
          --output /dev/null \
          -X PUT http://mill.jfrog.info:12020/artifactory/$repo/$namespace/http-builder-0.5.2.jar)
      if [ ! $response -eq 201 ]; then 
        echo "....FAILED - property changed back to original should be able to deploy. error $response repository $repo"
      fi
  done
}

resetproperty() {
  user=$1
  password=$2
  namespace=$3
  for repo in "${dev1List[@]}"
  do 
    response=$(
      curl -u$user:$password \
        --write-out %{http_code} \
        --silent \
        --output /dev/null \
        -X PUT http://mill.jfrog.info:12020/artifactory/api/storage/$repo/$namespace\?properties\=sandboxPerms.ownerUsers\=$user)                                 
      if [ ! $response -eq 204 ]; then
          echo "....FAILED - Could not change property.  error $response repository $repo"
      fi
  done
}

changepropertylist() {
  namespace=$3
  user=$1
  password=$2
  userlist=$4
  altstatus=$5
  
  for repo in "${dev1List[@]}"
  do 
    response=$(
      curl -u$user:$password \
        --write-out %{http_code} \
        --silent \
        --output /dev/null \
        -X PUT http://mill.jfrog.info:12020/artifactory/api/storage/$repo/$namespace\?properties\=sandboxPerms.ownerUsers\=$userlist)                                 
      if [ ! $response -eq 204 ]; then
          echo "....FAILED - Could not change property.  error $response repository $repo"
      fi

      response=$( 
        curl -u$user:jfrog \
          --write-out %{http_code} \
          --silent \
          --output /dev/null \
          -X PUT http://mill.jfrog.info:12020/artifactory/$repo/$namespace/http-builder-0.5.2.jar)
      if [ ! $response -eq 201 ]; then 
        echo "....FAILED - property changed to user list. Should be able to deploy. error $response repository $repo"
      fi

      response=$( 
        curl -u$jagans:jfrog \
          --write-out %{http_code} \
          --silent \
          --output /dev/null \
          -X PUT http://mill.jfrog.info:12020/artifactory/$repo/$namespace/http-builder-0.5.2.jar)
      if [ ! $response -eq 201 ]; then 
        echo "....FAILED - property changed to user list. jagans should be able to deploy. error $response repository $repo"
      fi

      response=$( 
        curl -u$ankushc:jfrog \
          --write-out %{http_code} \
          --silent \
          --output /dev/null \
          -X PUT http://mill.jfrog.info:12020/artifactory/$repo/$namespace/http-builder-0.5.2.jar)
      if [ ! $response -eq $altstatus ]; then 
        echo "....FAILED - ankushc not in list should not be able to deploy but has permission to deploy. error $response repository $repo OR"
        echo "....FAILED - ankushc belongs to development-team and should be able to deploy. error $response repository $repo"
      fi
  done
}


deleteproperty() {
  namespace=$3
  user=$1
  password=$2

  for repo in "${dev1List[@]}"
  do 
    response=$( 
        curl -u$user:jfrog \
          --write-out %{http_code} \
          --silent \
          --output /dev/null \
          -X PUT http://mill.jfrog.info:12020/artifactory/$namespace/$user/http-builder-0.5.2.jar)
      if [ ! $response -eq 403 ]; then 
        echo "....FAILED - Should not be able to deploy with $user. Should be able to deploy. error $response repository $repo"
      fi

    response=$(
      curl -u$user:$password \
        --write-out %{http_code} \
        --silent \
        --output /dev/null \
        -X DELETE http://mill.jfrog.info:12020/artifactory/api/storage/$repo/$namespace\?properties\=sandboxPerms.ownerUsers)                                 
      if [ ! $response -eq 204 ]; then
          echo "....FAILED - Could not delete property.  error $response repository $repo"
      fi

      response=$( 
        curl -u$user:jfrog \
          --write-out %{http_code} \
          --silent \
          --output /dev/null \
          -X PUT http://mill.jfrog.info:12020/artifactory/$namespace/$user/http-builder-0.5.2.jar)
      if [ ! $response -eq 201 ]; then 
        echo "....FAILED - property deleted, Dev2 should be able to deploy. error $response repository $repo"
      fi

      response=$( 
        curl -u$jagans:jfrog \
          --write-out %{http_code} \
          --silent \
          --output /dev/null \
          -X PUT http://mill.jfrog.info:12020/artifactory/$repo/$namespace/http-builder-0.5.2.jar)
      if [ ! $response -eq 403 ]; then 
        echo "....FAILED - jagans should be not be able to deploy. error $response repository $repo"
      fi

      response=$( 
        curl -u$ankushc:jfrog \
          --write-out %{http_code} \
          --silent \
          --output /dev/null \
          -X PUT http://mill.jfrog.info:12020/artifactory/$repo/$namespace/http-builder-0.5.2.jar)
      if [ ! $response -eq 403 ]; then 
        echo "....FAILED - ankushc not in list should not be able to deploy. error $response repository $repo"
      fi
  done
}

deleteartifactsbyadmin() {
  namespace=$3
  user=$1
  password=$2

  for repo in "${dev1List[@]}"
  do 
    response=$( 
      curl -u$user:password \
        --write-out %{http_code} \
        --silent \
        --output /dev/null \
        -X DELETE http://mill.jfrog.info:12020/artifactory/$repo/$namespace/http-builder-0.5.2.jar)
    if [ ! $response -eq 204 ]; then 
      echo "....FAILED - fail to delete artifact if property set or cleared : error $response repository $repo"
    fi

    response=$(
      curl -u$user:password \
        --write-out %{http_code} \
        --silent \
        --output /dev/null \
        -X DELETE http://mill.jfrog.info:12020/artifactory/$repo/$namespace/org/jfrog/com/folder/abc.jar)
      if [ ! $response -eq 204 ]; then
        echo "....FAILED - fail to delete artifact if property set or cleared : error $response repository $repo"
      fi
  done
}

clearproperty() {
  namespace=$3
  user=$1
  password=$2

  for repo in "${dev1List[@]}"
  do 
    response=$(
      curl -u$user:$password \
        --write-out %{http_code} \
        --silent \
        --output /dev/null \
        -X DELETE http://mill.jfrog.info:12020/artifactory/api/storage/$repo/$namespace\?properties\=sandboxPerms.ownerUsers)                                 
      if [ ! $response -eq 204 ]; then
          echo "....FAILED - Could not delete property.  error $response repository $repo"
      fi
  done
}

deploydocker() {
    namespace=$3
    user=$1
    password=$2
    repo=$4
    docker pull hello-world:latest
    docker login -u$user -p$password mill.jfrog.info:12021
    docker tag hello-world:latest mill.jfrog.info:12021/$repo/$namespace:latest
    response=$(
      docker push mill.jfrog.info\:12021/$repo/$namespace\:latest)
    echo $response
}

echo "Test1 - deploy by user belonging to group"
deploygroupanduser dev1 dev1 201 #association by group - parameter user, namespace, expect response code
echo "Test2 - deploy by user"
deploygroupanduser eliom eliom 201 #association by user - parameter user, namespace, expect response code
echo "Test3 - deploy by admin"
deployadmin admin password dev1 #admin - parameter admin, password, namespace
echo "Test4 - deploy by user with admin"
deployadmin stanleyf jfrog dev1 #admin - parameter stanleyf, password, namespace
echo "Test5 - attempt to override namespace by group"
deploygroupanduser dev2 dev1 403 #association by group - fail by namespace
echo "Test6 - deploy by another user from same group to repo list 2"
deploygroupanduser dev2 apple 201 
echo "Test7 - deploy by user to repo list 2"
deploygroupanduser travisf travisf 201 
echo "Test8 - change sandbox property by different user than owner; deploy fail; change property back"
changeproperty dev1 jfrog dev1
echo "Test9 - change sandbox property to have multiple users and test deployment"
changepropertylist dev1 jfrog dev1 "jagans,dev1,dev2" 403
echo "Test10 - change sandbox property to use groups deploy by user belonging to group"
resetproperty dev1 jfrog devgroup
deploygroupanduser dev1 devgroup 201 
changepropertylist dev1 jfrog devgroup "development-team" 201
echo "Test11 - delete property and verify artifact be deploy by new user"
deploygroupanduser dev1 deleteproperty 201 
deleteproperty dev2 jfrog deleteproperty 
echo "Test12 - admin should be able to delete if property set"
deploygroupanduser dev1 admindeleteartifact 201 
deleteartifactsbyadmin admin password admindeleteartifact 
echo "Test13 - user with admin priveleges should be able if property set"
deploygroupanduser dev1 aduserdeleteartifact 201 
deleteartifactsbyadmin stanleyf jfrog aduserdeleteartifact 
echo "Test14 - delete property then delete artifacts by admin"
deploygroupanduser dev1 deletepropartifact 201 
clearproperty dev1 jfrog deletepropartifact 
deleteartifactsbyadmin admin password deletepropartifact
echo "Test15 - docker deploy"
deploydocker dev1 jfrog dev1docker docker-local


