# JFrog welcomes community contribution!

Before we can accept your contribution, process your GitHub pull requests, and thank you full heartedly, we request that you will fill out and submit JFrog's Contributor License Agreement (CLA).

[Click here](https://secure.echosign.com/public/hostedForm?formid=5IYKLZ2RXB543N) to submit the JFrog CLA.
This should only take a minute to complete and is a one-time process.

*Thanks for Your Contribution to the Community!* :-)

## Pull Request Process ##

- Fork this repository.
- Clone the forked repository to your local machine and perform the proposed changes. You can use the steps provided at [artifactory-user-plugins-devenv](https://github.com/JFrogDev/artifactory-user-plugins-devenv) to setup your development environment.
- Make sure your changes respect the Acceptance Criteria below.
- When you are confident about the changes, crate a Pull Request pointing to the master branch of this repo. 

## Acceptance Criteria ##

- Plugin files must be placed inside a folder. The plugin groovy file and its folder must share the same name. The plugin folder can be placed inside a category folder to group plugins under the same subject.
- Update the README.md to be consistent with the changes. If you are proposing a new plugin or if the README.md file is missing, use the README.md template below to create a new one.
- Provide at least one automated test case. If you cannot provide working test cases, use the template below to add a always fail test case to your plugin. This will make easier for us to identify plugins missing actual test cases that need our attention.
- Test case groovy files must have the `Test` suffix in their names. Example: `MyPluginTest.groovy`
- Test case groovy files can be placed at root level of plugin folder or at a subfolder called `test`. If you have multiple files as part of your tests, you must use the subfolder method.

## README.md Template ##

```markdown
# <Name> User Plugin

<Provide a brief description on what this plugins does and the scenarios when it can/should be used.>

## Features

<List and describe in more details the plugin features>

## Instalation

<Describe how to get this plugin up and running>

## Usage

<Describe how to use the features delivered by this plugin>
```

## Always Fail Test Case Template ##
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
