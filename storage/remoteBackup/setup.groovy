artifactory 8088, {
  plugin 'storage/remoteBackup'
  sed 'RemoteBackupTest.groovy', /remote.url\('http:\/\/localhost:/, "remote.url(\'http://$localhost:"
}
