# JFrog welcomes community contribution!

Before we can accept your contribution, process your GitHub pull requests, and thank you full-heartedly, we request that you will fill out and submit JFrog's Contributor License Agreement (CLA).

[Click here](https://secure.echosign.com/public/hostedForm?formid=5IYKLZ2RXB543N) to submit the JFrog CLA.
This should only take a minute to complete and is a one-time process.

*Thanks for Your Contribution to the Community!* :-)

## Pull Request Process ##

- Fork this repository.
- Clone the forked repository to your local machine and perform the proposed changes. 
- To develop and test plugins you can use any non-production Artifactory instance you have access. All you need to do is to place the plugin files at `<ARTIFACTORY_HOME>/etc/plugins`. You can also use [artifactory-user-plugins-devenv](https://github.com/JFrogDev/artifactory-user-plugins-devenv) to setup your development environment.
- Make sure your changes follow the Acceptance Criteria below.
- When you are confident about the changes, create a Pull Request pointing to the master branch of this repository. 

## Acceptance Criteria ##

Plugins must adhere to a number of conventions, mostly in the way the plugin files are laid out. This allows tools such as the `artifactory-user-plugins-devenv` and our internal testing system, that is constantly running against all plugins in this repo, to properly recognize and work with them. These conventions are as follows:

- Plugin files must be placed inside a plugin folder. The plugin groovy file and its folder must share the same name. The plugin folder can be placed inside a category folder to group plugins under the same subject.
- If the plugin depends on an external file that it accesses while running, such as a configuration file, this file must have the same name as the plugin groovy file.
- Update the `README.md` file to be consistent with the changes. If you are proposing a new plugin or if the `README.md` file is missing, use the template below to create a new one.
- Ideally provide at least one automated test case. If you cannot provide working test cases, please use the template below to add an always-fail test case to your plugin. This will make it easier for us to identify plugins that lack test cases and need our attention.
- Test case groovy files must have the *Test* suffix in their names, and must be capitalized. The name of the test file must match the name of the test class it defines. Test cases should be written using the [Spock framework](http://spockframework.org/spock/docs/1.1/spock_primer.html).
- If there is exactly one test groovy file and no supplimentary files required to run the test, the file can be in the same folder as the plugin groovy file, and must have the same name as the plugin.
- If there are multiple test groovy files and/or tests require supplimentary files, all test-related files must be in the `test` subfolder of the plugin folder.

For example, the Artifact Cleanup plugin:
```
cleanup/artifactCleanup/README.md                   # The README.md file
cleanup/artifactCleanup/artifactCleanup.groovy      # The plugin itself
cleanup/artifactCleanup/artifactCleanup.properties  # An optional configuration file
cleanup/artifactCleanup/ArtifactCleanupTest.groovy  # The test file
```

Another example, the Chop Module Properties plugin:
```
build/chopModuleProperties/README.md                             # The README.md file
build/chopModuleProperties/chopModuleProperties.groovy           # The plugin itself
build/chopModuleProperties/test/ChopModulePropertiesTest.groovy  # The test file
build/chopModuleProperties/test/build.json                       # A test suppliment: this build is pushed to Artifactory as part of the test
```

## README.md Template ##

```markdown
# Artifactory <Name> User Plugin

<Provide a brief description on what this plugins does and the scenarios when it can/should be used.>

## Features

<List and describe in more details the plugin features>

## Installation

<Describe how to get this plugin up and running>

## Usage

<Describe how to use the features delivered by this plugin>
```

## Always-Fail Test Case Template ##
```groovy
import spock.lang.Specification

class PluginTest extends Specification {
    def 'not implemented plugin test'() {
        when:
        throw new Exception("Not implemented.")
        then:
        false
    }
}
```
