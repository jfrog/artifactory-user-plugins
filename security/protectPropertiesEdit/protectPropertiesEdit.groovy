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

//modified so that only workflow.status being set to PASSED is protected

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
    def protectedProperty = 'workflow.status'
    def protectedValue = 'PASSED'
    log.error("name: "+name+ ";;; values: "+values)
    // the only user that can edit/delete existing properties is admin.
    if (name==protectedProperty) {
    if (values[0] == protectedValue) {
        if(security.currentUsername != userName) {
            status = 403
            log.error("User ${security.currentUsername} try to set the property" +
                     " $name with value $values which is already set => Forbidden")
            throw new CancelException("Property overloading of $name is forbidden",status)
        } else log.error("valid user test")
    } else log.error("unaffected value test")
    } else log.error("unaffected property test")
}
