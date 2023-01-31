# Artifactory Modify NuGet Download User Plugin

This is an example plugin. It rewrites download requests for NuGet packages, based on the package name and version.

For example, if you request a package from:

`nuget-gallery/example.1.0.0.nupkg`

Artifactory will instead give you the file at:

`nuget-gallery/example/example/example.1.0.0.nupkg`

By default, this plugin only affects downloads from the `nuget-gallery` repository.
