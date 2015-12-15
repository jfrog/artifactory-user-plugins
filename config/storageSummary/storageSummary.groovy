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

import groovy.json.JsonBuilder
import groovy.lang.MissingPropertyException
// depending on the Artifactory version, InternalStorageService might be in
// either of these locations
import org.artifactory.storage.service.*
import org.artifactory.storage.*

import static org.artifactory.api.storage.StorageUnit.toReadableString
import static org.artifactory.util.NumberFormatter.formatLong
import static org.artifactory.util.NumberFormatter.formatPercentage

executions {
    storageSummary(httpMethod: 'GET') { params ->
        def storageService = ctx.beanForType(InternalStorageService.class)
        def fileStoreInfo = storageService.fileStoreStorageSummary
        def summaryInfo = storageService.storageSummaryInfo
        def binariesInfo = summaryInfo.binariesInfo
        def binct = formatLong(binariesInfo.binariesCount)
        def itemt = formatLong(summaryInfo.totalItems)
        def filet = formatLong(summaryInfo.totalFiles)
        def optim = formatPercentage(summaryInfo.optimization)
        def usedp = formatPercentage(fileStoreInfo.usedSpaceFraction)
        def freep = formatPercentage(fileStoreInfo.freeSpaceFraction)
        def binsz = toReadableString(binariesInfo.binariesSize)
        def totsz = toReadableString(summaryInfo.totalSize)
        def totsp = toReadableString(fileStoreInfo.totalSpace)
        def useds = toReadableString(fileStoreInfo.usedSpace)
        def frees = toReadableString(fileStoreInfo.freeSpace)
        def repoSummaries = [], binSummary = [:], fstoreSummary = [:]
        binSummary['binariesCount'] = binct
        binSummary['binariesSize'] = binsz
        binSummary['artifactsSize'] = totsz
        binSummary['optimization'] = optim
        binSummary['itemsCount'] = itemt
        binSummary['artifactsCount'] = filet
        def storageType = fileStoreInfo.binariesStorageType.toString()
        fstoreSummary['storageType'] = storageType
        def storageDirLabel = null
        try {
            def binariesFolders = fileStoreInfo.binariesFolders
            storageDirLabel = 'Filesystem storage is not used'
            if (binariesFolders != null && !binariesFolders.isEmpty()) {
                storageDirLabel = binariesFolders*.absolutePath.join(', ')
            }
        } catch (MissingPropertyException ex) {
            def binariesFolder = fileStoreInfo.binariesFolder
            if (binariesFolder != null) {
                storageDirLabel = binariesFolder.absolutePath
            }
        }
        fstoreSummary['storageDirectory'] = storageDirLabel
        fstoreSummary['totalSpace'] = totsp
        fstoreSummary['usedSpace'] = "$useds ($usedp)"
        fstoreSummary['freeSpace'] = "$frees ($freep)"
        def totalSize = Double.longBitsToDouble(summaryInfo.totalSize)
        for (repo in summaryInfo.repoStorageSummaries) {
            def result = [:]
            result['repoKey'] = repo.repoKey
            result['repoType'] = repo.repoType.name()
            result['foldersCount'] = repo.foldersCount
            result['filesCount'] = repo.filesCount
            result['usedSpace'] = toReadableString(repo.usedSpace)
            result['itemsCount'] = repo.itemsCount
            try {
                result['packageType'] = repo.type
            } catch (MissingPropertyException ex) {
                result['packageType'] = "Generic"
            }
            def perc = Double.longBitsToDouble(repo.usedSpace) / totalSize
            result['percentage'] = formatPercentage(perc)
            repoSummaries += result
        }
        def result = [:]
        result['repoKey'] = 'TOTAL'
        result['repoType'] = 'NA'
        result['foldersCount'] = summaryInfo.totalFolders
        result['filesCount'] = summaryInfo.totalFiles
        result['usedSpace'] = totsz
        result['itemsCount'] = summaryInfo.totalItems
        repoSummaries += result
        def json = [:]
        json['fileStoreSummary'] = fstoreSummary
        json['repositoriesSummaryList'] = repoSummaries
        json['binariesSummary'] = binSummary
        message = new JsonBuilder(json).toPrettyString()
        status = 200
    }
}
