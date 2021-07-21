/**
 * Copyright (C) 2016 WhiteSource Ltd.
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


import groovy.transform.Field
import org.artifactory.fs.ItemInfo
import org.artifactory.repo.RepoPath

import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.client.WhitesourceService;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.Coordinates;
import org.whitesource.agent.api.dispatch.CheckPolicyComplianceResult;
import org.whitesource.agent.api.dispatch.GetDependencyDataResult;
import org.whitesource.agent.api.model.ResourceInfo;
import org.whitesource.agent.api.model.VulnerabilityInfo;
import org.whitesource.agent.api.model.PolicyCheckResourceNode;

import javax.ws.rs.core.*


@Field final String ACTION = 'WSS-Action'
@Field final String POLICY_DETAILS = 'WSS-Policy-Details'
@Field final String DESCRIPTION = 'WSS-Description'
@Field final String HOME_PAGE_URL = 'WSS-Homepage'
@Field final String LICENSES = 'WSS-Licenses'
@Field final String VULNERABILITY = 'WSS-Vulnerability: '
@Field final String CVE_URL = 'https://cve.mitre.org/cgi-bin/cvename.cgi?name='

@Field final String PROPERTIES_FILE_PATH = 'plugins/whitesource.properties'
@Field final String AGENT_TYPE = 'artifactory-plugin'
@Field final String AGENT_VERSION = '2.3.1'
@Field final String OR = '|'
@Field final int MAX_REPO_SIZE = 10000

@Field final String PROJECT_NAME = 'ArtifactoryDependencies'
@Field final String BLANK = ''
@Field final String DEFAULT_SERVICE_URL = 'https://saas.whitesourcesoftware.com/agent'

/**
 * This is a plug-in that integrates Artifactory with WhiteSource
 * Extracts descriptive information from your open source libraries located in the Artifactory repositories
 * and integrates them with WhiteSource.
 *
 * The plugin will check each item details against the organizational policies
 * Check policies suggests information about the action (approve/reject),
 * and policy details as defined by the user in WhiteSource(for example : Approve some license)
 *  1. WSS-Action
 *  2. WSS-Policy-Details
 * Additional data for the item will be populated in your Artifactory property tab :
 * 1. WSS-Description
 * 2. WSS-HomePage
 * 3. WSS-Licenses
 * 4. WSS-Vulnerabilities
 */

jobs {
    /**
     * How to set cron execution:
     * cron (java.lang.String) - A valid cron expression used to schedule job runs (see: http://www.quartz-scheduler.org/docs/tutorial/TutorialLesson06.html)
     * 1 - Seconds , 2 - Minutes, 3 - Hours, 4 - Day-of-Month , 5- Month, 6 - Day-of-Week, 7 - Year (optional field).
     * Examples :
     * "0 42 9 * * ?"  - Build a trigger that will fire daily at 9:42 am
     * "0 0/2 8-17 * * ?" - Build a trigger that will fire every other minute, between 8am and 5pm, every day
    */
    updateRepoWithWhiteSource(cron: "0 10 18 * * ?") {
        log.info("Starting job updateRepoData With WhiteSource")
        def config = new ConfigSlurper().parse(new File(ctx.artifactoryHome.etcDir, PROPERTIES_FILE_PATH).toURL())
        String[] repositories = config.repoKeys as String[]
        for (String repository : repositories) {
            Map<String, ItemInfo> sha1ToItemMap = new HashMap<String, ItemInfo>()
            findAllRepoItems(RepoPathFactory.create(repository), sha1ToItemMap)
            int repoSize = sha1ToItemMap.size()
            if (repoSize > MAX_REPO_SIZE) {
                log.warn("The max repository size for check policies in WhiteSource is : ${repoPath} items, Job Exiting")
            } else if (repoSize == 0) {
                log.warn("This repository is empty or not exit : ${repository} , Job Exiting")
            } else {
                setItemsPoliciesAndExtraData(sha1ToItemMap, config, repository)
            }
        }
        log.info("Job updateRepoWithWhiteSource Finished")
    }
}


