Artifactory Discover License and Prevent Unapproved 
===================================================

Download
--------
This plugin prevents download of artifacts whose licenses have not been approved.  Response code 403 is returned if the
artifact's property 'approve.status' equals 'pending' or 'rejected', else the artifactory response code is returned.  

Upload 
------
This plugin sets the artifact's license approval status property 'approve.status' after it discovers its licenses.  The approve.status is:

- 'rejected' : If the artifact's licenses is in the FORBIDDEN_LICENSES list.
- 'approved' : If any one of the artifact's licenses is approved.  
- 'pending'  : If no licenses found

 


