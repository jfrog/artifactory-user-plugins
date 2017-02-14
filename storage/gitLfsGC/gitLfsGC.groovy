import groovy.json.JsonSlurper
import groovy.json.JsonException
import org.artifactory.repo.RepoPathFactory
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.transport.CredentialItem
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.eclipse.jgit.treewalk.TreeWalk
import static org.eclipse.jgit.api.ListBranchCommand.ListMode.ALL

// Extract the cron expression from the config file and return it. The default
// is every night at midnight.
def getCron() {
  def etcdir = ctx.artifactoryHome.haAwareEtcDir
  def cfgfile = new File(etcdir, 'plugins/gitLfsGC.json')
  try {
    def cfg = new JsonSlurper().parse(cfgfile)
    return cfg.cron ?: '0 0 0 * * ?'
  } catch (JsonException ex) {
    return '0 0 0 * * ?'
  }
}

// Clean all configured repositories on a cron job.
jobs {
  gitLfsGCJob(cron: getCron()) {
    try {
      cleanLFS(null)
    } catch (RuntimeException ex) {
      if (ex.cause) log.error(ex.message, ex)
      else log.error(ex.message)
    }
  }
}

// Clean provided (or all) configured repositories when executed.
executions {
  gitLfsGC(groups: ['readers'] as Set) { params ->
    status = 200
    def stat = null, repo = params?.getAt('repos') as List ?: null
    try {
      stat = cleanLFS(repo)
    } catch (RuntimeException ex) {
      if (ex.cause) log.error(ex.message, ex)
      else log.error(ex.message)
      status = 400
      message = ex.message
    }
    stat = stat.findAll { k, v -> v[0] > v[1] }
    if (stat) {
      status = 400
      message = "Error: Artifacts could not be cleaned in repositories: "
      message += stat.keySet().toString()
      message += ". Check your permissions."
    }
  }
}

// Given a git repository, get a list of sha256 checksums for all the
// LFS-tracked files that currently exist in any of the branches. These are the
// files that need to be preserved.
def getSha2s(git) {
  def result = [] as Set, oids = [], repo = git.repository
  def twalk = null, rwalk = null
  // iterate over all the branches in this repository
  for (branch in git.branchList().setListMode(ALL).call()) {
    try {
      // extract a file tree from the branch commit, and walk it
      rwalk = new RevWalk(repo)
      twalk = new TreeWalk(repo)
      twalk.reset()
      twalk.recursive = true
      twalk.addTree(rwalk.parseCommit(branch.objectId).tree)
      while (twalk.next()) {
        // if we've already seen this file, don't bother
        def oid = twalk.getObjectId(0)
        if (oid in oids) continue
        oids << oid
        // get the contents of the file (the first 130 characters should be
        // enough). If this file is LFS-tracked, there will be a line with the
        // oid, which is just the sha256 checksum. So, parse it out and add it
        // to the list if it's there.
        def hunk = new String(repo.open(oid).getBytes(130))
        def matcher = hunk =~ '(?ms).*^oid sha256:([a-f0-9]{64})$.*'
        if (matcher.matches()) result << matcher[0][1]
      }
    } finally {
      twalk?.close()
      rwalk?.close()
    }
  }
  // return the list of checksums
  return result as List
}

// Add the credentials specificed in the config to a git command object.
def addCreds(cmd, cfg) {
  def cp = null, url = cfg.repourl
  if (url.startsWith('http://') || url.startsWith('https://')) {
    if (!cfg.username) return
    if (!cfg.password) cfg.password = ""
    // there's a built-in credentials provider for http authentication, use it
    cp = new UsernamePasswordCredentialsProvider(cfg.username, cfg.password)
  } else if (url.startsWith('ssh://')) {
    if (!cfg.password) return
    // there's no built-in provider for ssh, so we have to make our own
    cp = [
      isInteractive: { false },
      supports: { CredentialItem... items -> true },
      get: { uri, CredentialItem... items ->
        items.each { it.value = cfg.password }
        return true
      }
    ] as CredentialsProvider
  }
  // add the credentials provider to the command
  if (cp) cmd.credentialsProvider = cp
}

// Recursively descend into a directory and add every file to the results list.
// This results in a list of all files contained in a given directory tree.
def gatherFiles(iteminfo, results) {
  if (iteminfo.isFolder()) {
    for (child in repositories.getChildren(iteminfo.repoPath)) {
      gatherFiles(child, results)
    }
  } else {
    results << iteminfo.relPath
  }
}

