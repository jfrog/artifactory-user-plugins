artifactory 8088, {
    plugin 'storage/quotaWarnSlack'
    sed 'quotaWarnSlack.json', /WEBHOOK_URL/, 'http://localhost:8000/'
}
