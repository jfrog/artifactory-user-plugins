import groovy.json.JsonBuilder
import org.artifactory.storage.db.binstore.dao.BinariesDao

executions {
  // Check the integrity of the filestore. This REST endpoint returns a JSON
  // object, containing a list of artifacts that appear in the Artifactory
  // database, but do not have corresponding files on the filesystem, and a list
  // of files that appear in the filesystem, but not the database.
  filestoreIntegrity(httpMethod: 'GET') { params ->
    // find the filestore directory, if one exists
    def bindir = null
    try {
      bindir = new File(ctx.artifactoryHome.haAwareDataDir, 'filestore')
    } catch (MissingPropertyException ex) {
      bindir = new File(ctx.artifactoryHome.dataDir, 'filestore')
    }


    log.debug("Reading from filestore at '$bindir'")
    if (!bindir.list()) {
      def err = "Error reading files from '$bindir': the directory is either"
      err += " empty, inaccessible, or gone. This Artifactory instance may be"
      err += " using a binary provider that is unsupported by this plugin."
      log.error(err)
      message = err
      status = 500
      return
    }
    // retrieve a BinariesDao instance so the database can be accessed
    def binstore = ctx.beanForType(BinariesDao)
    // create the lists of potentially discrepant binaries
    def (missing, extra) = buildInitialList(binstore, bindir)
    // remove any false-positives from the missing list
    missing.retainAll {
      existsInDatabase(binstore, it) && !new File(bindir, "${it[0..1]}/$it").exists()
    }
    // remove any false-positives from the extra list
    extra.retainAll {
      if (!it[1].startsWith(it[0])) return true
      !existsInDatabase(binstore, it[1]) && new File(bindir, "${it[0]}/${it[1]}").exists()
    }
    extra = extra.collect { "${it[0]}/${it[1]}" }
    // prepare a list of repoPaths matching each checksum in the missing list
    def json = []
    for (result in missing) {
      repopaths = searches.artifactsBySha1(result)
      for (repopath in repopaths) {
        json << [repoPath: repopath.toString(), sha1: result]
      }
    }
    json.sort { it.repoPath }
    // respond with a JSON object, containing all results
    def size = json.size() + extra.size()
    log.debug("Integrity check complete, $size discrepancies found.")
    message = new JsonBuilder([missing: json, extra: extra]).toPrettyString()
    status = 200
  }
}

// Build the initial lists of potential discrepancies. This is done by first
// dumping the list of sha1 checksums from the database, then comparing that to
// the list of files in the filesystem. A list of all checksums that appear in
// the database but not in the filesystem is returned, as is another list of all
// files found in the filesystem but not in the database. These lists may
// contain false-positives, if artifacts were deleted, added, or replaced
// between the collection of the database list and the filesystem list.
def buildInitialList(binstore, bindir) {
  def idx = 0, missing = [], extra = []
  // retrieve the list of tracked sha1 sums from the database, and sort them
  def binaries = binstore.findAll()*.sha1.sort()
  def binsize = binaries.size()
  // iterate over each potential subdirectory in the filestore
  for (diridx in 0..255) {
    // attempt to list the files in the subdirectory, and sort them
    def dirname = Integer.toHexString(diridx).padLeft(2, '0')
    def hashes = new File(bindir, dirname).list()?.sort()
    // if the subdirectory doesn't exist, skip it
    if (!hashes) continue
    // iterate over each file in the subdirectory
    for (hash in hashes) {
      def isextra = true
      // if this binary should not be in this directory, skip it
      if (hash.startsWith(dirname)) {
        // compare entries in the database list to the current binary
        for (; idx < binsize && hash >= binaries[idx]; idx += 1) {
          // if an entry in the database list does not have a corresponding
          // binary, add it to the missing list
          if (hash > binaries[idx]) missing << binaries[idx]
          else isextra = false
        }
      }
      // if a binary does not have a corresponding database entry, add it to the
      // extra list
      if (isextra) extra << [dirname, hash]
    }
  }
  // if we're not yet at the end of the database list, add the rest of it to the
  // results before returning
  if (idx < binsize) missing += binaries[idx..-1]
  return [missing, extra]
}

/**
* Check if sha1 exists in database
**/
def existsInDatabase(binstore, sha1) {
    try {
        // Artifactory 5.5.0 and newer
        return binstore.exists(org.artifactory.checksum.ChecksumType.sha1, sha1)
    } catch (MissingMethodException e) {
        // Artifactory 5.4.6 and older
        return binstore.exists(sha1)
    }
}
