/*
 * Copyright (C) 2020 JFrog Ltd.
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

import groovy.json.JsonBuilder
import java.util.zip.GZIPInputStream
import org.artifactory.addon.yum.InternalYumAddon
import org.artifactory.exception.CancelException
import org.artifactory.repo.RepoPathFactory
import org.artifactory.repo.service.InternalRepositoryService
import org.artifactory.request.NullRequestContext
import org.artifactory.resource.ResourceStreamHandle
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.constructor.Constructor
import org.yaml.snakeyaml.nodes.Tag
import org.yaml.snakeyaml.parser.ParserException
import org.yaml.snakeyaml.representer.Representer
import org.yaml.snakeyaml.resolver.Resolver

// A list of groups that are allowed to use this plugin, in addition to admin
// users. The default is 'readers', which all authenticated users belong to by
// default.
def groups = ['readers']

executions {
    // Create a new module. Takes the following parameters:
    // - target: a path to the location where the module should be added
    // - force: [optional] true if the module should be added even if not all of
    //   its RPMs exist in the repository
    // - request body: the module definition, written in yaml
    createModule(groups: groups) { params, ResourceStreamHandle body ->
        try {
            def args = extractParams(params)
            enforceParams(["target", args.target])
            def module = readModule(body.inputStream)
            def paths = getPaths(args.target)
            if ("modules" in paths) {
                enforceReadPermissions(paths.modules, true)
            }
            if ("primary" in paths) {
                enforceReadPermissions(paths.primary, true)
            }
            verifyNewModule(paths, module, args.force)
            enforceWritePermissions(paths.repodata)
            create(paths, module)
        } catch (CancelException ex) {
            log.info(ex.message)
            status = ex.errorCode
            message = ex.message
        }
    }

    // Delete a module. Takes the following parameters:
    // - target: a path to the location from which the module should be removed
    // - name: the name of the module to delete
    // - version: the version of the module to delete
    deleteModule(groups: groups) { params ->
        try {
            def args = extractParams(params)
            enforceParams(["target", args.target], ["name", args.name], ["version", args.version])
            def paths = getPaths(args.target)
            if ("modules" in paths) {
                enforceReadPermissions(paths.modules, true)
                enforceWritePermissions(paths.repodata)
                delete(paths, args.name, args.version)
            }
        } catch (CancelException ex) {
            log.info(ex.message)
            status = ex.errorCode
            message = ex.message
        }
    }

    // Return a given module definition yaml. Takes the following parameters:
    // - target: a path to the location where the module can be found
    // - name: the name of the module to view
    // - version: the version of the module to view
    viewModule(httpMethod: 'GET', groups: groups) { params ->
        try {
            def args = extractParams(params)
            enforceParams(["target", args.target], ["name", args.name], ["version", args.version])
            def paths = getPaths(args.target)
            if ("modules" in paths) {
                enforceReadPermissions(paths.modules, true)
            }
            def mod = find(paths.modules, args.name, args.version)
            if (mod == null) {
                message = "Cannot find module: $args.name:$args.version"
                status = 404
                log.trace(message)
            } else {
                message = newYaml().dump(mod)
                status = 200
            }
        } catch (CancelException ex) {
            log.info(ex.message)
            status = ex.errorCode
            message = ex.message
        }
    }

    // Verify that a given module has all of its RPMs. Takes the following parameters:
    // - target: a path to the location where the module can be found
    // - name: the name of the module to verify
    // - version: the version of the module to verify
    verifyModule(httpMethod: 'GET', groups: groups) { params ->
        try {
            def args = extractParams(params)
            enforceParams(["target", args.target], ["name", args.name], ["version", args.version])
            def paths = getPaths(args.target)
            if ("modules" in paths) {
                enforceReadPermissions(paths.modules, true)
            }
            if ("primary" in paths) {
                enforceReadPermissions(paths.primary, true)
            }
            def mod = find(paths.modules, args.name, args.version)
            if (mod == null) {
                message = "Cannot find module: $args.name:$args.version"
                status = 404
                log.trace(message)
            } else {
                message = verify(paths, mod)
                status = 200
            }
        } catch (CancelException ex) {
            log.info(ex.message)
            status = ex.errorCode
            message = ex.message
        }
    }

    // Copy/promote a module to a new location. Takes the following parameters:
    // - source: a path to the location from where the module should be copied
    // - target: a path to the location to where the module should be copied
    // - name: the name of the module to copy
    // - version: the version of the module to copy
    // - move: [optional] true if the module should be moved instead of copied
    // - downloadMissing: [optional] true if the module's RPM files should be
    //   cached before copying, assuming we're copying from a remote repository
    //   (if this is false, the first non-cached artifact will cause a failure)
    promoteModule(groups: groups) { params ->
        try {
            def args = extractParams(params)
            enforceParams(["source", args.source], ["target", args.target], ["name", args.name], ["version", args.version])
            def srcpaths = getPaths(args.source)
            def paths = getPaths(args.target)
            if (!(paths.root.repoKey in repositories.localRepositories)) {
                throw new CancelException("Target repository must be local", 400)
            }
            if ("modules" in srcpaths) {
                enforceReadPermissions(srcpaths.modules, true)
            }
            if ("modules" in paths) {
                enforceReadPermissions(paths.modules, true)
            }
            enforceWritePermissions(paths.repodata)
            def module = find(srcpaths.modules, args.name, args.version)
            if (module == null) {
                message = "Cannot find module: $args.name:$args.version"
                status = 404
                log.trace(message)
            } else {
                copyRPMs(srcpaths, paths, module, args.downloadMissing)
                create(paths, module)
                if (args.move && srcpaths.modules.repoKey in repositories.localRepositories) {
                    enforceWritePermissions(srcpaths.repodata)
                    delete(srcpaths, args.name, args.version)
                }
            }
        } catch (CancelException ex) {
            log.info(ex.message)
            status = ex.errorCode
            message = ex.message
        }
    }
}

// A custom resolver for the yaml parser. This removes support for parsing
// integers and floats, causing those values to parse as strings instead.
// Without this, some version numbers will parse incorrectly, causing those
// versions to become unavailable.
class CustomResolver extends Resolver {
    protected void addImplicitResolvers() {
        addImplicitResolver(Tag.MERGE, MERGE, "<")
        addImplicitResolver(Tag.NULL, NULL, "~nN\0")
        addImplicitResolver(Tag.NULL, EMPTY, null)
    }
}

// Create a new yaml parser using the above custom resolver.
def newYaml() {
    return new Yaml(new Constructor(), new Representer(),
                    new DumperOptions(), new CustomResolver())
}

// Update a modules.yaml file, adding a new module to it. Replace an existing
// module with the same name and version if it exists. Add the new module to the
// defaults entry if necessary.
def create(paths, module) {
    newModfile(paths.repodata) { writeModule ->
        def wroteMod = false
        for (mod in readModfile(paths.modules)) {
            if (mod.document == 'modulemd-defaults'
                && mod.data.module == module.data.name
                && !(module.data.stream in mod.data.profiles)) {
                mod.data.profiles[module.data.stream] = []
                writeModule(mod)
            } else if (mod.document == 'modulemd'
                       && mod.data.name == module.data.name
                       && mod.data.stream == module.data.stream) {
                if (!wroteMod) {
                    wroteMod = true
                    writeModule(module)
                }
            } else if (mod.document == 'modulemd'
                       && mod.data.name == module.data.name) {
                if (!wroteMod) {
                    wroteMod = true
                    writeModule(module)
                }
                writeModule(mod)
            } else {
                writeModule(mod)
            }
        }
        if (!wroteMod) {
            writeModule(module)
        }
    }
    reindex(paths.root)
}

// Update a modules.yaml file, deleting a module from it. Remove the module from
// the defaults entry if necessary.
def delete(paths, name, version) {
    newModfile(paths.repodata) { writeModule ->
        for (mod in readModfile(paths.modules)) {
            if (mod.document == 'modulemd-defaults'
                && mod.data.module == name
                && version in mod.data.profiles) {
                mod.data.profiles.remove(version)
                if (mod.data?.stream == version) {
                    mod.data.remove('stream')
                }
                writeModule(mod)
            } else if (!(mod.document == 'modulemd'
                         && mod.data.name == name
                         && mod.data.stream == version)) {
                writeModule(mod)
            }
        }
    }
    reindex(paths.root)
}

// Search a modules.yaml file for a module, and return it. Return null if the
// module is not found.
def find(modules, name, version) {
    for (mod in readModfile(modules)) {
        if (mod.document == 'modulemd'
            && mod.data.name == name
            && mod.data.stream == version) {
            return mod
        }
    }
}

// Extract any arguments from the params list that might be of use. Some of
// these have string values, which will be null if not provided. Others are
// optional boolean flags, which will be false if not provided.
def extractParams(params) {
    def res = [:]
    res.source = params?.getAt("source")?.getAt(0) ?: null
    res.target = params?.getAt("target")?.getAt(0) ?: null
    res.name = params?.getAt("name")?.getAt(0) ?: null
    res.version = params?.getAt("version")?.getAt(0) ?: null
    res.downloadMissing = new Boolean(params?.getAt("downloadMissing")?.getAt(0))
    res.cleanArtifacts = new Boolean(params?.getAt("cleanArtifacts")?.getAt(0))
    res.force = new Boolean(params?.getAt("force")?.getAt(0))
    res.move = new Boolean(params?.getAt("move")?.getAt(0))
    return res
}

// Enforce mandatory arguments. These are name/value pairs, and all values must
// be non-null.
def enforceParams(List... params) {
    def missing = []
    for (param in params) {
        if (param[1] == null) {
            missing << param[0]
        }
    }
    if (missing) {
        throw new CancelException("Missing mandatory parameters: ${missing.join(", ")}", 400)
    }
}

// Enforce that the user has read permissions on a given path.
def enforceReadPermissions(path, cache) {
    if (cache) {
        cacheArtifact(path)
    }
    if (!repositories.exists(path)) {
        throw new CancelException("Path $path does not exist", 404)
    }
    if (!security.canRead(path)) {
        throw new CancelException("Insufficient permissions on path $path", 403)
    }
}

// Enforce that the user has write permissions on a given path.
def enforceWritePermissions(path) {
    if (!security.canDeploy(path) || !security.canDelete(path)) {
        throw new CancelException("Insufficient permissions on path $path", 403)
    }
}

// Enforce that the user has delete permissions on a given path.
def enforceDeletePermissions(path) {
    if (!security.canDelete(path)) {
        throw new CancelException("Insufficient permissions on path $path", 403)
    }
}

// Enforce that the user has cache permissions on a given path.
def enforceCachePermissions(path) {
    if (!security.canDeploy(path)) {
        throw new CancelException("Insufficient permissions on path $path", 403)
    }
}

// Given a new module, verify that its listed RPMs exist.
def verifyNewModule(paths, module, force) {
    if (module == null) {
        throw new CancelException("Valid body not provided", 400)
    }
    if (!force) {
        discoverRPMs(paths, module.data.artifacts.rpms).each { k, v ->
            if (v == null) {
                throw new CancelException("Adding a module requires that all prerequisite files be present in the repository.", 400)
            }
            def rpmpath = mkRepoPath(paths.root, v)
            cacheArtifact(rpmpath)
            if (!repositories.exists(rpmpath)) {
                throw new CancelException("Adding a module requires that all prerequisite files be present in the repository.", 400)
            }
        }
    }
}

// Given an existing module, verify that its listed RPMs exist.
def verify(paths, module) {
    def rpms = []
    def missingrpms = []
    discoverRPMs(paths, module.data.artifacts.rpms).each { k, v ->
        if (v == null) {
            rpms << k
            missingrpms << k
            return
        }
        def rpmpath = mkRepoPath(paths.root, v)
        cacheArtifact(rpmpath)
        rpms << k
        if (!repositories.exists(rpmpath)) {
            missingrpms << k
        }
    }
    def status = missingrpms ? 'fail' : 'pass'
    def js = ['status': status, 'artifacts': rpms, 'missing': missingrpms]
    return new JsonBuilder(js).toString()
}

// Given a module to copy, verify that its listed RPMs exist and copy them to
// the new module's location.
def copyRPMs(srcpaths, paths, module, downloadMissing) {
    discoverRPMs(srcpaths, module.data.artifacts.rpms).each { k, v ->
        if (v == null) {
            throw new CancelException("Promoting a module requires that all prerequisite files be present in the repository.", 400)
        }
        def srcrpmpath = mkRepoPath(srcpaths.root, v)
        def dstrpmpath = mkRepoPath(paths.root, v)
        enforceReadPermissions(srcrpmpath, downloadMissing)
        enforceWritePermissions(dstrpmpath)
        repositories.copy(srcrpmpath, dstrpmpath)
    }
}

// Using the metadata listing, convert a list of rpm name:version pairs to a
// list of subpaths.
def discoverRPMs(paths, rpms) {
    cacheArtifact(paths.primary)
    def is = repositories.getContent(paths.primary).inputStream
    def xml = new XmlParser().parse(new GZIPInputStream(is))
    def rpmmap = [:]
    xml.each {
        if (it.@type?.getAt(0) == 'rpm' || it.@type == 'rpm') {
            def name = it.name.text()
            def arch = it.arch.text()
            def epoch = it.version.@epoch?.getAt(0)
            def ver = it.version.@ver?.getAt(0)
            def rel = it.version.@rel?.getAt(0)
            def rpm = "${name}-${epoch}:${ver}-${rel}.${arch}"
            for (r in rpms) {
                if (rpm == r) {
                    rpmmap[r] = it.location.@href?.getAt(0)
                    break
                }
            }
        }
    }
    for (r in rpms) {
        if (!(r in rpmmap || r.contains('debug') || r.endsWith('.src'))) {
            rpmmap[r] = null
        } else if (!(r in rpmmap)) {
            log.warn("Ignoring missing source or debug package $r listed in module")
        }
    }
    return rpmmap
}

// Given a path to a metadata listing, read the repomd file and extract paths to
// each of tbe metadata files, so they can be read.
def getPaths(path) {
    def paths = [:]
    paths.root = RepoPathFactory.create(path)
    paths.repodata = mkRepoPath(paths.root, "repodata")
    def repomd = mkRepoPath(paths.repodata, "repomd.xml")
    if (!repositories.exists(repomd) && repomd.repoKey in repositories.localRepositories) {
        reindex(paths.root)
    }
    enforceReadPermissions(repomd, true)
    def xml = new XmlParser().parse(repositories.getContent(repomd).inputStream)
    xml.each {
        if (it.name() == 'data' || it.name()?.getLocalPart() == 'data') {
            def p = it.location?.@href?.getAt(0)
            if (p) {
                paths[it.@type] = mkRepoPath(paths.root, p)
            }
        }
    }
    return paths
}

// Load a new module from an InputStream.
def readModule(instream) {
    try {
        return newYaml().load(instream)
    } catch (ParserException ex) {
        throw new CancelException("Invalid body provided", 400)
    }
}

// Load a modules.yaml.gz file and parse it into a list of module entries.
def readModfile(path) {
    if (path == null) {
        return []
    }
    def stream = new GZIPInputStream(repositories.getContent(path).inputStream)
    return newYaml().loadAll(stream)
}

// Generate a random hex string for use as a new modules file name. This must be
// unique, and will be changed to the file's sha1 checksum during indexing.
def randStr() {
    def pool = ['0'..'9','a'..'f'].flatten()
    def rand = new Random(System.currentTimeMillis())
    return (0..40).collect({ pool[rand.nextInt(pool.size())] }).join()
}

// Create a new modules.yaml file, usually as a replacement for an old one.
// Accepts a path to the repodata folder, and a callback that writes modules to
// the file. The callback is passed a function that can be called to write each
// module.
def newModfile(path, cb) {
    def err = null
    def modname = "${randStr()}-modules.yaml"
    def modpath = mkRepoPath(path, modname)
    def is = new PipedInputStream()
    def os = new PipedOutputStream(is)
    def writer = new BufferedWriter(new OutputStreamWriter(os))
    Thread.start {
        try {
            def yaml = newYaml()
            cb { module ->
                writer << "---\n"
                yaml.dump(module, writer)
                writer << "...\n"
            }
        } catch (Exception ex) {
            err = ex
        } finally {
            writer.close()
        }
    }
    repositories.deploy(modpath, is)
    if (err != null) {
        repositories.delete(modpath)
        throw err
    }
}

// Append two path strings, adding a slash character if none exists.
def appendPath(path1, path2) {
    if (path1 && path1[-1] != '/') {
        path1 += '/'
    }
    return path1 + path2
}

// Create a new RepoPath object by appending a path string to the end of a given
// RepoPath object.
def mkRepoPath(rpath, subpath) {
    return RepoPathFactory.create(rpath.repoKey, appendPath(rpath.path, subpath))
}

// Reindex metadata at a given path to introduce a new or modified modules file.
def reindex(path) {
    def yumBean = ctx.beanForType(InternalYumAddon.class)
    try {
        yumBean.calculateYumMetadata(null, path)
    } catch (MissingMethodException ex) {
        yumBean.calculateYumMetadata(path)
    }
}

// Ensure the artifact at the given path is available. This means if the
// artifact is in a remote repository, ensure it is in the cache. If the
// artifact is in a virtual repository, calculate the real (local or remote)
// location first.
def cacheArtifact(path) {
    def reqctx = new NullRequestContext(path)
    path = ctx.repositoryService.repositoryByKey(path.repoKey).getInfo(reqctx).repoPath
    if (path.repoKey in repositories.remoteRepositories) {
        enforceCachePermissions(path)
        def repoService = ctx.beanForType(InternalRepositoryService)
        def repo = repoService.remoteRepositoryByKey(path.repoKey)
        repo.getResourceStreamHandle(reqctx, repo.getInfo(reqctx))?.close()
    }
}
