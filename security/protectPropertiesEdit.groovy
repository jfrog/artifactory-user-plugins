import org.artifactory.repo.*
import org.artifactory.exception.CancelException

storage { 

	beforePropertyCreate {item, name, values -> checkPropChangeAuthorization(item,name,values) }

	beforePropertyDelete {item, name, values -> checkPropChangeAuthorization(item,name,values) }

    def checkPropChangeAuthorization(item,name,values) {
		def userName="admin"  // the user you would like to block from edit and delete properies
		if (security.currentUsername == userName && repositories.hasProperty(item.repoPath,name)) {
			log.info "User ${security.currentUsername} try to set the property $name with value $values which is already set => Forbidden"
			throw new CancelException("Property overloading of $name is forbidden")
		}
	}
 
}