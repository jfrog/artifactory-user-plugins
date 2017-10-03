package data
import definitions.UserClass;

class Users {
	List<UserClass> userList = []

	def createUsersList() {
		UserClass userItem

		userItem = new UserClass(
				name: 'stanleyf',
				email: 'stanleyf@jfrog.com',
				admin: false,
				groups: ["jfrog", "soldev-group"]
		)
		userList << userItem;

		userItem = new UserClass(
				name: 'edmilisonp',
				email: 'edmilisonp@jfrog.com',
				admin: false,
				groups: ["jfrog", "soldev-group"]
		)
		userList << userItem;

		userItem = new UserClass(
				name: 'jenkins',
				email: 'jenkins@jfrog.com',
				admin: true,
				groups: ["jfrog", "soldev-group"]
		)
		userList << userItem;

		userItem = new UserClass(
				name: 'jagans',
				email: 'jagans@jfrog.com',
				admin:  false,
				groups: ["jfrog", "soldev-group"]
		)
		userList << userItem;

		userItem = new UserClass(
				name: 'jainishs',
				email: 'jainishs@jfrog.com',
				admin: false,
				groups: ["jfrog", "soldev-group"]
		)
		userList << userItem;

		userItem = new UserClass(
				name: 'narendray',
				email: 'narendray@jfrog.com',
				admin:  false,
				groups: ["jfrog", "soldev-group"]
		)
		userList << userItem;

		userItem = new UserClass(
				name: 'travisf',
				email: 'travisr@jfrog.com',
				admin:  false,
				groups: ["jfrog", "soldev-group"]
		)
		userList << userItem;

		userItem = new UserClass(
				name: 'shikharr',
				email: 'shikharr@jfrog.com',
				admin:  false,
				groups: ["jfrog", "soldev-group"]
		)
		userList << userItem;

		userItem = new UserClass(
				name: 'ankushc',
				email: 'ankushc@jfrog.com',
				admin:  false,
				groups: ["jfrog", "soldev-group"]
		)
		userList << userItem;

		userItem = new UserClass(
				name: 'eliom',
				email: 'eliom@jfrog.com',
				groups: ["jfrog", "soldev-group"]
		)
		userList << userItem;

		userItem = new UserClass(
				name: 'markg',
				email: 'markg@jfrog.com',
				admin:  false,
				groups: ["jfrog"]
		)
		userList << userItem;

		userItem = new UserClass(
				name: 'eytanh',
				email: 'eytanh@jfrog.com',
				admin:  false,
				groups: ["jfrog"]
		)
		userList << userItem;

		userItem = new UserClass(
				name: 'dev1',
				email: 'null@jfrog.com',
				admin:  false,
				groups: ["dev-group"]
		)
		userList << userItem;

		userItem = new UserClass(
				name: 'dev2',
				email: 'null@jfrog.com',
				groups: ["dev-group"]
		)
		userList << userItem;

		userItem = new UserClass(
				name: 'qa1',
				email: 'null@jfrog.com',
				groups: ["qa-group"]
		)
		userList << userItem;

		userItem = new UserClass(
				name: 'qa2',
				email: 'null@jfrog.com',
				groups: ["qa-group"]
		)
		userList << userItem;

		userItem = new UserClass(
				name: 'devops1',
				email: 'null@jfrog.com',
				groups: ["devops-group"]
		)
		userList << userItem;

		userItem = new UserClass(
				name: 'devops2',
				email: 'null@jfrog.com',
				groups: ["devops-group"]
		)
		userList << userItem;
	}

	def createDynamicUserList(int count, int startIndex, def userPrefix, def password) {

		UserClass userItem

		for (int i = startIndex; i < count + startIndex; i++) {
			userItem = new UserClass(
					name: userPrefix + i,
					email: 'null@jfrog.com',
					password: password,
					groups: ['jfrog']
			)
			userList << userItem;
		}
	}

	def clearUserList () {
		userList.clear()
	}
}
