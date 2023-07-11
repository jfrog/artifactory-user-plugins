artifactory 8088, {
  plugin 'storage/restrictNugetDeploy'
  sed 'RestrictNugetDeployTest.groovy', /remote.url\('http:\/\/localhost:/, "remote.url(\'http://$localhost:"
}
