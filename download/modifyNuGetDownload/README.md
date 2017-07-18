# Artifactory modifyNuGetDownlaod User Plugin

## Features
This plugins rewrite downlaod request for nupkg packages in nuget-gallery repo 2 levels down from it's original request path

For example, if you are requesting a package from

`nuget-gallery/example.1.0.0.nupkg`

the download request will be redirect to

`nuget-galllery/example/example/example.1.0.0.nupkg`