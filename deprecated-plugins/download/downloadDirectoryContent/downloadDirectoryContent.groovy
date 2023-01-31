/*
 * Copyright 2014 JFrog Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.artifactory.fs.ItemInfo
import org.artifactory.repo.RepoPath
import org.artifactory.request.Request

import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * This plugin allow a user to download a directory with its content as a zip
 * file. The plugin archives the content of a directory and temporary save it in
 * the java.io.tmpdir, return it to the client and delete it from the
 * java.io.tmpdir right after. In order to be able to download an existing
 * directory, you will need to follow the below instructions:
 * 1. Deploy the user plugin under the $ARTIFACTORY_HOME/etc/plugins directory
 * 2. If your Artifactory server is configured to automatically reload the
 *    plugin, go to step 3, otherwise, you will need to restart your server so
 *    Artifactory will reload the plugin.
 * 3. Once the plugin is loaded, perform an HTTP GET request with mandatory
 *    matrix parameter named 'downloadDirectory' and value ='true'. For example:
 *
 * curl -X GET -uadmin:password "http://localhost:8081/artifactory/libs-release-local/myDirectory;downloadDirectory+=true" > result.zip
 *
 * @author Shay Bagants
 * @since 19/01/14
 */

download {
    /**
     * When asking directory with matrix param which is not exist, you should
     * get 404 Error. When this happen, the plugin will change the response from
     * 404 to the zip file
     */
    afterDownloadError { Request request ->
        boolean downloadDir = false
        request.getProperties().entries().each { item ->
            if (item.getKey().equalsIgnoreCase("downloadDirectory+") && item.getValue().equalsIgnoreCase("true")) {
                downloadDir = true
            }
        }
        if (downloadDir) {
            RepoPath dir = request.getRepoPath()
            List<RepoPath> dirChildren = new ArrayList()
            dirChildren = getAllChildren(dir, dirChildren)
            File archiveFileToReturn = getCompressed(dirChildren)
            inputStream = new DeleteFISOnClose(archiveFileToReturn)
            status = 200
        }
    }
}

private List getAllChildren(RepoPath repoPath, List list) {
    List<ItemInfo> children = repositories.getChildren(repoPath)
    if (!children.isEmpty()) {
        for (ItemInfo item : children) {
            if (item.isFolder()) {
                getAllChildren(item.getRepoPath(), list)
            } else {
                list.add(item.getRepoPath())
            }
        }
    }
    return list
}

public File getCompressed(List list) throws IOException {
    byte[] data = new byte[2048]
    File archive = new File(System.getProperty("java.io.tmpdir") + "/tmp-downloadDir" + System.nanoTime())
    FileOutputStream fos
    ZipOutputStream zos
    try {
        fos = new FileOutputStream(archive)
        zos = new ZipOutputStream(fos)
        list.each { item ->
            ZipEntry ze = new ZipEntry(item.getPath())
            zos.putNextEntry(ze)
            InputStream is
            try {
                is = repositories.getContent(item).getInputStream()
                int len
                while ((len = is.read(data)) > 0) {
                    zos.write(data, 0, len)
                }
            } finally {
                is.close()
                zos.closeEntry()
            }
        }
    } finally {
        zos.close()
        fos.close()
    }
    return archive
}

class DeleteFISOnClose extends FileInputStream {
    File file

    DeleteFISOnClose(File file) {
        super(file)
        this.file = file
    }

    public void close() {
        super.close()
        file.delete()
    }
}
