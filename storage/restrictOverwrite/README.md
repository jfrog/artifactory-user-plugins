Artifactory Restrict Overwrite User Plugin
==========================================

This plugin disallows overwrites of artifacts in any of a configured set of
repositories. This can be used to prevent accidental modifications to artifacts
that should not be changed. With this plugin loaded, any attempt to deploy,
move, or copy an artifact to a path where another artifact already resides will
result in an error.

If you really need to update a file, you can delete the old file before
deploying the new one.

Configuration
-------------

The configuration file is `restrictOverwrite.json`. This file simply contains a
JSON list of repository names. Only the repositories in this list will be
affected by this plugin.

For example, if you wanted to prevent overwrites in the repositories
`master-repo-1` and `master-repo-2`, your `restrictOverwrite.json` would look
like this:

``` json
[
    "master-repo-1",
    "master-repo-2"
]
```

Notes
-----

When using this plugin with Docker repositories, please note that Artifactory
treats Docker images as folders, which contain layers and a manifest. Therefore,
copying a Docker image over another will merge the layers of the first image
into the second one, rather than overwrite the image as you might expect.
Because of this, this plugin allows such a copy to take place, so layers in the
source image will be copied into the destination image successfully. However,
the plugin will not allow existing layers to be overwritten, and will not allow
the manifest file to be overwritten, so the image should still work as expected.
