import org.artifactory.repo.*
import org.artifactory.exception.CancelException

storage { 

	beforePropertyCreate {item, name, values -> checkPropChangeAuthorization(item,name,values) }

	beforePropertyDelete {item, name -> checkPropChangeAuthorization(item,name,"") }
}

def checkPropChangeAuthorization(item,name,values) {
    def userName="admin"  // the only user that can edit/delete existing properties is admin.
    if (security.currentUsername != userName && repositories.hasProperty(item.repoPath,name)) {
        log.info "User ${security.currentUsername} try to set the property $name with value $values which is already set => Forbidden"
        throw new CancelException("Property overloading of $name is forbidden")
    }
}
