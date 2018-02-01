artifactory 8088, {
    plugin 'build/buildSync'
    dependency 'org.codehaus.groovy.modules.http-builder:http-builder:0.7.2'
    dependency 'net.sf.json-lib:json-lib:2.4:jdk15'
    dependency 'xml-resolver:xml-resolver:1.2'
    dependency 'net.sf.ezmorph:ezmorph:1.0.6'
    sed 'buildSync.json', /"password": "AKCp2.*a2iwX"/, '"password": "password"'
}

artifactory 8081, {
    plugin 'build/buildSync'
    dependency 'org.codehaus.groovy.modules.http-builder:http-builder:0.7.2'
    dependency 'net.sf.json-lib:json-lib:2.4:jdk15'
    dependency 'xml-resolver:xml-resolver:1.2'
    dependency 'net.sf.ezmorph:ezmorph:1.0.6'
    sed 'buildSync.json', /"password": "AKCp2.*a2iwX"/, '"password": "password"'
    sed 'buildSync.json', /http:\/\/localhost:8081/, 'http://localhost:8088'
}