// Perform garbage collection to delete unused LFS artifacts. To do this, clone
// the correlated git repos and retrieve available LFS-tracked checksums, then
// delete all artifacts that don't match those checksums.
def cleanLFS(workrepos) {
  // read the config file
  def status = [:], etcdir = ctx.artifactoryHome.haAwareEtcDir
  def cfg = null, cfgfile = new File(etcdir, 'plugins/gitLfsGC.json')
  try {
    cfg = new JsonSlurper().parse(cfgfile)
  } catch (JsonException ex) {
    throw new RuntimeException("Error reading config $cfg: $ex.message")
  }
  // decide on a directory to use to store the git clones in: if one is
  // specified in the config, use that, otherwise use the system default
  def tmpdir = null
  if (cfg.tmpdir) {
    tmpdir = new File(cfg.tmpdir)
  } else {
    def tmpfoldername = 'artifactoryGitLFSGCPlugin'
    tmpdir = new File(System.getProperty('java.io.tmpdir'), tmpfoldername)
  }
  log.info("Checking temp directory $tmpdir")
  try {
    tmpdir.mkdirs()
    if (!tmpdir.isDirectory() || !tmpdir.canRead() || !tmpdir.canExecute()) {
      throw new RuntimeException("Temp directory inaccessible: check privs")
    }
  } catch (SecurityException ex) {
    throw new RuntimeException("Temp directory inaccessible: $ex.message")
  }
  // iterate over all of the configured repositories, or just the provided ones
  // if running as an execution
  def repos = cfg.repositories?.entrySet()
  if (workrepos) {
    def missing = workrepos - repos.collect { it.key }
    if (missing) {
      throw new RuntimeException("Specified repos do not exist: $missing")
    }
    repos = repos.findAll { it.key in workrepos }
  }
  for (repo in cfg.repositories?.entrySet()) {
    log.info("Cleaning repo $repo.key")
    def files = [], repofiles = [], git = null
    def gitdir = new File(tmpdir, repo.key)
    // get the list of files in the repository; this needs to be done first so
    // that any files uploaded during the clone process won't be deleted
    // accidentally
    def root = repositories.getItemInfo(RepoPathFactory.create(repo.key, ''))
    gatherFiles(root, repofiles)
    try {
      // if the git repo exists already, fetch the latest changes
      if (gitdir.exists()) {
        log.info("Updating git repo for $repo.key")
        git = Git.open(gitdir)
        // if the repo doesn't fetch from the configured remote (say, if the
        // configuration has changed), delete it and reclone
        def confurl = new URIish(repo.value.repourl)
        def remotes = git.remoteList().call()
        def remote = remotes.find { it.getURIs().find { it == confurl }}
        if (!remote) {
          log.info("Git repo for $repo.key out of date, deleting")
          git.close()
          git = null
          if (!gitdir.deleteDir()) {
            throw new RuntimeException("Cannot delete directory $gitdir")
          }
        } else {
          log.info("Fetching git repo $repo.value.repourl")
          def fetch = git.fetch()
          fetch.remote = remote.name
          addCreds(fetch, repo.value)
          fetch.setRemoveDeletedRefs(true).call()
        }
      }
      // if the git repo doesn't exist, clone it
      if (!git) {
        log.info("Cloning new git repo $repo.value.repourl")
        def clone = Git.cloneRepository()
        clone.bare = true
        clone.cloneAllBranches = true
        clone.noCheckout = true
        clone.gitDir = gitdir
        addCreds(clone, repo.value)
        git = clone.setURI(repo.value.repourl).call()
      }
      // collect the shas from the git repo and turn them into file paths
      log.info("Collecting files from $repo.value.repourl")
      files = getSha2s(git).collect {
        "objects/${it[0..1]}/${it[2..3]}/$it".toString()
      }
    } catch (GitAPIException ex) {
      throw new RuntimeException("Error accessing Git: $ex.message", ex)
    } catch (IOException ex) {
      throw new RuntimeException("Error accessing Git: $ex.message", ex)
    } finally {
      git?.close()
    }
    // delete all files in the repository that aren't in the git sha list
    log.info("Deleting ${(repofiles - files).size()} files from $repo.key")
    def attempt = 0, succeed = 0
    for (file in repofiles - files) {
      def stat = repositories.delete(RepoPathFactory.create(repo.key, file))
      if (!stat.isError()) succeed += 1
      attempt += 1
    }
    log.info("Deleted $succeed files from $repo.key")
    status[repo.key] = [attempt, succeed]
  }
  return status
}
