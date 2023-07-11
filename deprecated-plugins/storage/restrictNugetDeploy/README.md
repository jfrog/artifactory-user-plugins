Artifactory Restrict NuGet Deploy User Plugin
=============================================

This plugin restricts NuGet packages from being deployed to a local repository,
if packages with the same name already exist in other NuGet repositories (say,
nuget.org). This ensures that NuGet doesn't accidentally fetch the wrong
packages from a virtual repository.

Each time a NuGet package is deployed, this plugin ensures that the package does
not already exist in another repository. If the package does already exist, the
deploy is cancelled, and a 409 error is thrown. The repositories to check can be
specified in an accompanying properties file.

Properties
----------

- `filteredRepos`: A list of repositories that trigger a check on deploy.
- `checkedRepos`: A list of repositories to check.
