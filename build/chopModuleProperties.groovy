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

import org.artifactory.build.DetailedBuildRun
import org.jfrog.build.api.Build
/**
 *
 * Date: 2/23/15
 * @author Michal Reuven
 */

build {
    beforeSave { DetailedBuildRun buildRun ->
        log.info "choppoing properties of ${buildRun.name} that are longer than 900 characters."
        Build build = buildRun.build
        build.modules.each { m ->
            log.debug "m.properties: ${m.properties}"
            m.properties.each { p ->
                log.debug "propertiy value: ${p.properties.toString()}"
                log.debug "p.properties length: ${p.properties.toString().length()}"
                if (p.properties.toString().length() > 899) {
                    p.properties = p.properties.toString().substring(0, 899)
                    log.debug "property value after change: ${p.properties.toString()}"
                }
            }
        }
    }
}


