import org.artifactory.repo.RepoPath
import org.artifactory.repo.RepoPathFactory

jobs {
	deleteApkIndexFiles(delay: 0, interval: 60000) {
        alpineRepos = getAlpineRepos()
        alpineCaches = alpineRepos.collect { it.getRepoKey() + '-cache' }
        indexFiles = searches.artifactsByName('APKINDEX.tar.gz', *alpineCaches)
        log.info("Found apk index files that will be deleted: {}", indexFiles)
        indexFiles.each {
            log.info("Deleting apk index files: {}", it)
            repositories.delete(it)
        }
    }
}

/**
 * Returns a list of local alpine repositories, determined by the existence of the 'alpine' property.
 */
List<RepoPath> getAlpineRepos() {
	repositories.getRemoteRepositories()
		.collect { RepoPathFactory.create(it) }
		.findAll { repositories.hasProperty(it, 'alpine') }
}
