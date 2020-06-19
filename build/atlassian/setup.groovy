artifactory 8088, {
    plugin 'build/atlassian'
    sed 'AtlassianTest.groovy', /localhost:7990/, localhost + ':7990'
}
