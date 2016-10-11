Artifactory Security Replication Plugin
=============================

Overview:
- Purpose
- Plugin activity history
- Deployment Usage
- Known Issues & Plugin Limitations

`Purpose`:
- `Story`: As an Artifactory administrator, I would like to have all my user’s api keys, credentials, and permissions replicated to all Artifactory instances in my mesh network, so that I can have users access their necessary objects quickly and seamlessly from any instance. 
- `Definition of Done`: 
    1. Provide a plugin that can replicate all user’s api keys, credentials, and permissions to every Artifactory instance as specified in the administrators mesh network.
    2. In the case of a failover, the other Artifactory instances will continue to replicate user information
    3. Allow an easy means of configuration of the mesh network and deployment of the plugin across the network for the Artifactory administrator 
- `Acceptance Criteria`: 
    1. All users api keys, credentials and permissions can be seen in all Artifactory instances in the specified mess network
    2. User api keys, credentials and permissions can be added/deleted on any instance and they will be seen in all other instances
    3. Any instance can be brought down and full user replication will still occur for the remaining instances
    4. No data loss will be incurred when replicating instances, even in cases of failover. 

`Plugin activity history`:
- Beta (v0.1) Coding Done (feature complete)
- `Testing`:
    - Functional
        1. Adding 650 users and delete some users on a node and verify replications to other 2 nodes.  Random manual check on a few users API keys is replicated. Each user belongs to a group and each group belongs to a permission group.
        2. Repeat the tests on the other 2 nodes and verify.
        3. Resetting each of the three nodes and verify the node has the latest replicated users after restart.
    - Stability
        1. Rerun test suite on a node the next day and verify consistency.
- Integration - to be done
        1. Run the tests suites while Artifactory Garbage collect, performing replications, downloads, uploads, and system exports.

`Deployment Usage`:

`Known Issues & Plugin Limitations`:
- `Known Issues`:
    1. Cannot upgrade plugins mid-sync-tasks, must restart all instances during plugin upgrades
    2. Database insertions are non-transactional
    3. No Instance Read/Write Locks
- `Plugin Limitations`:
    1. Required to have full mesh topology
    2. Admin must have single api key to all instances






