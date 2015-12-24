import com.sun.jersey.core.util.MultivaluedMapImpl
import org.artifactory.addon.nuget.repomd.NuGetArtifactoryService
import org.artifactory.addon.nuget.repomd.NuGetPackageWorkContext
import org.artifactory.addon.nuget.rest.NuGetRequestContext
import org.artifactory.addon.nuget.search.delegate.id.FindPackagesByIdFeedRequestDelegate
import org.artifactory.exception.CancelException
import org.artifactory.repo.RepoPathFactory
import org.artifactory.repo.service.InternalRepositoryService
import org.jfrog.repomd.nuget.rest.handler.NuGetLocalRepoHandler
import org.jfrog.repomd.nuget.rest.request.NuGetSearchParameters

import javax.ws.rs.core.*

class FakeUriInfo implements UriInfo {
    MultivaluedMap<String,String> ps;
    public FakeUriInfo(MultivaluedMap<String,String> ps) {this.ps = ps}
    MultivaluedMap<String,String> getQueryParameters() {ps}
    MultivaluedMap<String,String> getQueryParameters(boolean decode) {ps}
    UriBuilder getRequestUriBuilder() {UriBuilder.newInstance()}
    URI getAbsolutePath() {null}
    UriBuilder getAbsolutePathBuilder() {null}
    URI getBaseUri() {null}
    UriBuilder getBaseUriBuilder() {null}
    List<Object> getMatchedResources() {null}
    List<String> getMatchedURIs() {null}
    List<String> getMatchedURIs(boolean decode) {null}
    String getPath() {null}
    String getPath(boolean decode) {null}
    MultivaluedMap<String,String> getPathParameters() {null}
    MultivaluedMap<String,String> getPathParameters(boolean decode) {null}
    List<PathSegment> getPathSegments() {null}
    List<PathSegment> getPathSegments(boolean decode) {null}
    URI getRequestUri() {null}
    URI relativize(URI uri) {null}
    URI resolve(URI uri) {null}
}

storage {
    beforeCreate { item ->
        def cpath = "plugins/restrictNugetDeploy.properties"
        def cfile = new File(ctx.artifactoryHome.haAwareEtcDir, cpath)
        def config = new ConfigSlurper().parse(cfile.toURL())
        def repoKeys = config.repos as String[]
        if (!item || item.isFolder() || !item.name.endsWith('.nupkg')) return
        def repoConf = repositories.getRepositoryConfiguration(item.repoKey)
        if (!repoConf.isEnableNuGetSupport()) return
        def layout = repositories.getLayoutInfo(item.repoPath)
        def id  = null
        if (layout.isValid()) id = layout.module
        else id = (item.name =~ '^(?:\\D[^.]*\\.)+')[0] - ~'\\.$'
        def repoService = ctx.beanForType(InternalRepositoryService.class)
        def ps = new MultivaluedMapImpl()
        ps.add('id', "'$id'")
        def context = new NuGetRequestContext()
        context.uriInfo = new FakeUriInfo(ps)
        context.nuGetSearchParameters = new NuGetSearchParameters(ps)
        def delegate = new FindPackagesByIdFeedRequestDelegate(context)
        for (repoKey in repoKeys) {
            def response = null
            def repo = repoService.repositoryByKey(repoKey)
            if (repo.isReal() && repo.isLocal()) {
                def repoPath = RepoPathFactory.create(repoKey)
                def workContext = new NuGetPackageWorkContext(repoPath)
                def newRepo = new NuGetArtifactoryService(workContext, repoKey)
                def uri = context.uriInfo
                def localHandler = new NuGetLocalRepoHandler(newRepo, uri, null)
                response = localHandler.findPackagesById()
            } else if (repo.isReal()) {
                response = delegate.handleRequestForRemote(repo)
            } else {
                response = delegate.handleRequestForVirtual(repo)
            }
            def entct = response.entity.entries?.size() ?: 0
            if (entct > 0) {
                def msg = "A package with the name '$id' already exists in"
                msg += " repository '$repoKey'."
                throw new CancelException(msg, 409)
            }
        }
    }
}
