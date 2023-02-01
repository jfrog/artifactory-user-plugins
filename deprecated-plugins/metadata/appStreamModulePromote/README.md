Artifactory AppStream Module Promote User Plugin
================================================

Provides some basic operations on Red Hat AppStream modules in local RPM
repositories. Currently, modules are only fully supported in remote
repositories, and atomic CRUD in local repositories is not yet available
(although if you have a built modules file, the local repository supports it).
This plugin provides some of this missing support, in the form of the following
execution endpoints:

createModule
------------

Create/add a new module. The request body is the YAML definition of the module
to add (the specification for such a definition can be found [here][]). The RPM
files specified in this YAML should already be available and indexed within the
repository. The following parameters are available:

[here]: https://github.com/fedora-modularity/libmodulemd/blob/master/yaml_specs/modulemd_stream_v2.yaml

- `target`: The path to the local RPM repository to which the module should be
  added.
- `force`: [optional] `true` if missing RPM files should be ignored, and the
  module should be created even if not all of its packages are available.

For example:

```
$ curl -u admin:password -X POST 'http://localhost:8081/artifactory/api/plugins/execute/createModule?params=target=rpm-local' -T module.yaml

## module.yaml
document: modulemd
version: 2
data:
  name: foo
  stream: 1.0
  version: 8010020191119214651
  context: eb48df33
  arch: x86_64
  summary: test module
  description: >-
    test module
  license:
    module:
    - MIT
    content:
    - GPLv3+
  artifacts:
    rpms:
    - foo-0:1.0-0.noarch
```

Note that even if `force` is not provided or is `false`, missing source or debug
packages will not cause the create to fail (they will instead be ignored, and
print a warn log). This is because source and debug packages are generally
located in a different repository, and are not required in order to install the
module normally.

deleteModule
------------

Delete an existing module. The following parameters are available:

- `target`: The path to the local RPM repository from which to delete the module.
- `name`: The name of the module to delete.
- `version`: The version (stream) of the module to delete.

For example:

```
$ curl -u admin:password -X POST 'http://localhost:8081/artifactory/api/plugins/execute/deleteModule?params=target=rpm-local;name=foo;version=1.0'
```

viewModule
----------

View the YAML definition of an existing module. The following parameters are
available:

- `target`: The path to the RPM repository where the module can be found.
- `name`: The name of the module to view.
- `version`: The version (stream) of the module to view.

For example:

```
$ curl -u admin:password -X GET 'http://localhost:8081/artifactory/api/plugins/execute/viewModule?params=target=rpm-local;name=foo;version=1.0'
```

verifyModule
------------

Verify that an existing module's packages are all available in the repository.
The following parameters are available:

- `target`: The path to the RPM repository where the module can be found.
- `name`: The name of the module to verify.
- `version`: The version (stream) of the module to verify.

For example:

```
$ curl -u admin:password -X GET 'http://localhost:8081/artifactory/api/plugins/execute/verifyModule?params=target=rpm-local;name=foo;version=1.0'
{
  "status": "fail",
  "artifacts": ["foo-0:1.0-0.noarch", "bar-0:1.0-0.noarch"],
  "missing": ["bar-0:1.0-0.noarch"]
}
```

Note that missing source or debug packages will not cause the verify to fail
(they will instead be ignored, and print a warn log). This is because source and
debug packages are generally located in a different repository, and are not
required in order to install the module normally.

promoteModule
-------------

Promote/copy an existing module from one repository to another. The following
parameters are available:

- `source`: The path to the RPM repository where the module can be found.
- `target`: The path to the local RPM repository to which the module should be
  promoted.
- `name`: The name of the module to promote.
- `version`: The version (stream) of the module to promote.
- `move`: [optional] `true` if the module should be moved, instead of copied.
- `downloadMissing`: [optional] `true` if `source` is a remote repository, and
  the module's RPM packages should be cached before being copied. If this is
  not provided or `false`, the promotion will fail if any of the required
  packages are not already cached.

For example:

```
$ curl -u admin:password -X POST 'http://localhost:8081/artifactory/api/plugins/execute/promoteModule?params=source=rpm-remote/8/AppStream/x86_64/os;target=rpm-local;name=foo;version=1.0;downloadMissing=true'
```

Note that missing source or debug packages will not cause the promote to fail
(they will instead be ignored, and print a warn log). This is because source and
debug packages are generally located in a different repository, and are not
required in order to install the module normally.
