artifactory 8088, {
    plugin 'config/getAndSetP2Url'
    sed 'GetAndSetP2UrlTest.groovy', /(?<=url: \'http:\/\/)localhost/, localhost
    sed 'GetAndSetP2UrlTest.groovy', /(?<=urls << \'http:\/\/)localhost/, localhost
    sed 'GetAndSetP2UrlTest.groovy', /(?<=modresponse.urls.contains\(\'http:\/\/)localhost/, localhost
}
