artifactory 8082, {
    plugin 'ha/inactiveServerCleaner'
    plugin 'config/haClusterDump'
    node 8088
    node 8081
}
