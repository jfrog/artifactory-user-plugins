artifactory 8088, {
    plugin 'security/cleanExternalUsers'
    sed 'cleanExternalUsers.json', /"host": (.*),/, '"host": "http://localhost:8000/",'
    sed 'cleanExternalUsers.json', /"apitoken": (.*),/, '"apitoken": "1234",'
}
