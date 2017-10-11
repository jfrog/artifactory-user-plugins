/**
 * Created by stanleyf on 11/10/2017.
 */

import static groovy.io.FileType.FILES

class Populate {

    def pluginList = []

    static void main (String[] args) {
        println "Create Artifactory User Plugin Metadata"

        Populate populate = new Populate()
        populate.userPluginList()
        populate.copyMetadata ()
    }

    def userPluginList () {
        File filepath = new File(".")
        filepath.traverse (type: FILES, maxDepth: 2) { file ->
            if (file.name.endsWith('.groovy') && file.name != 'setup.groovy' && !file.name.endsWith('Test.groovy')) {
                String prefix = "${file.name.minus('.groovy')}"
                String tmp = file.absolutePath.minus("/$file.name")
                String fullPrefix = tmp.minus("$filepath.absolutePath/")
                pluginList << fullPrefix
            }
        }
        pluginList.each {pname ->
            println pname
        }
    }

    def copyMetadata() {
        File source = new File("./sample.yaml")
        pluginList.each { pname ->
            File pluginDir = new File("./" + pname)
            if (pluginDir.isDirectory()) {
                File dest = new File("./" + pname + "/metadata.yaml")
                if (dest.exists()) {
                    dest.delete()
                    dest = new File("./" + pname + "/metadata.yaml")
                }
                dest << source.text
            }
        }
    }
}
