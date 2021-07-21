artifactory 8088, {
  plugin 'storage/uniqueNugetDeploy'
  dependency 'com.sun.jersey:jersey-core:1.19.4'
  sed 'UniqueNugetDeployTest.groovy', /remote.url\('http:\/\/localhost:/, "remote.url(\'http://$localhost:"
}
