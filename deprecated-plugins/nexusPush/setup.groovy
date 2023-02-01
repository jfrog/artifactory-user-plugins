artifactory 8088, {
    plugin 'nexusPush'
    dependency 'org.codehaus.groovy.modules.http-builder:http-builder:0.7.2'
    dependency 'net.sf.json-lib:json-lib:2.4:jdk15'
    dependency 'xml-resolver:xml-resolver:1.2'
    dependency 'org.ccil.cowan.tagsoup:tagsoup:1.2.1'
}

container 8081, {
    image 'sonatype/nexus:latest'
    internalPort 8081
    startupTime 10000
}
