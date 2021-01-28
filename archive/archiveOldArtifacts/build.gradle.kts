plugins {
    groovy
}

repositories {
    jcenter()
}

dependencies {
    implementation("org.codehaus.groovy:groovy-all:2.5.4")
    implementation("org.artifactory:artifactory-papi:6.5.2")
    implementation("org.artifactory:artifactory-api:6.5.2")

    testImplementation("org.jfrog.artifactory.client:artifactory-java-client-services:2.8.6")
    testImplementation("org.spockframework:spock-core:1.3-groovy-2.5")
}

tasks {
    test {
        environment("ARTIFACTORY_DATA_ARCHIVE", "${rootDir.absolutePath}/.archive/")
    }
}
