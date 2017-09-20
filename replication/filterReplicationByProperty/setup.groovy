artifactory 8088, {
    plugin 'replication/filterReplicationByProperty'
    sed 'FilterReplicationByPropertyTest.groovy', /(?<=def replicationurl = .{0,10})localhost/, localhost
}

artifactory 8081, {
}
