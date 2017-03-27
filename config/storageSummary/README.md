Artifactory Storage Summary User Plugin
=======================================

Exposes a summary of the storage info via the REST api.

Usage
-----

This plugin exposes the execution `storageSummary`, which returns a JSON
representation of the summary. The returned JSON string has the following
fields:

- `binariesSummary`: An object containing a summary of the the binary info.
- `repositoriesSummaryList`: A list of summaries for each repository.
- `fileStoreSummary`: An object containing a summary of the filestore info.

The `binariesSummary` object has the following fields:

- `artifactsCount`: The total number of artifacts being tracked.
- `itemsCount`: The total number of artifacts and folders being tracked.
- `optimization`: The current optimization level.
- `artifactsSize`: The sum of the sizes of all the artifacts being tracked.
- `binariesSize`: The sum of the sizes of all the files being stored.
- `binariesCount`: The total number of files being stored.

The `fileStoreSummary` object has the following fields:

- `freeSpace`: The total amount of unused storage space.
- `usedSpace`: The total amount of space being used for storage.
- `totalSpace`: The total amount of space available for storage.
- `storageDirectory`: The filepath of the directory being used for storage.
- `storageType`: The type of storage being used. One of `"filesystem"`,
  `"fullDb"`, `"cachedFS"`, `"S3"`, `"S3Old"`, or `"goog"`.

Each member of `repositoriesSummaryList` represents a repository, and has the
following fields:

- `percentage`: The percentage of available space being used for storage.
- `packageType`: The package type of the repository.
- `itemsCount`: The number of artifacts and folders being tracked in the
  repository.
- `usedSpace`: The total amount of used storage space.
- `filesCount`: The number of artifacts being tracked in the repository.
- `foldersCount`: The number of folders being tracked in the repository.
- `repoType`: The type of repository. Might be `"LOCAL"`, `"CACHE"`, `"REMOTE"`,
  `"VIRTUAL"`, or `"BROKEN"`.
- `repoKey`: The name of the repository.

One memeber of `repositoriesSummaryList` represents the sum of all the
repositories, and has the following fields:

- `itemsCount`: The total number of artifacts and folders being tracked.
- `usedSpace`: The total amount of space being being used for storage.
- `filesCount`: The total number of artifacts being tracked.
- `foldersCount`: The total number of folders being tracked.
- `repoType`: The string `"NA"`.
- `repoKey`: The string `"TOTAL"`.

For example:

```
$ curl -u admin:password 'http://localhost:8081/artifactory/api/plugins/execute/storageSummary'
```
