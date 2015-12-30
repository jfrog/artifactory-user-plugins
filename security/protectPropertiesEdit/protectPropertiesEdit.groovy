/*
 * Copyright (C) 2014 JFrog Ltd.
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

import org.artifactory.exception.CancelException
import org.artifactory.repo.*

storage {
    beforePropertyCreate { item, name, values ->
        checkPropChangeAuthorization(item, name, values)
    }
    beforePropertyDelete { item, name ->
        checkPropChangeAuthorization(item, name, "")
    }
}

def checkPropChangeAuthorization(item, name, values) {
    def userName = 'admin'
    // the only user that can edit/delete existing properties is admin.
    if (security.currentUsername != userName &&
        repositories.hasProperty(item.repoPath, name)) {
        status = 403
        log.info("User ${security.currentUsername} try to set the property" +
                 " $name with value $values which is already set => Forbidden")
        throw new CancelException("Property overloading of $name is forbidden", status)
    }
}
