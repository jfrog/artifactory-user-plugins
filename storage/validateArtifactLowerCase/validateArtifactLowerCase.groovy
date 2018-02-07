import org.artifactory.build.promotion.PromotionConfig
import org.artifactory.build.staging.ModuleVersion
import org.artifactory.build.staging.VcsConfig
import org.artifactory.exception.CancelException
import org.artifactory.repo.RepoPathFactory
import org.artifactory.request.Request
import org.artifactory.util.StringInputStream

storage {
    beforeCreate {
        item - >
            def repositorykey = item.repoPath.repoKey
        def repositories = new JsonSlurper().parse(new File(ctx.artifactoryHome.haAwareEtcDir, 'plugins/validateArtifactLowerCase.json'))
        repositories.each {

            if (repositorykey == it) {

                def a = item.name
                def b = a.toLowerCase()
                if (a.equals(b)) {
                    log.info("all are lowercase and import is success")
                } else {
                    throw new CancelException("Please upload Artifacts with lowercase", 403)
                }
            }
        }
    }
}