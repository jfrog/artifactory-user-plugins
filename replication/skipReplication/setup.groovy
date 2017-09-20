artifactory 8088, {
    plugin 'replication/skipReplication'
    sed 'SkipReplicationTest.groovy', /(?<=def replicationurl = .{0,10})localhost/, localhost
}

artifactory 8081, {
}
