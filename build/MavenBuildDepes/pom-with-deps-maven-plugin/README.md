Maven Plugin to create global pom.xml for all artifacts of parent build and child builds.
 
 Steps to build this plugin:
 
 Prerequisites (Maven)
 
 CD to root directory of this plugin
 
 run command: 
 ```
 mvn clean install
 ```
 
 To use this plugin add following section to pom.xml of your project:
 
 ```
 <plugins>
             <plugin>
                 <groupId>com.jfrog.maven.plugins</groupId>
                 <artifactId>pom-with-deps-maven-plugin</artifactId>
                 <version>1.0-SNAPSHOT</version>
                 <executions>
                     <execution>
                         <phase>clean</phase>
                         <goals>
                             <goal>createPom</goal>
                         </goals>
                     </execution>
                 </executions>
                 <configuration>
                     <artifactoryBaseUrl>http://localhost:8080/artifactory</artifactoryBaseUrl> (artifactory url)
                     <userPluginName>MavenDep</userPluginName> (Plugin Name)
                     <buildName>build-aggregator</buildName> (Build Name)
                     <buildNumber>70</buildNumber> (Build Number)
                     <user>$admin</user> (Artifactory Username)
                     <password>$password</password> (artifactory Password)
                     <httpMethod>POST</httpMethod>
                     <templatePomPath>templates/pom.xml</templatePomPath> (path to build template)
                     <templateDependencyPath>templates/dependency.xml</templateDependencyPath> (path to build dependencies template)
                 </configuration>
             </plugin>
 </plugins>
 ```
 
 
 Go to your project and run command:
 ```
 mvn clean
 ```
 
 it will call MavenDep plugin to get List of artifact's GAV using those GAV create new-pom.xml.
 
 example of new-pom.xml
 ```
 <?xml version="1.0" encoding="UTF-8"?>
 <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/POM/4.0.0          http://maven.apache.org/maven-v4_0_0.xsd">
     <modelVersion>4.0.0</modelVersion>
     <groupId>org.jfrog.test</groupId>
     <artifactId>multi</artifactId>
     <version>3.7-SNAPSHOT</version>
     <packaging>pom</packaging>
     <name>Simple Multi Modules Build</name>
     <dependencyManagement>
         <dependencies>
             <dependency>
                 <groupId>com.example.maven-samples</groupId>
                 <artifactId>single-module-project</artifactId>
                 <version>1.0-20160331.163207-1</version>
             </dependency>
             <dependency>
                 <groupId>com.example.maven-samples</groupId>
                 <artifactId>server</artifactId>
                 <version>1.0-20160331.163207-1</version>
             </dependency>
             <dependency>
                 <groupId>com.example.maven-samples</groupId>
                 <artifactId>webapp</artifactId>
                 <version>1.0-20160331.163207-1</version>
             </dependency>
             <dependency>
                 <groupId>com.example.maven-samples</groupId>
                 <artifactId>parent</artifactId>
                 <version>1.0-20160329.212249-11</version>
             </dependency>
             <dependency>
                 <groupId>com.example.maven-samples</groupId>
                 <artifactId>multi-module-parent</artifactId>
                 <version>1.0-20160329.213557-14</version>
             </dependency>
             <dependency>
                 <groupId>com.mkyong</groupId>
                 <artifactId>spring3-mvc-maven-xml-hello-world</artifactId>
                 <version>1.0-20160331.163152-19</version>
             </dependency>
             <dependency>
                 <groupId>org.jfrog.test</groupId>
                 <artifactId>multi2</artifactId>
                 <version>3.3</version>
             </dependency>
             <dependency>
                 <groupId>org.jfrog.test</groupId>
                 <artifactId>multi</artifactId>
                 <version>3.3</version>
             </dependency>
             <dependency>
                 <groupId>org.jfrog.test</groupId>
                 <artifactId>multi1</artifactId>
                 <version>3.3</version>
             </dependency>
             <dependency>
                 <groupId>org.jfrog.test</groupId>
                 <artifactId>multi3</artifactId>
                 <version>3.3</version>
             </dependency>
             <dependency>
                 <groupId>org.jfrog.test</groupId>
                 <artifactId>bintray-info</artifactId>
                 <version>3.3</version>
             </dependency>
             <dependency>
                 <groupId>org.jfrog.test</groupId>
                 <artifactId>multi2</artifactId>
                 <version>3.7-20160329.213502-14</version>
             </dependency>
             <dependency>
                 <groupId>org.jfrog.test</groupId>
                 <artifactId>multi1</artifactId>
                 <version>3.7-20160329.205725-10</version>
             </dependency>
             <dependency>
                 <groupId>org.jfrog.test</groupId>
                 <artifactId>multi</artifactId>
                 <version>3.7-20160329.213915-15</version>
             </dependency>
             <dependency>
                 <groupId>org.jfrog.test</groupId>
                 <artifactId>multi3</artifactId>
                 <version>3.7-20160331.163108-18</version>
             </dependency>
         </dependencies>
     </dependencyManagement>
</project>
 ```
 
 