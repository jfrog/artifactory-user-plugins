artifactory 8088, {
    plugin 'pgp/pgpVerify'
    sed 'pgpVerify.properties', /repos=.*/, "repos=[\'maven-local\']"
    sed 'pgpVerify.groovy', /http:\/\/pgp.mit.edu:11371/, "http://localhost:8000"
    dependency 'org.codehaus.groovy.modules.http-builder:http-builder:0.7.2'
    dependency 'net.sf.json-lib:json-lib:2.4:jdk15'
    dependency 'xml-resolver:xml-resolver:1.2'
}
