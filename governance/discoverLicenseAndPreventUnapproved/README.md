Artifactory Discover License and Prevent Unapproved 
===================================================

Download
--------
This plugin prevents download of artifacts whose licenses have not been approved.  Response code 403 is returned if the
artifact's property 'approve.status' equals 'pending' or 'rejected', else the artifactory response code is returned.  

Upload 
------
This plugin sets the artifact's license approval status property 'approve.status'.  The approve.status is 
 'rejected' : If the artifact's licenses contains either 'GPL-2.0' or 'CC  BY-SA'
 'approved' : if any one of the artifact's licenses is approved.  
 'pending'  : If no licenses found
 
 


