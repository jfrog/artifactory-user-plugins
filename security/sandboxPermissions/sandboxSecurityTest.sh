#!/bin/bash
echo "SandboxSecurity Artifactory User Plugin Test"


# To Do
# Need test for conan; docker; ivy  repo

dev1List=("bower-dev-local" "cocoapod-dev-local" "debian-dev-local" "gems-dev-local" "gitlfs-dev-local" "gradle-prod-local" \
          "ivy-dev-local" "maven-data-snapshot-local" "npm-dev-local" "nuget-dev-local" "opkg-dev-local" "pypi-dev-local" "sbt-dev-local" \
          "tomcat-local" "vagrant-dev-local" "yum-dev-local")

artbaseurl="http://mill.jfrog.team:12231/artifactory"
dockerdomain="mill.jfrog.team:12231"

#Deploy artifacts by user
#Inputs
# user:  user - owner of namespace
# password: user password
# expecterror - expected error if user is not the owner of the namepsace
# namespace - namespace to use - use namespace to be the name of the test
# Required - the following jar files are used
#  1. http-builder-0.5.2.jar
#  2. abc.jar
#  3. root.jar
deploygroupanduser() {
  user=$1
  password=$2
  namespace=$3
  expecterror=$4

  for repo in "${dev1List[@]}"
  do
    response=$(
              curl -u$user:$password \
            --write-out %{http_code} \
            --silent \
            --output /dev/null \
            -X PUT $artbaseurl/$repo/$namespace/http-builder-0.5.2.jar)
        if [ ! $response -eq $expecterror ]; then
              echo "....FAILED - fail to upload flat file with new namespace, error $response repository $repo"
        fi
        response=$(
              curl -u$user:$password \
            --write-out %{http_code} \
            --silent \
            --output /dev/null \
            -X PUT $artbaseurl/$repo/$namespace/org/jfrog/com/folder/abc.jar)
        if [ ! $response -eq $expecterror ]; then
              echo "....FAILED - fail to upload gvac structure with new namesace. : error $response repository $repo"
        fi
        response=$(
              curl -u$user:$password \
            --write-out %{http_code} \
            --silent \
            --output /dev/null \
            -X PUT $artbaseurl/$repo/rootjar.jar)
          if [ ! $response -eq 403 ]; then
            echo "....FAILED - fail: should not be able to upload to root directory;  error $response repository $repo"
          fi
  done
}

#Deploy artifacts by admin
#this test is similar to deploygroupand user.  This is added in the event the function of admin changes.
#Inputs
# user: user with admin privileges
# password: admin password
# namespace - namespace to use - use namespace to be the name of the test
# expectederror
#Required - the following jar files are used
#  1. admin.jar
#  2. http-builder-0.5.2.jar
#  3. root.jar
deployadmin() {
  namespace=$3
  user=$1
  password=$2
  expectedstatus=$4
  for repo in "${dev1List[@]}"
  do
      response=$(
            curl -u$user:$password \
              --write-out %{http_code} \
              --silent \
              --output /dev/null \
              -X PUT $artbaseurl/artifactory/$repo/$namespace/http-builder-0.5.2.jar)
        if [ ! $response -eq $expectedstatus ]; then
            echo "....FAILED - Admin user should not upload flat file: error $response repository $repo"
        fi
        response=$(
              curl -u$user:$password \
            --write-out %{http_code} \
            --silent \
            --output /dev/null \
            -X PUT $artbaseurl/$repo/$namespace/org/jfrog/com/folder/admin.jar)
        if [ ! $response -eq $expectedstatus ]; then
            echo "....FAILED - Admin user fail to upload gvac: error $response repository $repo"
        fi
        response=$(
              curl -u$user:$password \
            --write-out %{http_code} \
            --silent \
            --output /dev/null \
            -X PUT $artbaseurl/$repo/rootjar.jar)
          if [ ! $response -eq $expectedstatus ]; then
              echo "....FAILED - Admin fail: should not be able to upload to root directory;  error $response repository $repo"
         fi
  done
}

#Change property
#Inputs
# user: user with admin privileges
# password: admin password
# namespace - namespace to use - use namespace to be the name of the test
#Required - the following jar files are used
#  1. admin.jar
#  2. http-builder-0.5.2.jar
#  3. root.jar
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
            -X PUT $artbaseurl/api/storage/$repo/$namespace\?properties\=sandboxPerms.ownerUsers\=nobody)
          if [ ! $response -eq 204 ]; then
              echo "....FAILED - Could not change property to namepace nobody.  error $response repository $repo"
          fi

        response=$(
            curl -u$user:$password \
              --write-out %{http_code} \
              --silent \
              --output /dev/null \
              -X PUT $artbaseurl/$repo/$namespace/http-builder-0.5.2.jar)
          if [ ! $response -eq 403 ]; then
            echo "....FAILED - property changed, should not be able to deploy. error $response repository $repo"
          fi

          response=$(
            curl -u$user:$password \
              --write-out %{http_code} \
              --silent \
              --output /dev/null \
              -X PUT $artbaseurl/api/storage/$repo/$namespace\?properties\=sandboxPerms.ownerUsers\=$user)

          response=$(
            curl -u$user:$password \
              --write-out %{http_code} \
              --silent \
              --output /dev/null \
              -X PUT $artbaseurl/$repo/$namespace/http-builder-0.5.2.jar)
          if [ ! $response -eq 201 ]; then
            echo "....FAILED - property changed back to original owner should be able to deploy. error $response repository $repo"
          fi
  done
}

