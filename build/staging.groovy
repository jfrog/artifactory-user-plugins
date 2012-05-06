/*
* Artifactory is a binaries repository manager.
* Copyright (C) 2012 JFrog Ltd.
*
* Artifactory is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* Artifactory is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
*/

import groovy.transform.Field
import org.artifactory.build.BuildRun
import org.artifactory.build.staging.ModuleVersion
import org.artifactory.build.staging.VcsConfig
import org.artifactory.build.promotion.PromotionConfig

import static org.apache.commons.lang.StringUtils.removeEnd

staging {

    /**
     * A build staging strategy definition.
     *
     * Closure delegate:
     * org.artifactory.build.staging.BuildStagingStrategy - The strategy that's to be returned.
     *
     * Plugin info annotation parameters:
     * version (java.lang.String) - Closure version. Optional.
     * description (java.lang.String - Closure description. Optional.
     * params (java.util.Map<java.lang.String, java.lang.String>) - Closure parameters. Optional.
     * users (java.util.Set<java.lang.String>) - Users permitted to query this plugin for information or invoke it.
     * groups (java.util.Set<java.lang.String>) - Groups permitted to query this plugin for information or invoke it.
     *
     * Closure parameters:
     * buildName (java.lang.String) - The build name specified in the REST request.
     * params (java.util.Map<java.lang.String, java.util.List<java.lang.String>>) - The parameters specified in the REST request.
     */
    gradle(users: "jenkins", params: [patch: 'false']) {String buildName, Map<String, List<String>> params ->

        @Field String releaseVersion = '1.0.0'

        //Finds the latest build
        def latestBuildRun = {buildRuns ->
            buildRuns.max {buildRun -> buildRun.startedDate }
        }

        //Finds the latest released build, if not found, return the latest build no matter what status
        def latestReleaseOrLatestBuild = {List<BuildRun> buildRuns ->
            def latestReleasedBuild = null;
            def allReleasedBuilds = buildRuns.findAll {buildRun -> (buildRun.releaseStatus == 'released') }

            if (allReleasedBuilds) {
                latestReleasedBuild = latestBuildRun(allReleasedBuilds)
            }

            if (!latestReleasedBuild) {
                latestReleasedBuild = latestBuildRun(buildRuns)
            }

            latestReleasedBuild
        }

        //Get the global version of the latest build run
        String latestReleaseVersion = getLatestReleaseVersion(builds.getBuilds(buildName, null), latestReleaseOrLatestBuild)

        //If the version is found, increment
        if (latestReleaseVersion) {
            releaseVersion = transformReleaseVersion(latestReleaseVersion, params['patch'][0] as Boolean)
        }

        def nextDevVersion = transformReleaseVersion(releaseVersion, false) + '-SNAPSHOT'

        moduleVersionsMap = [currentVersion: new ModuleVersion('currentVersion', releaseVersion, nextDevVersion)]

        vcsConfig = new VcsConfig()
        vcsConfig.useReleaseBranch = false
        vcsConfig.createTag = true
        vcsConfig.tagUrlOrName = "gradle-multi-example-${releaseVersion}"
        vcsConfig.tagComment = "[gradle-multi-example] Release version ${releaseVersion}"
        vcsConfig.nextDevelopmentVersionComment = "[gradle-multi-example] Next development version"

        promotionConfig = new PromotionConfig("gradle-staging-local")
        promotionConfig.comment = "Staging Artifactory ${releaseVersion}"
    }

    gradleAgile(users: "jenkins", params: [patch: 'false']) { String buildName, Map<String, List<String>> params ->

        //Finds the latest build
        def latestBuildRun = {buildRuns ->
            buildRuns.max {buildRun -> buildRun.startedDate }
        }

        //Finds the latest released build, if not found, return the latest build no matter what status
        def latestReleaseOrLatestBuild = {buildRuns ->
            def latestReleasedBuild = null;
            def allReleasedBuilds = buildRuns.findAll {buildRun -> buildRun.releaseStatus == "release" }

            if (allReleasedBuilds) {
                latestReleasedBuild = latestBuildRun(allReleasedBuilds)
            }

            if (!latestReleasedBuild) {
                latestReleasedBuild = latestBuildRun(buildRuns)
            }

            latestReleasedBuild
        }

        //Get the global version of the latest build run
        def latestReleaseVersion = getLatestReleaseVersion(builds.getBuilds(buildName, null), latestReleaseOrLatestBuild)

        //If the version is found, increment
        if (latestReleaseVersion) {
            releaseVersion = transformReleaseVersion(latestReleaseVersion, params['patch'][0] as Boolean).plus('-SNAPSHOT')
        }

        moduleVersionsMap = [currentVersion: new ModuleVersion('currentVersion', releaseVersion, releaseVersion)]

        vcsConfig = new VcsConfig()
        vcsConfig.useReleaseBranch = false
        vcsConfig.createTag = false
        vcsConfig.tagUrlOrName = "gradle-multi-example-${releaseVersion}"
        vcsConfig.tagComment = "[gradle-multi-example] RC version ${releaseVersion}"
        vcsConfig.nextDevelopmentVersionComment = "[gradle-multi-example] Next development version"

        promotionConfig = new PromotionConfig("gradle-snapshot-local")
        promotionConfig.comment = "Yet another snapshot of gradle-multi-example ${releaseVersion}"
    }

    maven(users: "jenkins", params: [key1: 'value1', key2: 'value2']) { buildName, params ->
        moduleVersionsMap = [myModule: new ModuleVersion('myModule', releaseVersion, "1.1.x-SNAPSHOT")]

        vcsConfig = new VcsConfig()
        vcsConfig.useReleaseBranch = false
        vcsConfig.releaseBranchName = null
        vcsConfig.createTag = true
        vcsConfig.tagUrlOrName = "file:///Users/noam/Desktop/test/svnrepo/multi-modules/tags/artifactory-${releaseVersion}"
        vcsConfig.tagComment = "[artifactory-release] Release version ${releaseVersion}"
        vcsConfig.nextDevelopmentVersionComment = "[artifactory-release] Next development version"

        promotionConfig = new PromotionConfig("libs-snapshot-local")
        promotionConfig.comment = "Staging Artifactory ${releaseVersion}"
    }
}


