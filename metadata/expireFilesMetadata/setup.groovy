artifactory 8088, {
  plugin 'metadata/expireFilesMetadata'
  sed 'ExpireFilesMetadataTest.groovy', /remoteBuilder.url\('http:\/\/localhost:/, "remoteBuilder.url(\'http://$localhost:"
}
