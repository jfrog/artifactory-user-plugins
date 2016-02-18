/*
 * Copyright (C) 2015 JFrog Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.artifactory.addon.yum.InternalYumAddon
import org.artifactory.repo.LocalRepositoryConfiguration
import org.artifactory.repo.RepoPath
import org.artifactory.repo.RepoPathFactory
import org.artifactory.schedule.CachedThreadPoolTaskExecutor

// usage: curl -X POST -u admin:password http://localhost:8088/artifactory/api/plugins/execute/yumCalculateAsync?params=path=yum-repository/path/to/dir

def workerThread = ctx.beanForType(CachedThreadPoolTaskExecutor)
def pmap = new HashMap()
def umap = new HashMap()
int nextUid = 0

executions {
    yumCalculateAsync(groups: ['indexers']) { params ->
        String repPath = params?.get('path')?.get(0) as String
        if (!repPath) {
            status = 400
            message = "Need a path parameter to calculate YUM metadata"
            return
        }
        def repoPath = RepoPathFactory.create(repPath)
        if (!repositories.exists(repoPath)) {
            status = 404
            message = "Folder $repoPath.id to index YUM does not exist"
            return
        }
        def dirDepth = repoPath.path.empty ? 0 : repoPath.path.split('/').length
        LocalRepositoryConfiguration repoConf
        repoConf = repositories.getRepositoryConfiguration(repoPath.repoKey)
        if (repoConf.yumRootDepth != dirDepth) {
            status = 403
            message = "Given directory $repoPath.path not at YUM repository's"
            message += " configured depth"
            return
        }
        if (repoConf.calculateYumMetadata) {
            status = 403
            message = "YUM metadata is set to calculate automatically"
            return
        }
        def uid = null, startThread = false
        def rpath = repoPath.toPath()
        synchronized (pmap) {
            if (rpath in pmap && pmap[rpath]['next'] != null) {
                uid = pmap[rpath]['next']
            } else {
                uid = (nextUid++)
                while (uid in umap) {
                    uid = (nextUid++)
                }
                umap[uid] = [done: false, path: rpath]
                if (rpath in pmap) {
                    pmap[rpath]['next'] = uid
                } else {
                    pmap[rpath] = [curr: uid, next: null]
                    startThread = true
                }
            }
        }
        if (startThread) {
            workerThread.submit {
                def myuid = uid
                while (myuid != null) {
                    def mypath = umap[myuid]['path']
                    asSystem {
                        def yumBean = ctx.beanForType(InternalYumAddon.class)
                        def path = RepoPathFactory.create(mypath)
                        yumBean.calculateYumMetadata(path)
                    }
                    try {
                        Thread.sleep(10000)
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt()
                    }
                    synchronized (pmap) {
                        umap[myuid]['done'] = true
                        if (pmap[mypath]['next'] == null) {
                            pmap.remove(mypath)
                            myuid = null
                        } else {
                            myuid = pmap[mypath]['next']
                            pmap[mypath]['next'] = null
                            pmap[mypath]['curr'] = myuid
                        }
                    }
                }
            }
        }
        status = 200
        message = "{\"uid\":\"${Integer.toHexString(uid)}\"}"
    }

    yumCalculateQuery(groups: ['indexers']) { params ->
        long luid = 0
        String uidstr = params?.get('uid')?.get(0) as String
        if (!uidstr) {
            status = 400
            message = "Need a uid parameter to check YUM metadata calculation"
            return
        }
        try {
            luid = Long.parseLong(uidstr, 16)
        } catch (NumberFormatException ex) {
            status = 400
            message = "Given uid parameter is not a valid uid"
            return
        }
        if (luid != (luid & (((long) 1 << Integer.SIZE) - 1))) {
            status = 400
            message = "Given uid is not a valid uid"
            return
        }
        int uid = (int) luid
        def ret = null
        synchronized (pmap) {
            if (uid in umap) {
                ret = umap[uid]['done']
                if (ret == true) {
                    umap.remove(uid)
                }
            }
        }
        if (ret == null) {
            status = 404
            message = "Given uid was not found"
            return
        }
        status = 200
        message = "{\"status\":\"${ret ? 'done' : 'pending'}\"}"
    }
}
