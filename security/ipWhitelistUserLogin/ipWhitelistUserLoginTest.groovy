import org.artifactory.api.repo.storage.RepoStorageSummaryInfo
import org.jfrog.artifactory.client.Artifactory
import org.jfrog.artifactory.client.RepositoryHandle
import org.jfrog.artifactory.client.model.LightweightRepository
import org.jfrog.artifactory.client.model.LocalRepository
import org.jfrog.artifactory.client.model.PackageType
import org.jfrog.artifactory.client.model.Privilege
import org.jfrog.artifactory.client.model.Repository
import org.jfrog.artifactory.client.model.RepositoryType
import org.jfrog.artifactory.client.model.builder.GroupBuilder
import org.jfrog.artifactory.client.model.builder.LocalRepositoryBuilder
import org.jfrog.artifactory.client.model.repository.settings.ConanRepositorySettings
import org.jfrog.artifactory.client.model.repository.settings.RepositorySettings
import org.jfrog.artifactory.client.model.repository.settings.impl.ConanRepositorySettingsImpl
import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl
import spock.lang.Specification

import org.jfrog.artifactory.client.ArtifactoryClientBuilder
import org.jfrog.artifactory.client.model.builder.UserBuilder

class IpWhitelistUserLoginTest extends Specification {

    def grantRepoReadPermissionToGroup (Artifactory artifactory, String permissionName, String groupName, String repoKey) {
        def principal = artifactory.security().builders().principalBuilder()
                .name(groupName)
                .privileges(Privilege.READ)
                .build()
        def principals = artifactory.security().builders().principalsBuilder()
                .groups(principal)
                .build()
        def permission = artifactory.security().builders().permissionTargetBuilder()
                .name(permissionName)
                .repositories(repoKey)
                .principals(principals)
                .build()
        artifactory.security().createOrReplacePermissionTarget(permission)
    }

    def 'ip whitelist user login test'() {
        setup:
        def baseurl1 = 'http://localhost:8082/artifactory'
        def password = "password"
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl1).setUsername('admin').setPassword(password).build()
        def artifactory_bob = ArtifactoryClientBuilder.create().setUrl(baseurl1).setUsername('bob').setPassword(password).build()

        when:

        /*

        1. Create a group (same as in the config, which will be assigned by the plugin)
        2. Create a repository: conan-test
        3. Allow the group to read that repository
        4. Create the user `bob` (without assigning the group)
        5. Then test that user `bob` gets the group assigned by the plugin, by verifying that it can see the repo

         */

        GroupBuilder groupBilder = artifactory.security().builders().groupBuilder()
        def ip_allowed__group = groupBilder
                .name("ip-allowed-bob")
                .adminPrivileges(false)
                .autoJoin(false).build();
        artifactory.security().createOrUpdateGroup(ip_allowed__group)

        LocalRepositoryBuilder localRepositoryBuilder = artifactory.repositories().builders().localRepositoryBuilder()
        localRepositoryBuilder.key("conan-test").repositorySettings(new ConanRepositorySettingsImpl())
        artifactory.repositories().create(0, localRepositoryBuilder.build())
        artifactory.security().builders().permissionTargetBuilder()

        grantRepoReadPermissionToGroup(artifactory, "conan-test-read", "ip-allowed-bob", "conan-test")

        UserBuilder userBuilder = artifactory.security().builders().userBuilder()
        def user1 = userBuilder.name("bob")
        .email("newuser@jfrog.com")
        .admin(false)
        .profileUpdatable(false)
        .password(password)
        .build();
        artifactory.security().createOrUpdate(user1)

        then:

        def repos_list = artifactory_bob.repositories().list()

        boolean repo_found = false

        for (repo in repos_list) {
            if (repo.key == "conan-test") {
                repo_found = true
                break
            }
        }

        repo_found

        cleanup:
        String result3 = artifactory.repository("conan-test").delete()
        String result2 = artifactory.security().deleteGroup("ip-allowed-bob")
        String result4 = artifactory.security().deletePermissionTarget("conan-test-read")
        String result1 = artifactory.security().deleteUser("bob")
    }
}
