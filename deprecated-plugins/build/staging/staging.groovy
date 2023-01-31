/*
 * Copyright (C) 2014 JFrog Ltd.
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

import groovy.transform.Field
import org.artifactory.build.BuildRun
import org.artifactory.build.Module
import org.artifactory.build.promotion.PromotionConfig
import org.artifactory.build.staging.ModuleVersion
import org.artifactory.build.staging.VcsConfig

import static org.apache.commons.lang.StringUtils.removeEnd

/**
 * Set of staging strategy definitions to be used by the build server during
 * staging process. The strategy provides the build server with the following
 * information:
 *  1. How the artifacts in the staged build should be versioned
 *  2. How the artifacts in the next integration build should be versioned
 *  3. Should the build server create a release branch/tag/stream in VCS and how
 *     it should be called
 *  4. To which repository in Artifactory the built artifacts should be
 *     submitted
 *
 * This user plugin contains two strategies, 'gradle' and 'maven', see their
 * comments for more details.
 *
 * This user plugin is called by the build server using REST call.
 */

staging {
    /**
     * Staging strategy for Gradle builds.
     * Depending on the 'patch' parameter, increments the latest version or adds
     * 'a' to it.
     * The latest version is the last released build, if not found - the latest
     * build, if not found, defaults to 1.0.0
     * Generates new integration version by attaching '-RELEASE' to latest
     * release.
     * Prefixes new tag name with the  build name ('gradle-multi-example-')
     * Stages the build in 'gradle-staging-local' repo
     */
    gradle(users: "jenkins", params: [patch: 'false']) { String buildName, Map<String, List<String>> params ->
        @Field String releaseVersion = '1.0.0'

        // Get the global version of the latest build run
        BuildRun latestReleaseOrBuild = latestReleaseOrLatestBuild(builds.getBuilds(buildName, null, null))
        String latestReleaseVersion = getLatestReleaseVersion(latestReleaseOrBuild)

        // If the version is found, increment
        if (latestReleaseVersion) {
            releaseVersion = transformReleaseVersion(latestReleaseVersion, params['patch'][0] as Boolean)
        }

        def nextDevVersion = transformReleaseVersion(releaseVersion, false) + '-SNAPSHOT'

        moduleVersionsMap = [modulex: new ModuleVersion('currentVersion', releaseVersion, nextDevVersion)]

        vcsConfig = new VcsConfig()
        vcsConfig.useReleaseBranch = false
        vcsConfig.createTag = true
        vcsConfig.tagUrlOrName = "gradle-multi-example-${releaseVersion}"
        vcsConfig.tagComment = "[gradle-multi-example] Release version ${releaseVersion}"
        vcsConfig.nextDevelopmentVersionComment = "[gradle-multi-example] Next development version"

        promotionConfig = new PromotionConfig("gradle-staging-local")
        promotionConfig.comment = "Staging Artifactory ${releaseVersion}"
    }

    /**
     * Staging strategy for Maven builds.
     * Versions are hard-coded in the plugin (1.0.0 for release and
     * 1.1.x-SNAPSHOT for next integration)
     * Adds new tag to the URL 'multi-modules/tags/artifactory-'
     * Stages the build in 'libs-snapshot-local' repo
     */
    detailedMaven(users: "jenkins") { buildName, params ->
        // Get the global version of the latest build run
        BuildRun latestReleaseOrBuild = latestReleaseOrLatestBuild(builds.getBuilds(buildName, null, null))
        def detailedLatestBuildRun = builds.getDetailedBuild latestReleaseOrBuild
        def moduleIdPattern = ~/(?:.+)\:(?:.+)\:(.+)/
        moduleVersionsMap = [:]
        int index = 0
        detailedLatestBuildRun.modules.each { Module module ->
            def moduleVersionMatcher = moduleIdPattern.matcher module.id
            if (moduleVersionMatcher.matches()) {
                def latestVersion = moduleVersionMatcher.group(1)
                def moduleKey = module.id.substring(0, module.id.length() - latestVersion.length() - 1)
                releaseVersion = transformReleaseVersion(latestVersion, false)
                moduleVersionsMap[moduleKey] = new ModuleVersion(moduleKey, "$releaseVersion-$index", "$releaseVersion-SNAPSHOT")
                index++
            }
        }

        vcsConfig = new VcsConfig()
        vcsConfig.useReleaseBranch = false
        vcsConfig.releaseBranchName = null
        vcsConfig.createTag = true
        vcsConfig.tagUrlOrName = "multi-modules/tags/artifactory-${releaseVersion}"
        vcsConfig.tagComment = "[artifactory-release] Release version ${releaseVersion}"
        vcsConfig.nextDevelopmentVersionComment = "[artifactory-release] Next development version"

        promotionConfig = new PromotionConfig("libs-snapshot-local")
        promotionConfig.comment = "Staging Artifactory ${releaseVersion}"
    }

    simpleMaven(users: "jenkins") { buildName, params ->
        // Get the global version of the latest build run

        BuildRun latestReleaseOrBuild = latestReleaseOrLatestBuildSimple(builds.getBuilds(buildName, null, null))
        def detailedLatestBuildRun = builds.getDetailedBuild latestReleaseOrBuild
        def moduleIdPattern = ~/(?:.+)\:(?:.+)\:(.+)/

        Module module = detailedLatestBuildRun.modules[0]
        def moduleVersionMatcher = moduleIdPattern.matcher module.id
        if (moduleVersionMatcher.matches()) {
            def latestVersion = moduleVersionMatcher.group(1)
            def moduleKey = module.id.substring(0, module.id.length() - latestVersion.length() - 1)
            releaseVersion = transformReleaseVersion(latestVersion, false)
            defaultModuleVersion = new ModuleVersion(moduleKey, "$releaseVersion", "$releaseVersion-SNAPSHOT")
        }

        vcsConfig = new VcsConfig()
        vcsConfig.useReleaseBranch = false
        vcsConfig.releaseBranchName = null
        vcsConfig.createTag = true
        vcsConfig.tagUrlOrName = "rel-${releaseVersion}"
        vcsConfig.tagComment = "[artifactory-release] Release version ${releaseVersion}"
        vcsConfig.nextDevelopmentVersionComment = "[artifactory-release] Next development version"

        promotionConfig = new PromotionConfig("staging-local")
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
private String getLatestReleaseVersion(latestReleaseBuild) {
    def moduleIdPattern = ~/(?:.+)\:(?:.+)\:(.+)/
    if (latestReleaseBuild) {
        def detailedLatestBuildRun = builds.getDetailedBuild latestReleaseBuild
        def moduleVersionMatcher = moduleIdPattern.matcher detailedLatestBuildRun.modules.first().id
        if (moduleVersionMatcher.matches()) {
            return moduleVersionMatcher.group(1)
        }
    }
    null
}

/**
 * Finds the latest released build, if not found, return the latest build no
 * matter what status
 *
 * @param buildRuns
 * @return
 */
private BuildRun latestReleaseOrLatestBuildSimple(List<BuildRun> buildRuns) {
    BuildRun[] allReleasedBuilds = buildRuns.findAll()
    // {buildRun -> (buildRun.releaseStatus == 'Released') }
    if (allReleasedBuilds) {
        buildRuns = allReleasedBuilds
    }
    buildRuns.max { buildRun -> buildRun.startedDate }
}

// Finds the latest released build, if not found, return the latest build no matter what status
private BuildRun latestReleaseOrLatestBuild(List<BuildRun> buildRuns) {
    BuildRun[] allReleasedBuilds = buildRuns.findAll { buildRun -> (buildRun.releaseStatus == 'Released') }
    if (allReleasedBuilds) {
        buildRuns = allReleasedBuilds
    }
    buildRuns.max { buildRun -> buildRun.startedDate }
}

/**
 * Expects a version string like \{d}.\{d}.\{d} (with optional char at the end).
 * If a char exists, it will be incremented, otherwise the last number will be
 *
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
                def lastNumberChar = releaseVersionMatcher.group(1)
                ret = removeEnd(releaseVersion, lastNumberChar).plus(lastNumberChar.toInteger().next())
            }
        }
    }
    ret
}