#Reset property to new owner
#Inputs
# user: user with admin privileges
# password: admin password
# namespace - namespace to use - use namespace to b
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
            -X PUT $artbaseurl/$repo/$namespace\?properties\=sandboxPerms.ownerUsers\=$user)
          if [ ! $response -eq 204 ]; then
              echo "....FAILED - Could not change property to new owner.  error $response repository $repo"
          fi
  done
}

#Use user list for property
#Inputs
# user: user with admin privileges
# password: admin password
# namespace - namespace to use - use namespace to b
# altuser - expect password = jfrog
# altstatus - expected status returned - dependent on whether user is on list or not.
changepropertylist() {
  namespace=$3
  user=$1
  password=$2
  userlist=$4
  altuser=$5
  altstatus=$6

  for repo in "${dev1List[@]}"
  do
        response=$(
              curl -u$user:$password \
            --write-out %{http_code} \
            --silent \
            --output /dev/null \
            -X PUT $artbaseurl/api/storage/$repo/$namespace\?properties\=sandboxPerms.ownerUsers\=$userlist)
          if [ ! $response -eq 204 ]; then
              echo "....FAILED - Could not change property to user list.  error $response repository $repo"
          fi

          response=$(
            curl -u$user:$password \
              --write-out %{http_code} \
              --silent \
              --output /dev/null \
              -X PUT $artbaseurl/$repo/$namespace/http-builder-0.5.2.jar)
          if [ ! $response -eq 201 ]; then
            echo "....FAILED - property changed to user list. Should be able to deploy. error $response repository $repo"
          fi

      response=$(
            curl -u$altuser:jfrog \
              --write-out %{http_code} \
              --silent \
              --output /dev/null \
              -X PUT $artbaseurl/$repo/$namespace/http-builder-0.5.2.jar)
      if [ ! $response -eq $altstatus ]; then
            echo "....FAILED - $altuser not in list should not be able to deploy but has permission to deploy. error $response repository $repo OR"
            echo "....FAILED - $altuser belongs to development-team and should be able to deploy. error $response repository $repo"
      fi
  done
}

changepropertygroup() {
  namespace=$3
  user=$1
  password=$2
  groupname=$4
  altuser=$5
  altstatus=$6

  for repo in "${dev1List[@]}"
  do
        response=$(
                curl -u$user:$password \
                --write-out %{http_code} \
                --silent \
                --output /dev/null \
                -X PUT $artbaseurl/api/storage/$repo/$namespace\?properties\=sandboxPerms.ownerGroups\=$groupname)
        if [ ! $response -eq 204 ]; then
                echo "....FAILED - Could not change property to groups.  error $response repository $repo"
        fi

        response=$(
                curl -u$user:$password \
                --write-out %{http_code} \
                --silent \
                --output /dev/null \
                -X PUT $artbaseurl/$repo/$namespace/http-builder-0.5.2.jar)
        if [ ! $response -eq 201 ]; then
                echo "....FAILED - property changed to group. Should be able to deploy. error $response repository $repo"
        fi

      response=$(
                curl -u$altuser:jfrog \
                --write-out %{http_code} \
                --silent \
                --output /dev/null \
                -X PUT $artbaseurl/$repo/$namespace/http-builder-0.5.2.jar)
      if [ ! $response -eq $altstatus ]; then
                echo "....FAILED - $altuser not in list should not be able to deploy but has permission to deploy. error $response repository $repo OR"
                echo "....FAILED - $altuser belongs to dev-group and should be able to deploy. error $response repository $repo"
      fi
  done
}

