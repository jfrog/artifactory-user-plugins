import org.artifactory.repo.RepoPath
import org.artifactory.repo.RepoPathFactory

/**
 * A CRON job to remove all apk index files from cache repositories marked 'alpine'.
 * Adjust the interval (in ms) at will. Note that when the interval is large you run a larger
 * risk for a not found error. This is because the index is cached and presetn, but if the client
 * requests a non-cached updated file, this will not be present in the upstream.
 * A smaller value will of course produce more server load.
 */
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
