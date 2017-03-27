Artifactory YUM Calculate Async User Plugin
===========================================

This plugin asynchronously calculates the YUM metadata at a given repository
path. It exposes two executions:

yumCalculateAsync
-----------------

This execution endpoint takes a param `path`, which is the repository path (eg,
`repo-name/path/to/dir`) of the subtree for which to calculate YUM metadata. The
metadata calculation begins in a new thread, and a unique identifier for this
particular calculation run is immediately returned. This identifier should be
passed to the `yumCalculateQuery` endpoint, to check the status of the
calculation run.

This endpoint returns a JSON object with a single property `uid`, which is a
string representing the unique identifier for the calculation run.

If the YUM metadata for a particular path is already in the process of
calculating when this endpoint is called, the new calculation run is queued, and
will be run once the current calculation completes. If there is already a run in
the queue, that run's identifier is used, rather than a newly created one.

yumCalculateQuery
-----------------

This execution endpoint takes a param `uid`, which is the universal identifier
of the calculation run to query. A JSON object containing the status of the run
is immediately returned. The JSON object has a single property `status`, which
can be one of the strings `"enqueued"`, `"processing"`, or `"done"`. After the
first time `"done"` is returned, the plugin stops tracking the uid, and will
report errors in response to any further queries.

Executing
---------

To execute this plugin:

`curl -X POST -u admin:password http://localhost:8088/artifactory/api/plugins/execute/yumCalculateAsync?params=path=yum-repository/path/to/dir`

This will respond with a JSON string similar to the following:

`{"uid":"1234"}`

Then, the following call can be used to check the status:

`curl -X POST -u admin:password http://localhost:8088/artifactory/api/plugins/execute/yumCalculateQuery?params=uid=1234`

This will respond with one of the following three JSON strings:

`{"status":"enqueued","processing":"5678"}` if the calculation run is waiting in
the queue. The `processing` property is the uid of the currently executing run,
that this run is waiting for.

`{"status":"processing"}` if the calculation run is currently running.

`{"status":"done"}` if the calculation run has completed.

The query can be repeated as long as the status is still pending.
