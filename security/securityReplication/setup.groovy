artifactory 8088, {
    plugin "security/securityReplication"
    sed 'securityReplication.json', /"filter": 1/, '"filter": 3'
    sed 'securityReplication.json', /"0 0 0\/1 \* \* \?"/, '"0/10 * * * * ?"'
    // node 8081
    // node 8082
}

artifactory 8090, {
    plugin "security/securityReplication"
    sed 'securityReplication.json', /"filter": 1/, '"filter": 3'
    sed 'securityReplication.json', /"0 0 0\/1 \* \* \?"/, '"0/10 * * * * ?"'
    sed 'securityReplication.json', /whoami": "http:\/\/localhost:8088/, 'whoami": "http://localhost:8090'
    // node 8083
    // node 8084
}

artifactory 8091, {
    plugin "security/securityReplication"
    sed 'securityReplication.json', /"filter": 1/, '"filter": 3'
    sed 'securityReplication.json', /"0 0 0\/1 \* \* \?"/, '"0/10 * * * * ?"'
    sed 'securityReplication.json', /whoami": "http:\/\/localhost:8088/, 'whoami": "http://localhost:8091'
}

artifactory 8092, {
    plugin "security/securityReplication"
    sed 'securityReplication.json', /"filter": 1/, '"filter": 3'
    sed 'securityReplication.json', /"0 0 0\/1 \* \* \?"/, '"0/10 * * * * ?"'
    sed 'securityReplication.json', /whoami": "http:\/\/localhost:8088/, 'whoami": "http://localhost:8092'
}
