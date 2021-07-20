artifactory 8088, {
    plugin 'metadata/addPypiNormalizedName'
    sed 'addPypiNormalizedName.groovy', /interval: \d+,/, 'interval: 3000,'
    sed 'AddPypiNormalizedNameTest.groovy', /(?<=\.url\(\'http:\/\/)localhost/, localhost
}
