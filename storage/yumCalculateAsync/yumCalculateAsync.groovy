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
// usage: curl -X POST -u admin:password http://localhost:8088/artifactory/api/plugins/execute/yumCalculateQuery?params=uid=1234

// a thread pool, for spawning threaded tasks
def threadPool = ctx.beanForType(CachedThreadPoolTaskExecutor.class)
// a global hash map, mapping paths to run uids
// "repo-name/path/to/dir" -> [curr: "running uid", next: "enqueued uid"|null]
// also used as the mutex controlling access to both pathMap and uidMap
def pathMap = new HashMap()
// a global hash map, mapping run uids to status and path values
// "uid" -> [status: "current run status", path: "repo-name/path/to/dir"]
def uidMap = new HashMap()

executions {
    yumCalculateAsync(groups: ['indexers']) { params ->
        // open a new thread, and run calculation for a uid, as well as any
        // other uids that are queued for the same path
        def beginCalc = { uid ->
            threadPool.submit {
                def myuid = uid
                while (myuid != null) {
                    def mypath = uidMap[myuid]['path']
                    asSystem {
                        def yumBean = ctx.beanForType(InternalYumAddon.class)
                        def path = RepoPathFactory.create(mypath)
                        yumBean.calculateYumMetadata(path)
                    }
                    // used for testing
                    // try {
                    //     Thread.sleep(10000)
                    // } catch (InterruptedException ex) {
                    //     Thread.currentThread().interrupt()
                    // }
                    synchronized (pathMap) {
                        uidMap[myuid]['status'] = 'done'
                        if (pathMap[mypath]['next'] == null) {
                            pathMap.remove(mypath)
                            myuid = null
                        } else {
                            myuid = pathMap[mypath]['next']
                            pathMap[mypath]['next'] = null
                            pathMap[mypath]['curr'] = myuid
                            uidMap[myuid]['status'] = 'processing'
                        }
                    }
                }
            }
        }
        String repPath = params?.get('path')?.get(0) as String
        if (!repPath) {
            status = 400
            message = "Need a path parameter to calculate YUM metadata"
            return
        }
        def repoPath = RepoPathFactory.create(repPath)
        if (!repositories.exists(repoPath)) {
            status = 404
            message = "Directory $repoPath.id does not exist"
            return
        }
        def dirDepth = repoPath.path.empty ? 0 : repoPath.path.split('/').length
        LocalRepositoryConfiguration repoConf
        repoConf = repositories.getRepositoryConfiguration(repoPath.repoKey)
        if (repoConf.packageType != 'yum') {
            status = 400
            message = "Given repository $repoPath.repoKey not a YUM repository"
            return
        }
        if (repoConf.yumRootDepth != dirDepth) {
            status = 400
            message = "Given directory $repoPath.path not at YUM repository's"
            message += " configured depth"
            return
        }
        if (repoConf.calculateYumMetadata) {
            status = 400
            message = "YUM metadata is set to calculate automatically"
            return
        }
        def uid = null, startThread = false
        def rpath = repoPath.toPath()
        synchronized (pathMap) {
            if (rpath in pathMap && pathMap[rpath]['next'] != null) {
                uid = pathMap[rpath]['next']
            } else {
                uid = UUID.randomUUID()
                if (rpath in pathMap) {
                    uidMap[uid] = [status: 'enqueued', path: rpath]
                    pathMap[rpath]['next'] = uid
                } else {
                    uidMap[uid] = [status: 'processing', path: rpath]
                    pathMap[rpath] = [curr: uid, next: null]
                    beginCalc(uid)
                }
            }
        }
        status = 200
        message = "{\"uid\":\"$uid\"}"
    }

    yumCalculateQuery(groups: ['indexers']) { params ->
        String uidstr = params?.get('uid')?.get(0) as String
        if (!uidstr) {
            status = 400
            message = "Need a uid parameter to check YUM metadata calculation"
            return
        }
        def uid = null
        try {
            uid = UUID.fromString(uidstr)
        } catch (IllegalArgumentException ex) {
            status = 400
            message = "Given uid parameter is not a valid uid"
            return
        }
        def ret = null
        synchronized (pathMap) {
            if (uid in uidMap) {
                ret = uidMap[uid]['status']
                if (ret == 'done') {
                    uidMap.remove(uid)
                }
            }
        }
        if (ret == null) {
            status = 404
            message = "Given uid was not found"
            return
        }
        status = 200
        message = "{\"status\":\"$ret\"}"
        if (ret == 'enqueued') {
            def proc = pathMap[uidMap[uid]['path']]['curr']
            message = "{\"status\":\"$ret\",\"processing\":\"$proc\"}"
        }
    }
}
