artifactory 8088, {
    plugin 'storage/quotaWarn'
    sed 'QuotaWarnTest.groovy', 'smtpHost = "localhost', 'smtpHost = "' + localhost
}
