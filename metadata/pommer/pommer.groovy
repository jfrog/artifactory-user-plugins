import org.artifactory.api.maven.MavenArtifactInfo
import org.artifactory.api.module.ModuleInfo
import org.artifactory.api.module.ModuleInfoUtils
import org.artifactory.maven.MavenModelUtils
import org.artifactory.repo.RepoPathFactory
import org.artifactory.repo.service.InternalRepositoryService
import org.artifactory.storage.db.util.DbUtils
import org.artifactory.storage.db.util.JdbcHelper
import org.artifactory.util.RepoLayoutUtils

import com.google.common.collect.HashMultimap

storage {
  afterCreate { item ->
    // when an artifact is created, make a pom file for it
    if (checkRepo(item.repoKey)) {
      upload(item)
    }
  }
}

executions {
  pommify() { params ->
    // retrieve the provided list of repositories to pommify
    def repos = params?.getAt('repos')
    // if the list is empty or nonexistant, return a 400
    if (!repos) {
      status = 400
      message = "Parameter 'repos' not present or empty"
      return
    }
    // if any repository on the list can't be checked, return a 400
    for (repo in repos) {
      if (!checkRepo(repo)) {
        status = 400
        message = "Repository '$repo' does not exist or is not a local Maven 2"
        return
      }
    }
    // otherwise, pommify each repository in turn, and return success
    for (repo in repos) {
      pommifyRepo(repo)
    }
    status = 200
    message = "Successfully added poms to all specified repositories"
  }
}

// given a repository name, create pom files for all artifacts in the repository
def pommifyRepo(repo) {
  // a multimap of module ids -> module infos
  def modules = HashMultimap.create()
  def reposerv = ctx.beanForType(InternalRepositoryService)
  // iterate over each file in the given repository
  for (node in getNodesList(repo)) {
    // if the file is a maven metdata, we don't care about it
    if (node.endsWith('/maven-metadata.xml')) continue
    // if the file does not follow the layout, we don't care about it
    def repopath = RepoPathFactory.create(repo, node)
    def modinf = reposerv.getItemModuleInfo(repopath)
    if (!modinf.isValid()) continue
    // otherwise, add the file to the multimap
    modules.put(modinf.prettyModuleId, [repopath, modinf])
  }
  // iterate over each module id in the multimap
  modules.asMap().each { key, values ->
    // if there is already a pom file at this location, don't bother
    if (values.any { it[1].ext == 'pom' }) return
    // otherwise, make a pom file
    upload(repositories.getItemInfo(values[0][0]))
  }
}

// query the database and select a list of all files in a repository
def getNodesList(repo) {
  def rs = null, nodelist = []
  def jdbcHelper = ctx.beanForType(JdbcHelper)
  // get the path of every file in the given repo
  def query = 'SELECT node_path, node_name FROM nodes'
  query += ' WHERE repo = ? AND node_type = 1'
  try {
    // execute the query and get the result set
    rs = jdbcHelper.executeSelect(query, repo)
    // add each result to the node list, as a file path
    while (rs.next()) {
      def path = rs.getString(1)
      if (path == '.') path = ''
      nodelist << path + '/' + rs.getString(2)
    }
  } finally {
    // close the result set
    if (rs) DbUtils.close(rs)
  }
  // return the completed node list
  return nodelist
}

// ensure that the given repository is a local maven 2 repository
def checkRepo(reponame) {
  def repo = repositories.getRepositoryConfiguration(reponame)
  if (!repo) return false
  if (repo.type != 'local' || repo.packageType != 'maven') return false
  if (repo.repoLayoutRef != 'maven-2-default') return false
  return true
}

// given an ItemInfo object, create and deploy a pom file for that artifact
def upload(item) {
  // only handle this upload if it is not itself a pom file
  if (item.relPath.endsWith('.pom')) return
  // extract the maven data from the path
  def layout = RepoLayoutUtils.MAVEN_2_DEFAULT
  def modinf = ModuleInfoUtils.moduleInfoFromArtifactPath(item.relPath, layout)
  if (!modinf.isValid()) return
  // build a path to the pom file
  def pompath = ModuleInfoUtils.constructDescriptorPath(modinf, layout, true)
  def pom = RepoPathFactory.create(item.repoKey, pompath)
  if (repositories.exists(pom)) return
  // build and deploy the pom file
  def info = MavenArtifactInfo.fromRepoPath(item.repoPath)
  def model = MavenModelUtils.toMavenModel(info)
  def pomstring = MavenModelUtils.mavenModelToString(model)
  log.info("Deploying pom file to '{}'", pom)
  repositories.deploy(pom, new ByteArrayInputStream(pomstring.bytes))
}