/**
 * Returns the global version of the given build
 *
 * @param allBuilds Builds to search within
 * @param latestBuildMethod Latest build criterion closure
 * @return Version string if found, null if not
 */
private String getLatestReleaseVersion(allBuilds, latestBuildMethod) {
    def moduleIdPattern = ~/(?:.+)\:(?:.+)\:(.+)/
    if (allBuilds) {
        def latestReleaseBuild = latestBuildMethod(allBuilds)
        if (latestReleaseBuild) {
            def detailedLatestBuildRun = builds.getDetailedBuild latestReleaseBuild
            def moduleVersionMatcher = moduleIdPattern.matcher detailedLatestBuildRun.modules.first().id
            if (moduleVersionMatcher.matches()) {
                moduleVersionMatcher.group 1
            }
        }
    }
    null
}

/**
 * Expects a version string like \{d}.\{d}.\{d} (with optional char at the end).
 * If a char exists, it will be incremented, otherwise the last number will be
 * @param releaseVersion
 * @return
 */
private String transformReleaseVersion(String releaseVersion, boolean patch) {
    String ret = releaseVersion
    def versionPattern = ~/(?:\d+)\.(?:\d+)\.(\d+)(\D{1})?/
    def releaseVersionMatcher = versionPattern.matcher(releaseVersion)
    if (releaseVersionMatcher.matches()) {
        def optionalChar = releaseVersionMatcher.group(2)
        if (optionalChar) {
            if (patch) {
                ret = releaseVersion.minus(optionalChar).plus(optionalChar.toCharacter().next())
            } else {
                ret = transformReleaseVersion(releaseVersion.minus(optionalChar), false)
            }
        } else {
            if (patch) {
                ret = releaseVersion.plus('a')
            } else {
                def lastNumberChar = releaseVersionMatcher.group(1);
                ret = removeEnd(releaseVersion, lastNumberChar).plus(lastNumberChar.toInteger().next())
            }
        }
    }
    ret
}