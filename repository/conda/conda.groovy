/**
 * Copyright (c) 2017 James Sexton
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.artifactory.fs.FileInfo
import org.artifactory.fs.ItemInfo
import org.artifactory.repo.RepoPath
import org.artifactory.repo.RepoPathFactory
import org.artifactory.request.Request

import com.google.common.collect.Iterables;

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.transform.Canonical
import groovy.transform.Field

/**
 * An Artifactory plugin to enable support for both remote and local conda repositories.
 * To treat a local repository as a conda channel, add a property with the name "conda".
 *
 * @author James Sexton
 */

/** The delimiter used by RepoPaths in Artifactory. */
@Field final char DELIMITER = '/'

/**
 * Support for remote conda repositories.
 */
download {
	/**
	 * Artifactory caches remote artifacts after initial download. By default, Artifactory
	 * doesn't recognize repodata.json as metadata that must be expired, rather than cached.
	 */
	beforeDownloadRequest { Request request, RepoPath repoPath ->
		if (repositories.getRemoteRepositories().contains(repoPath.getRepoKey())) {
			if (repoPath.getName().equalsIgnoreCase("repodata.json") ||
				repoPath.getName().equalsIgnoreCase("repodata.json.bz2")) {
				log.warn("Expiring repodata at " + repoPath)
				expired = true
			}
		}
	}
}

/**
 * Support for local repositories as conda channels.
 */
jobs {
	/**
	 * Every 10 seconds, index all local repositories that have the "conda" property.
	 */
	indexPackages(delay: 0, interval: 10000) {
		log.info("Executing indexPackages")
		long start = System.currentTimeMillis()
		for (RepoPath repo : getCondaRepos()) {
			indexPackagesRecursive(repo)
		}
		long end = System.currentTimeMillis()
		log.info("Took " + (end-start) + "ms to execute indexPackages")
	}
}

/**
 * Index the given dir, and all subdirs, as a conda channel. This includes:
 *  -Updating repodata.json and repodata.json.bz2 files
 *  -Moving packages into their correct subdir
 *
 * To maintain efficiency, this methods avoids unzipping packages
 * if a matching entry with identical md5 is already in the repodata.
 */
void indexPackagesRecursive(RepoPath dir) {
	log.info("Indexing packages in " + dir)

	// Parse the existing repodata (if any) in this directory.
	RepoPath repodataPath = childFilePath(dir, "repodata.json")
	def repodataJson = repositories.exists(repodataPath)
		? repositories.getContent(repodataPath).withCloseable { new JsonSlurper().parse(it.getInputStream()) }
		: [info: {}, packages: [:]]

	// Look through every child package to make sure corresponding entry exists in repodata.
	// This should be done before recursing into child dirs, as we may move packages into subdir.
	boolean repodataUpdated = false
	Set<String> childPackageNames = []
	for (FileInfo childFile : getChildFiles(dir)) {
		// Keep track of packages we see, to later delete stale entries in repodata.
		String packageName = childFile.getName()
		childPackageNames.add(packageName)

		// If md5 inside repodata exists and matches the package, we can assume that repodata is correct.
		String repoMD5 = repodataJson.packages.get(packageName)?.get("md5")
		String packageMD5 = childFile.getMd5()
		if (repoMD5 == packageMD5) {
			continue
		}

		// Process the package info if this is a valid conda package.
		Map<String, PackageInfo> packageInfo = getPackageInfo(childFile)
		if (packageInfo != null) {
			// If this package is already in the correct subdir, update the repodata.
			// Otherwise, move the package into the correct subdir (and let recursion handle updating repodata).
			String correctSubdir = Iterables.getOnlyElement(packageInfo.values()).subdir
			if (correctSubdir == null || dir.getName() == correctSubdir) { // note: paths are case sensitive
				repodataJson.packages << packageInfo
				repodataUpdated = true
			} else {
				RepoPath newPath = childFilePath(dir, [correctSubdir], packageName)
				safeMove(childFile.getRepoPath(), newPath)
			}
		}
	}

	// Remove any entries in repodata for which we did not see a corresponding package in this dir.
	Iterator<String> packages = repodataJson.packages.keySet().iterator()
	while (packages.hasNext()) {
		String packageName = packages.next()
		if (!childPackageNames.contains(packageName)) {
			packages.remove()
			repodataUpdated = true
		}
	}

	// Write out new repodata if any packages have been added/removed/changed.
	if (repodataUpdated) {
		writeRepoData(dir, repodataJson)
	}

	// Recurse into subdirs. This will also handle any packages that were moved into a subdir previously.
	for (ItemInfo childDir : getChildDirs(dir)) {
		indexPackagesRecursive(childDir.getRepoPath())
	}
}

/**
 * Returns a list of local conda repositories, determined by the existence of the "conda" property.
 */
List<RepoPath> getCondaRepos() {
	return repositories.getLocalRepositories()
		.collect { RepoPathFactory.create(it, "") }
		.findAll { repositories.hasProperty(it, "conda") }
}

/**
 * Returns a map of package name to package info that can be appended to repodata's packages,
 * or null if the given file is not a valid conda package.
 */
