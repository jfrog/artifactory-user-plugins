artifactory 8088, max: '5.10.4', {
    plugin "security/securityReplication"
    sed 'securityReplication.json', /"filter": 1/, '"filter": 3'
    sed 'securityReplication.json', /"0 0 0\/1 \* \* \?"/, '"0 0 0 * * ? 2000"'
    sed 'securityReplication.json', /"safety": true/, '"safety": "off"'
    // node 8081
    // node 8082
}

artifactory 8090, max: '5.10.4', {
    plugin "security/securityReplication"
    sed 'securityReplication.json', /"filter": 1/, '"filter": 3'
    sed 'securityReplication.json', /"0 0 0\/1 \* \* \?"/, '"0 0 0 * * ? 2000"'
    sed 'securityReplication.json', /"safety": true/, '"safety": "off"'
    sed 'securityReplication.json', /whoami": "http:\/\/localhost:8088/, 'whoami": "http://localhost:8090'
    // node 8083
    // node 8084
}

artifactory 8091, max: '5.10.4', {
    plugin "security/securityReplication"
    sed 'securityReplication.json', /"filter": 1/, '"filter": 3'
    sed 'securityReplication.json', /"0 0 0\/1 \* \* \?"/, '"0 0 0 * * ? 2000"'
    sed 'securityReplication.json', /"safety": true/, '"safety": "off"'
    sed 'securityReplication.json', /whoami": "http:\/\/localhost:8088/, 'whoami": "http://localhost:8091'
}

artifactory 8092, max: '5.10.4', {
    plugin "security/securityReplication"
    sed 'securityReplication.json', /"filter": 1/, '"filter": 3'
    sed 'securityReplication.json', /"0 0 0\/1 \* \* \?"/, '"0 0 0 * * ? 2000"'
    sed 'securityReplication.json', /"safety": true/, '"safety": "off"'
    sed 'securityReplication.json', /whoami": "http:\/\/localhost:8088/, 'whoami": "http://localhost:8092'
}
