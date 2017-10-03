package definitions


class PermissionClass {
		def name = ''
		def includesPattern = '**/*'
		def excludesPattern = ''
		List repositories
		Map<String, List> users = [:]
		Map<String, List> groups = [:]
}


