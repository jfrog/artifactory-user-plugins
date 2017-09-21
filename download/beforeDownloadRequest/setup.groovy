artifactory 8088, {
  plugin 'download/beforeDownloadRequest'
  sed 'beforeDownloadRequest.groovy', /JSON_CACHE_MILLIS\s*=\s*(\d+)(.*)?/, "JSON_CACHE_MILLIS = 1000L"
  sed 'BeforeDownloadRequestTest.groovy', /remote.url\('http:\/\/localhost:/, "remote.url(\'http://$localhost:"
}
