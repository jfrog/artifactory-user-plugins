artifactory 8088, {
    plugin "security/securityReplication"
    sed 'securityReplication.json', /"filter":1/, '"filter":3'
}
