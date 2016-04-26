WhiteSource-Artifactory-Plugin
==============================

This plugin integrates Artifactory Artifacts with WhiteSource.
The Artifactory plugin adds additional information to your artifacts.  

The Artifactory plugin works in two modes:
==========================================
1. Cron based job - when invoked, repositories artifacts will be checked 
   in WhiteSource and additional data will be added to the property tab 
   of each artifact.
2. Adding new Artifact - when uploaded, new artifact will be checked in
   WhiteSource. Policies will be checked and additional data will be 
   added to the property tab of the artifact.
   
How it Works
============
1. Clone this GitHub repo or just copy the whitesource-artifactory-plugin.groovy 
   and whitesource-artifactory-plugin.properties files.
2. Put these files under ../path/to/your/artifactory/etc/plugins/.
3. Create a 'lib' folder under  .../path/to/your/artifactory/etc/plugins/lib .
4. Download the latest version of the following jars and put them in the 
   lib folder
   * http://search.maven.org/#search%7Cga%7C1%7Ca%3A%22wss-agent-report%22
   * http://search.maven.org/#search%7Cga%7C1%7Ca%3A%22wss-agent-api-client%22
   * http://search.maven.org/#search%7Cga%7C1%7Ca%3A%22wss-agent-api%22
5. Schedule the job in whitesource-artifactory-plugin.groovy file.


Cron Scheduling Example:
========================

Open the whitesource-artifactory-plugin.groovy file in your txt editor 
and go to the "jobs" section.
Find a row similar to this: updateRepoWithWhiteSource(cron: "* * * * * ?")  
and schedule the job.

Cron parameters (from left to right) :
1 - seconds, 2 - Minutes, 3 - Hours, 4 - Day-of-Month, 5 - Month, 
6 - Day-of-Week, 7 - Year (optional field).

Examples:
"0 42 10 * * ?" - Build a trigger that will fire daily at 10:42 am.
"0 0/2 8-17 * * ?" - Build a trigger that will fire every other minute, 
between 8am and 5pm, every day. 

Properties File:
================
1. checkPolicies parameter - whether or not to send the check policies 
   request to WhiteSource (true/false).
2. apiKey - unique identifier of the organization, can be retrieved from 
   the admin page in your WhiteSource account.
3. repoKeys - The list of the repositories names to scan.
4. wssUrl - URL to send the request, defaults to 
   "https://www.whitesourcesoftware.com/agent".
