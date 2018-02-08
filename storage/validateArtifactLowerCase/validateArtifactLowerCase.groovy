    import org.artifactory.build.promotion.PromotionConfig
    import org.artifactory.build.staging.ModuleVersion
    import org.artifactory.build.staging.VcsConfig
    import org.artifactory.exception.CancelException
    import org.artifactory.repo.RepoPathFactory
    import org.artifactory.request.Request
    import org.artifactory.util.StringInputStream
    import groovy.json.JsonSlurper

    def repositories = new JsonSlurper().parse(new File(ctx.artifactoryHome.haAwareEtcDir, 'plugins/validateArtifactLowerCase.json'))
    storage {
        beforeCreate {
            item ->
                def repositorykey = item.repoPath.repoKey
               if (repositorykey in repositories) {
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
