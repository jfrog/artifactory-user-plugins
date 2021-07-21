artifactory 8088, {
    plugin 'deploy/remoteDownload'
    dependency 'org.codehaus.groovy.modules.http-builder:http-builder:0.7.2'
    dependency 'net.sf.json-lib:json-lib:2.4:jdk15'
    dependency 'xml-resolver:xml-resolver:1.2'
    sed 'conf.json', /localhost/, localhost
}
