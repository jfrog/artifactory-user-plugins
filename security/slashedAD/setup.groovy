artifactory 8088, {
  plugin 'security/slashedAD'
  sed 'SlashedADTest.groovy', /ldap:\/\/localhost:/, "ldap://$localhost:"
}
