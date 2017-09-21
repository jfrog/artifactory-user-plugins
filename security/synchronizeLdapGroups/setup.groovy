artifactory 8088, {
  plugin 'security/synchronizeLdapGroups'
  sed 'SynchronizeLdapGroupsTest.groovy', /ldap:\/\/localhost:/, "ldap://$localhost:"
}
