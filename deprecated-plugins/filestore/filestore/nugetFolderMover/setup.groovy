artifactory 8088, {
    plugin 'filestore/nugetFolderMover'
    sed 'nugetFolderMover.groovy', 'cron: "0/10', 'cron: "0/2'
}
