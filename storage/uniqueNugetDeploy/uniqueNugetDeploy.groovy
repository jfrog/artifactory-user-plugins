/*
 * Copyright (C) 2015 JFrog Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.sun.jersey.core.util.MultivaluedMapImpl
import org.artifactory.addon.nuget.rest.NuGetRequestContext
import org.artifactory.addon.nuget.search.delegate.packages.PackageEntryRequestDelegate
import org.artifactory.exception.CancelException
import org.artifactory.repo.RepoPathFactory
import org.artifactory.repo.service.InternalRepositoryService
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.util.UriComponentsBuilder

import javax.ws.rs.core.*

def ngsps3 = null, ngsps4 = null, nglrh = null, ngpwc = null, ngas = null
try {
    ngsps4 = org.jfrog.repomd.nuget.rest.request.NuGetSearchParameters.class
} catch (MissingPropertyException ex) {
    ngsps3 = org.artifactory.addon.nuget.search.NuGetSearchParameters.class
}
try {
    nglrh = org.jfrog.repomd.nuget.rest.handler.NuGetLocalRepoHandler.class
    ngpwc = org.artifactory.addon.nuget.repomd.NuGetPackageWorkContext.class
    ngas = org.artifactory.addon.nuget.repomd.NuGetArtifactoryService.class
} catch (MissingPropertyException ex) {}

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
        def cpath = "plugins/uniqueNugetDeploy.properties"
        def cfile = new File(ctx.artifactoryHome.haAwareEtcDir, cpath)
        def config = new ConfigSlurper().parse(cfile.toURL())
        def repoKeys = config.checkedRepos as String[]
        def filtKeys = config.filteredRepos as String[]
        if (!item || !(item.repoKey in filtKeys)) return
        if (item.isFolder() || !item.name.endsWith('.nupkg')) return
        def repoConf = repositories.getRepositoryConfiguration(item.repoKey)
        if (!repoConf.isEnableNuGetSupport()) return
        def layout = repositories.getLayoutInfo(item.repoPath)
        def id = null
        def ver = null
        if (layout.isValid()) {
            id = layout.module
            ver = layout.baseRevision
        } else {
            def match = item.name =~ '^((?:\\D[^.]*\\.)+)((?:\\d[^.]*\\.)+)'
            id = match[0][1] - ~'\\.$'
            ver = match[0][2] - ~'\\.$'
        }
        def urlid = "(Id='$id',Version='$ver')"
        def repoService = ctx.beanForType(InternalRepositoryService.class)
        def ps3 = new LinkedMultiValueMap()
        def ps4 = new MultivaluedMapImpl()
        def context = new NuGetRequestContext()
        try {
            context.uriInfo = new FakeUriInfo(ps4)
        } catch (MissingPropertyException ex) {}
        try {
            def compBuilder = UriComponentsBuilder.newInstance()
            compBuilder.pathSegment('x')
            context.uriComponents = compBuilder.build()
        } catch (MissingPropertyException ex) {}
        if (ngsps4 != null) {
            def params = [ps4] as Object[]
            context.nuGetSearchParameters = ngsps4.newInstance(params)
        } else {
            def params = [ps3, ''] as Object[]
            context.nuGetSearchParameters = ngsps3.newInstance(params)
        }
        def delegate = new PackageEntryRequestDelegate(context, urlid)
        for (repoKey in repoKeys) {
            def response = null
            def repo = repoService.repositoryByKey(repoKey)
            if (repo.isReal() && repo.isLocal() && nglrh == null) {
                response = delegate.handleRequestForLocalOrCache(repo)
            } else if (repo.isReal() && repo.isLocal()) {
                def repoPath = RepoPathFactory.create(repoKey)
                def wcparams = [repoPath] as Object[]
                def workContext = ngpwc.newInstance(wcparams)
                def nrparams = [workContext, repoKey] as Object[]
                def newRepo = ngas.newInstance(nrparams)
                def uri = context.uriInfo
                def lhparams = [newRepo, uri, null] as Object[]
                def localHandler = nglrh.newInstance(lhparams)
                response = localHandler.packageEntry(urlid)
            } else if (repo.isReal()) {
                response = delegate.handleRequestForRemote(repo)
            } else {
                response = delegate.handleRequestForVirtual(repo)
            }
            if (response.status == 200) {
                def msg = "A package with the name '$id' and version '$ver'"
                msg += " already exists in repository '$repoKey'."
                throw new CancelException(msg, 409)
            }
        }
    }
}
