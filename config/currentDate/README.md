Artifactory Current Date User Plugin
=======================================

Returns the current date and time of the artifactory server according to a W3C profile of the ISO 8601 Standard for Date and Time Formats.
The complete date and time notation is specified as: 
YYYY-MM-DDThh:mm:ss.sTZD (e.g., 2012-07-16T19:20:30.45+01:00)

Installation
------------
Install by placing in the etc/plugins directory and run the reload plugins REST API

Usage
-----

This plugin exposes the execution `currentDate`, which returns a plain string formatted as noted

For example:

```
$ curl -u admin:password 'http://localhost:8081/artifactory/api/plugins/execute/currentDate'
```