#Delete property and start with new owners
#Inputs
# user: user
# password: password
# namespace - namespace to use - use namespace to b
# altuser: other user should not be able to deploy. Assume password is jfrog
deleteproperty() {
  namespace=$3
  user=$1
  password=$2
  altuser=$4

  for repo in "${dev1List[@]}"
  do
        response=$(
            curl -u$user:$password \
              --write-out %{http_code} \
              --silent \
              --output /dev/null \
              -X PUT $artbaseurl/$repo/$namespace/$user/http-builder-0.5.2.jar)
          if [ ! $response -eq 403 ]; then
            echo "....FAILED - Should not be able to deploy with $user. Make sure namespace is own by another user. error $response repository $repo"
          fi

        response=$(
              curl -u$user:$password \
            --write-out %{http_code} \
            --silent \
            --output /dev/null \
            -X DELETE $artbaseurl/api/storage/$repo/$namespace\?properties\=sandboxPerms.ownerUsers)
          if [ ! $response -eq 204 ]; then
              echo "....FAILED - Could not delete property.  error $response repository $repo"
          fi

          response=$(
            curl -u$user:$password \
              --write-out %{http_code} \
              --silent \
              --output /dev/null \
              -X PUT $artbaseurl/$repo/$namespace/$user/http-builder-0.5.2.jar)
          if [ ! $response -eq 201 ]; then
            echo "....FAILED - property deleted, user should be able to deploy. error $response repository $repo"
          fi

      response=$(
            curl -u$altuser:jfrog \
              --write-out %{http_code} \
              --silent \
              --output /dev/null \
              -X PUT $artbaseurl/$repo/$namespace/http-builder-0.5.2.jar)
      if [ ! $response -eq 403 ]; then
            echo "....FAILED - $altuser should be not be able to deploy. error $response repository $repo"
      fi
  done
}

#Admin to be able to delete artifacts irregardless of property set.
#
deleteartifactsbyadmin() {
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
            -X DELETE $artbaseurl/$repo/$namespace/http-builder-0.5.2.jar)
        if [ ! $response -eq 204 ]; then
              echo "....FAILED - fail to delete artifact if property set or cleared : error $response repository $repo"
        fi

        response=$(
              curl -u$user:$password \
            --write-out %{http_code} \
            --silent \
            --output /dev/null \
            -X DELETE $artbaseurl/$repo/$namespace/org/jfrog/com/folder/abc.jar)
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
            -X DELETE $artbaseurl/api/storage/$repo/$namespace\?properties\=sandboxPerms.ownerUsers)
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
    dlimage=$5
    docker pull $dlimage:latest
    docker login -u$user -p$password $dockerdomain
    docker tag $dlimage:latest $dockerdomain/$repo/$namespace:latest
    response=$(
      docker push $dockerdomain/$repo/$namespace\:latest)
    echo $response
}

testsuite() {
#### Begin Test Suites
####
    echo "Test1 - deploy by user belonging to artifactory group"
    deploygroupanduser dev1 jfrog test1 201
####
    echo "Test2 - deploy by user not part of artifactory group but has permission"
    deploygroupanduser eliom jfrog test2 201
####
    echo "Test3 - deploy by admin to existing namespace"
    deployadmin admin password test1 403
####
    echo "Test4 - deploy by user with admin privileges to existing namespace"
    deployadmin stanleyf jfrog test2 403
####
    echo "Test5 - attempt to override namespace by another member of artifactory group"
    deploygroupanduser dev2 jfrog test1 403
####
    echo "Test6 - change sandbox property by different user than owner; deploy fail; change property back"
    changeproperty dev1 jfrog test1
####
    echo "Test7 - change sandbox property to have multiple users and test deployment with user on list"
    changepropertylist dev1 jfrog test1 "jagans/dev1/dev2" jagans 201
####
    echo "Test8 - change sandbox property to have multple users and test deplopment with users not on list"
    changepropertylist dev1 jfrog test1 "jagans/dev1/dev2" ankushc 403
####
    echo "Test9 - change sandbox property to use groups deploy by user belonging to artifactory group"
    deploygroupanduser dev1 jfrog test9 201
    changepropertygroup dev1 jfrog test9 "dev-group" jainishs 201
####
    echo "Test10 - change sandbox property to use groups; deploy by user outside of group"
    deploygroupanduser dev1 jfrog test10 201
    changepropertygroup dev1 jfrog test10 "dev-group" ankushc 403
####
    echo "Test11 - delete property and verify artifact be deploy by new user"
    clearproperty dev1 jfrog deleteproperty
    deploygroupanduser dev1 jfrog deleteproperty 201
    deleteproperty dev2 jfrog deleteproperty jainishs
####
    echo "Test12 - admin should be able to delete if property set"
    deploygroupanduser dev1 jfrog admindeleteartifact 201
    deleteartifactsbyadmin admin password admindeleteartifact
####
    echo "Test13 - user with admin priveleges should be able if property set"
    deploygroupanduser dev1 jfrog aduserdeleteartifact 201
    deleteartifactsbyadmin stanleyf jfrog aduserdeleteartifact
####
    echo "Test14 - delete property then delete artifacts by admin"
    deploygroupanduser dev1 jfrog deletepropartifact 201
    clearproperty dev1 jfrog deletepropartifact
    deleteartifactsbyadmin admin password deletepropartifact
####
    echo "Test15 - docker deploy"
     deploydocker dev1 jfrog alpine docker-local "alpine"
####
    echo "Test16 - docker deploy with another user than the namespace owner"    
    deploydocker dev2 jfrog alpine docker-local "hello-world"
####
    echo "Test17 - docker deploy with virtual repository"
    deploydocker dev1 jfrog alpinevirtual docker "alpine"
####
    echo "Test18 - docker deploy with another user than the namespace owner using virtual repository"
    deploydocker dev2 jfrog alpinevirtual docker "alpine"
}