storage {
    /**
     * Handle after create events.
     *
     * Closure parameters:
     * item (org.artifactory.fs.ItemInfo) - the original item being created.
     */
    afterCreate { item ->
        if (!item.isFolder()) {
            def config = new ConfigSlurper().parse(new File(ctx.artifactoryHome.etcDir, PROPERTIES_FILE_PATH).toURL())
            Map<String, ItemInfo> sha1ToItemMap = new HashMap<String, ItemInfo>()
            sha1ToItemMap.put(repositories.getFileInfo(item.getRepoPath()).getChecksumsInfo().getSha1(), item)
            setItemsPoliciesAndExtraData(sha1ToItemMap, config, item.getRepoKey())
            log.info("New Item - {$item} was added to the repository")
        }
    }
}

    /* --- Private Methods --- */

     private void checkPolicies(Map<String, PolicyCheckResourceNode> projects, Map<String, ItemInfo> sha1ToItemMap){
         for (String key : projects.keySet()) {
             PolicyCheckResourceNode policyCheckResourceNode = projects.get(key);
             Collection<PolicyCheckResourceNode> children = policyCheckResourceNode.getChildren();
             for (PolicyCheckResourceNode child : children) {
                 ItemInfo item = sha1ToItemMap.get(child.getResource().getSha1())
                 if (item != null && child.getPolicy() != null){
                     def path = item.getRepoPath()
                     repositories.setProperty(path, ACTION, child.getPolicy().getActionType())
                     repositories.setProperty(path, POLICY_DETAILS, child.getPolicy().getDisplayName())
                 }
             }
         }
     }

    private updateItemsExtraData(GetDependencyDataResult dependencyDataResult, Map<String, ItemInfo> sha1ToItemMap){
        for (ResourceInfo resource : dependencyDataResult.getResources()) {
            ItemInfo item = sha1ToItemMap.get(resource.getSha1())
            if (item != null) {
                RepoPath repoPath = item.getRepoPath()
                if (!BLANK.equals(resource.getDescription())) {
                    repositories.setProperty(repoPath, DESCRIPTION, resource.getDescription())
                }
                if (!BLANK.equals(resource.getHomepageUrl())) {
                    repositories.setProperty(repoPath, HOME_PAGE_URL, resource.getHomepageUrl())
                }

                Collection<VulnerabilityInfo> vulns = resource.getVulnerabilities()
                for (VulnerabilityInfo vulnerabilityInfo : vulns) {
                    String vulnName = vulnerabilityInfo.getName()
                    repositories.setProperty(repoPath, VULNERABILITY + vulnName, "${vulnerabilityInfo.getSeverity()}, ${CVE_URL}${vulnName}")
                }
                Collection<String> licenses = resource.getLicenses()
                String dataLicenses = BLANK
                for (String license : licenses) {
                    dataLicenses += license + ", "
                }
                if (dataLicenses.size() > 0) {
                    dataLicenses = dataLicenses.substring(0, dataLicenses.size() - 2)
                    repositories.setProperty(repoPath, LICENSES, dataLicenses)
                }
            }
        }
    }

    private void findAllRepoItems(def repoPath, Map<String, ItemInfo> sha1ToItemMap) {
        for (ItemInfo item : repositories.getChildren(repoPath)) {
            if (item.isFolder()) {
                findAllRepoItems(item.getRepoPath(), sha1ToItemMap)
            } else {
                sha1ToItemMap.put(repositories.getFileInfo(item.getRepoPath()).getChecksumsInfo().getSha1(), item)
            }
        }
        return
    }

    private void setItemsPoliciesAndExtraData(Map<String, ItemInfo> sha1ToItemMap, def config, String repoName) {
        Collection<AgentProjectInfo> projects = new ArrayList<AgentProjectInfo>()
        AgentProjectInfo projectInfo = new AgentProjectInfo()
        projects.add(projectInfo)
        projectInfo.setCoordinates(new Coordinates(null, PROJECT_NAME, BLANK))
        // Set details
        List<DependencyInfo> dependencies = new ArrayList<DependencyInfo>()
        for (String key : sha1ToItemMap.keySet()) {
            DependencyInfo dependencyInfo = new DependencyInfo(key)
            dependencyInfo.setArtifactId(sha1ToItemMap.get(key).getName());
            dependencies.add(dependencyInfo)
        }
        projectInfo.setDependencies(dependencies)
        String url = BLANK.equals(config.wssUrl) ? DEFAULT_SERVICE_URL : config.wssUrl
        WhitesourceService whitesourceService = new WhitesourceService(AGENT_TYPE , AGENT_VERSION, url)
        GetDependencyDataResult dependencyDataResult = whitesourceService.getDependencyData(config.apiKey, repoName, BLANK, projects);
        log.info("Updating additional dependency data")
        updateItemsExtraData(dependencyDataResult, sha1ToItemMap)
        log.info("Finished updating additional dependency data")
        if (config.checkPolicies) {
            CheckPolicyComplianceResult checkPoliciesResult = whitesourceService.checkPolicyCompliance(
                    config.apiKey, repoName, BLANK, projects, false);
            log.info("Updating policies for repo")
            checkPolicies(checkPoliciesResult.getNewProjects(), sha1ToItemMap)
            checkPolicies(checkPoliciesResult.getExistingProjects(), sha1ToItemMap)
            log.info("Finished updating policies for repo")
        }
    }
