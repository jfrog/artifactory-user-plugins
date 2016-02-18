Artifactory YUM Calculate Async User Plugin
===========================================

This plugin asynchronously calculates the YUM metadata at a given repository
path. It exposes two executions:

yumCalculateAsync
-----------------

This execution endpoint takes a param `path`, which is the repository path of
the subtree to calculate YUM metadata for. The metadata calculation begins in a
new thread, and a unique identifier for this particular calculation run is
immediately returned. This identifier should be passed to the
`yumCalculateQuery` endpoint, to check the status of the calculation run.

This endpoint returns a JSON object with a single property `uid`, which is a
string representing the unique identifier for the calculation run.

If the YUM metadata for a particular path is already in the process of
calculating when this endpoint is called, the new calculation run is queued, and
will be run once the current calculation completes.

yumCalculateQuery
-----------------

This execution endpoint takes a param `uid`, which is the universal identifier
of the calculation run to query. A JSON object containing the status of the run
is immediately returned. The JSON object has a single property `status`, which
can be either the string `"pending"` or the string `"done"`. After the first
time `"done"` is returned, the plugin stops tracking the uid, and will report
errors in response to any further queries (the uid might also be reused later).

Executing
---------

To execute this plugin:

`curl -X POST -u admin:password http://localhost:8088/artifactory/api/plugins/execute/yumCalculateAsync?params=path=yum-repository/path/to/dir`

This will respond with a JSON string similar to the following:

`{"uid":"1234"}`

Then, the following call can be used to check the status:

`curl -X POST -u admin:password http://localhost:8088/artifactory/api/plugins/execute/yumCalculateQuery?params=uid=1234`

This will respond with one of the following two JSON strings:

`{"status":"pending"}` if the calculation run has not yet completed.

`{"status":"done"}` if the calculation run has completed.

The query can be repeated as long as the status is still pending.
