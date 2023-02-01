artifactory 8088, {
    plugin 'download/beforeSymbolServerDownload'
    sed 'BeforeSymbolServerDownloadTest.groovy', /url = \"http:\/\/localhost/, 'url = "http://' + localhost
}

artifactory 8081, {
}
