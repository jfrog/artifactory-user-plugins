import groovy.transform.Field
import org.artifactory.fs.FileLayoutInfo
import org.artifactory.fs.ItemInfo
import org.artifactory.repo.RepoPath
import org.codehaus.mojo.versions.ordering.VersionComparator
import org.codehaus.mojo.versions.ordering.VersionComparators
@Grapes([
        @Grab(group = 'org.semver', module = 'api', version = '0.9.20'),
        @Grab(group = 'org.semver', module = 'api', classifier = 'sources', version = '0.9.20'),
        @GrabExclude('asm:asm'),
        @GrabExclude('asm:asm-tree'),
        @GrabExclude('asm:asm-commons'),
        @GrabExclude('commons-lang:commons-lang')
])
import org.semver.Comparer
import org.semver.Delta
import org.semver.Version

import java.nio.file.Files
import java.nio.file.Path

@Field final String BINARY_COMPATIBILITY_PROPERTY_NAME = 'approve.binaryCompatible'

storage {
    /**
     * Handle after create events.
     *
     * Closure parameters:
     * item (org.artifactory.fs.ItemInfo) - the original item being created.
     */
    afterCreate { ItemInfo item ->
        FileLayoutInfo currentLayout = repositories.getLayoutInfo(item.repoPath)
        if (currentLayout.organization && currentLayout.ext == 'jar') {
            List<RepoPath> allVersions = searches.artifactsByGavc(currentLayout.getOrganization(),
                    currentLayout.getModule(), null, null).findAll { it.path.endsWith('.jar') }
            if (allVersions.size() > 1) {
                VersionComparator mavenComparator = VersionComparators.getVersionComparator('mercury')
                allVersions.sort(true, mavenComparator)
                if (allVersions.pop().equals(item.repoPath)) {
                    RepoPath previousVersion = allVersions.pop()
                    Path currentTempPath = Files.createTempFile("${currentLayout.module}-current", '.jar')

                    File currentTempFile = currentTempPath.toFile()
                    currentTempFile << repositories.getContent(item.repoPath).inputStream


                    FileLayoutInfo previousLayout = repositories.getLayoutInfo(previousVersion)
                    Path previousTempPath = Files.createTempFile("${previousLayout.module}-previous", '.jar')
                    File previousTempFile = previousTempPath.toFile()
                    previousTempFile << repositories.getContent(previousVersion).inputStream

                    Comparer comparer = new Comparer(previousTempFile, currentTempFile, [] as Set, [] as Set)
                    Delta delta = comparer.diff()

                    //Provide version number for previous and current Jar files.
                    final Version previous = Version.parse(previousLayout.baseRevision)
                    final Version current = Version.parse(currentLayout.baseRevision)

                    //Validates that current version number is valid based on semantic versioning principles.
                    final boolean compatible = delta.validate(previous, current)
                    repositories.setProperty(item.repoPath, BINARY_COMPATIBILITY_PROPERTY_NAME, compatible.toString())

                } else {
                    //TODO you might want to check versus previous or next version?
                    log.warn("Skipping compatibility analysis for $item - newer versions already exist")
                }
            } else {
                repositories.setProperty(item.repoPath, BINARY_COMPATIBILITY_PROPERTY_NAME, true.toString())
            }
        } else {
            log.warn("Skipping compatibility analysis for $item - not jar or not in Maven layout")
        }

    }
}