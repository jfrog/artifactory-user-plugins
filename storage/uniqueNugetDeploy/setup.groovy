artifactory 8088, {
  plugin 'storage/uniqueNugetDeploy'
  sed 'UniqueNugetDeployTest.groovy', /remote.url\('http:\/\/localhost:/, "remote.url(\'http://$localhost:"
}
