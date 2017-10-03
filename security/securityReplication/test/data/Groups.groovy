package data
import definitions.GroupClass;

class Groups {
	List<GroupClass> groupList = []

	def adminGroups = new GroupClass (
		name : 'admin-group',
		description : 'Administration-Group',
	)
	
	def devGroups = new GroupClass (
		name : 'dev-group',
		description : 'Developers-Group',
	)
	
	def qaGroups = new GroupClass (
		name : 'qa-group',
		description : 'Quality-Assurance-Groups',
	)
	
	def devOpsGroups = new GroupClass (
		name : 'devops-group',
		description : 'Dev-Operation-Group',
	)

	def solDevGroups = new GroupClass (
		name : 'soldev-group',
		description : 'Solution Development Team'
	)

	def jfrog = new GroupClass (
		name : 'jfrog',
		description : 'JFrog INC'
	)

	def defaultGroupList = [adminGroups, devGroups, devOpsGroups, qaGroups, solDevGroups, jfrog];

	def createDynamicGroupList(int count, int startIndex, def groupPrefix, def password) {

		GroupClass groupItem

		for (int i = startIndex; i < count + startIndex; i++) {
			groupItem = new GroupClass(
					name: groupPrefix + i,
					description: 'auto generated groups',
					autoJoin: false,
					realm: '',
					realmAttributes:''
			)
			groupList << groupItem;
		}
	}

	def clearGroupsList () {
		groupList.clear()
	}
}
