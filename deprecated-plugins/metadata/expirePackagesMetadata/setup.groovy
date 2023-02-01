artifactory 8088, {
  plugin 'metadata/expirePackagesMetadata'
  sed 'ExpirePackagesMetadataTest.groovy', /remoteBuilder.url\('http:\/\/localhost:/, "remoteBuilder.url(\'http://$localhost:"
}
