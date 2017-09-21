artifactory 8088, {
    plugin 'replication/yumReplicationFilter'
    sed 'YumReplicationFilterTest.groovy', /(?<=def replicationurl = .{0,10})localhost/, localhost
}

artifactory 8081, {
}
