artifactory 8088, {
  plugin 'download/yumValidRemote'
  sed 'YumValidRemoteTest.groovy', /remote.url\('http:\/\/localhost:/, "remote.url(\'http://$localhost:"
}
