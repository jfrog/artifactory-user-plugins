package data

import definitions.RepositoryLocalClass

/**
 * Created by stanleyf on 03/08/2017.
 */
class RepositoryConan {
    def myconanDev = new RepositoryLocalClass (
            key: 'conan-dev-local',
            packageType: 'conan',
            description: "conan-development-path",
            repoLayoutRef: 'conan-default',
            handleReleases: 'false',
            handleSnapshots: 'true')

    def myconanRelease = new RepositoryLocalClass (
            key: 'conan-release-local',
            packageType: 'conan',
            description: "development-to-release-path",
            repoLayoutRef: 'conan-default',
            handleReleases: 'true',
            handleSnapshots: 'false')

    def myconanReposList = [myconanDev, myconanRelease]
    def myconanDevOpsPermissionList = [myconanDev['key'], myconanRelease['key']]
    def myconanDevPermissionList = [myconanDev['key']]
    def myconanQAPermissionList = [myconanDev['key'], myconanRelease['key']]


}
