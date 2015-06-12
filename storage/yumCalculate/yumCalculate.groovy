import org.artifactory.repo.LocalRepositoryConfiguration
import org.artifactory.repo.RepoPath
import org.artifactory.repo.RepoPathFactory
import org.artifactory.addon.yum.InternalYumAddon

// usage: curl -X POST -u admin:password http://localhost:8088/artifactory/api/plugins/execute/yumCalculate?params=path=yum-repository/path/to/dir

def calculateYumMetadata(RepoPath repoPath) {
    def dirDepth = repoPath.path.empty ? 0 : repoPath.path.split('/').length
    def repoConf = (LocalRepositoryConfiguration) repositories.getRepositoryConfiguration(repoPath.repoKey)
    if (repoConf.yumRootDepth != dirDepth) {
        return "Given directory $repoPath.path not at YUM repository's configured depth"
    }
    if (repoConf.calculateYumMetadata) {
        return "YUM metadata is set to calculate automatically"
    }
    def yumBean = ctx.beanForType(InternalYumAddon.class)
    yumBean.calculateYumMetadata(repoPath)
    return 0
}

executions {
    yumCalculate() { params ->
        def err = calculateYumMetadata(RepoPathFactory.create(params.path[0]))
        if (err) {
            status = 403
            message = err
        } else {
            status = 200
            message = "YUM metadata successfully calculated"
        }
    }
}
