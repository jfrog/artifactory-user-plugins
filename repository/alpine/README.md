# Artifactory Alpine User Plugin

Adds support for Alpine mirror repositories, using the Generic Remote and at a certain interval removing the APK index files.

An alternative approach would be rsync, but this is not supported by Artifactory (and would result in always having a full mirror). This plugin does not remove any old apk packages from cache (which could be a possible improvement).

## Features

Very little :). Only removes apk index files at set interval.

## Installation

- Create remote repository of the Generic type, configuring any alpine apk mirror as remote.
- On the respective cache repository, add a property 'alpine' (value not needed or used).
- Install plugin as any other plugin, optionally setting the log level to info, and optionally altering the job interval from within the code.

## Usage

Point the client to use Artifactory, following the [Alpine documentation](https://wiki.alpinelinux.org/wiki/Alpine_Linux_package_management#Repository_pinning).
