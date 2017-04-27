Artifactory NetApp SnapCenter User Plugin
=======================================

A REST to trigger NetApp SnapCenter backup using from Artifactory.
---
###Steps to trigger SpanCenter Backup

1. create **body.json** with SanpCenter url, token (Non expirable), policy and resourcegroups.
    ```
       {
         "url": "https://sc1.netapp.local:8146",
         "token": "Token",
         "policy": "Database_Daily",
         "resourcegroups": "db1_netapp_local_MySQL_artdb"
       }
    ```
    
2. Use following command to call Artifactory user plugin to trigger backup":

    `curl  -u admin:password -H "Content-Type: application/json" -X POST -d @body.json "http://localhost:8081/artifactory/api/plugins/execute/snapCenter"`
    
   