Map<String, PackageInfo> getPackageInfo(FileInfo file) {
	if (file.getSize() <= 0 || !file.getName().toLowerCase().endsWith(".tar.bz2")) {
		return null
	}

	// Unzip the package contents, look for index.json.
	InputStream packageStream = repositories.getContent(file.getRepoPath()).getInputStream()
	TarArchiveInputStream tarStream = new TarArchiveInputStream(new BZip2CompressorInputStream(packageStream))
	tarStream.withCloseable {
		while (true) {
			TarArchiveEntry tarEntry = tarStream.getNextTarEntry()
			if (tarEntry == null) {
				log.error("index.json not found in package " + file.getName())
				return null
			} else if (tarEntry.getName().equalsIgnoreCase("info/index.json")) {
				return getPackageInfo(file, tarStream)
			}
			// TODO: Also look for "meta.yaml", parse with snakeyaml and assign properties we care about.
		}
	}
}

/**
 * Returns a map of package name to package info that can be appended to repodata's packages.
 */
Map<String, PackageInfo> getPackageInfo(FileInfo file, InputStream indexStream) {
	Object indexJson = parseJson(indexStream)

	PackageInfo packageInfo = new PackageInfo()
	packageInfo.md5 = file.getMd5()
	packageInfo.size = file.getSize()
	packageInfo.build = indexJson["build"]
	packageInfo.build_number = indexJson["build_number"]
	packageInfo.depends = indexJson["depends"]
	if (packageInfo.depends == null) {
		// Some older conda packages use "requires" instead of "depends".
		packageInfo.depends = indexJson["requires"]
	}
	packageInfo.license = indexJson["license"]
	packageInfo.name = indexJson["name"]
	packageInfo.subdir = indexJson["subdir"]
	packageInfo.version = indexJson["version"]

	return [(file.getName()): packageInfo]
}

/**
 * Returns an object containing the input stream parsed as a JSON string, without closing the stream.
 */
Object parseJson(InputStream inputStream) {
	// Unfortunately, JsonSlurper closes a stream after parsing, as does stream.getBytes().
	// To avoid closing the input stream prematurely, parse ByteStreams.toByteArray() instead.
	return new JsonSlurper().parse(com.google.common.io.ByteStreams.toByteArray(inputStream))
}

/**
 * Writes "repodata.json" and "repodata.json.bz2" in the given dir with the contents of the JSON object.
 */
void writeRepoData(RepoPath dir, Object repodataJson) {
	log.warn("Writing new repodata in " + dir)

	// Write "repodata.json"
	String repodataString = new JsonBuilder(repodataJson).toPrettyString()
	byte[] repodataBytes = repodataString.getBytes()
	RepoPath repodataPath = childFilePath(dir, "repodata.json")
	repositories.deploy(repodataPath, new ByteArrayInputStream(repodataBytes))

	// Write "repodata.json.bz2"
	ByteArrayOutputStream bzOutBytes = new ByteArrayOutputStream()
	BZip2CompressorOutputStream bzOutStream = new BZip2CompressorOutputStream(bzOutBytes)
	bzOutStream.withCloseable { bzOutStream.write(repodataBytes) }
	RepoPath repodataPathBz = childFilePath(dir, "repodata.json.bz2")
	repositories.deploy(repodataPathBz, new ByteArrayInputStream(bzOutBytes.toByteArray()))
}

/**
 * Moves the file at 'source' to 'target', creating parent directories if needed.
 */
void safeMove(RepoPath source, RepoPath target) {
	// The move command will fail if the target's parent directory doesn't exist,
	// but the deploy command has no such limitation.
	if (!repositories.exists(target.getParent())) {
		repositories.deploy(target, new ByteArrayInputStream(new byte[0]))
	}
	log.warn("Moving " + source + " to " + target)
	repositories.move(source, target)
}

/**
 * Returns a RepoPath representing a file in the given dir.
 */
RepoPath childFilePath(RepoPath dir, String fileName) {
	return childFilePath(dir, [], fileName)
}

/**
 * Returns a RepoPath representing a file with the given base dir and subdirs.
 */
RepoPath childFilePath(RepoPath dir, Iterable<String> subdirs, String fileName) {
	StringBuilder path = new StringBuilder(dir.toPath())
	assert dir.isFolder() && path[-1] == DELIMITER
	for (String subdir : subdirs) {
		path << subdir
		path << DELIMITER
	}
	path << fileName
	return RepoPathFactory.create(path.toString())
}

/**
 * Returns all subdirs in the given dir.
 */
List<ItemInfo> getChildDirs(RepoPath dir) {
	assert dir.isFolder()
	return repositories.getChildren(dir)
		.findAll { it.isFolder() }
}

/**
 * Returns all files directly contained in the given dir (not in subdirs).
 */
List<FileInfo> getChildFiles(RepoPath dir) {
	assert dir.isFolder()
	return repositories.getChildren(dir)
		.findAll { !it.isFolder() }
		.collect { repositories.getFileInfo(it.getRepoPath()) }
}

/**
 * The metadata extracted from a conda package that is required in repodata.json.
 */
@Canonical
class PackageInfo {
	String build
	int build_number
	List<String> depends
	String license
	String md5
	String name
	String sig
	int size
	String subdir
	String version
}
