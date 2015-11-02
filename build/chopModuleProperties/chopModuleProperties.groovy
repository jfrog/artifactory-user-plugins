/*
 * Copyright (C) 2014 JFrog Ltd.
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
 * This plugin is responsible to check whether the module properties are longer
 * than 900 charaters, which cause the DB to fale. If true, chop the property to
 * 900 characters.
 *
 * @author Michal Reuven
 * @since 02/23/15
 */

build {
    beforeSave { DetailedBuildRun buildRun ->
        log.info("choppoing properties of ${buildRun.name} that are longer" +
                 " than 900 characters.")
        Build build = buildRun.build
        build.modules.each { m ->
            log.debug "m.properties: ${m.properties}"
            Map<String, String> changed = [:]
            m.properties.each { String k, String v ->
                log.debug "property: $k $v"
                log.debug "p.property length: ${v.length()}"
                if (v.length() > 899) {
                    log.debug "property is too long. chopping"
                    changed[k] = v.substring(0, 899)
                }
            }
            m.properties.putAll(changed)
        }
    }
}
