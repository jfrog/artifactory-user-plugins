import org.artifactory.exception.CancelException

/**
 * Class that holds the paths that can not be override by non admin users.
 */

class ReposName {

    public ArrayList<String> name = new ArrayList();

    /**
     * Initialize the repos list.
     * The list is taken from the properties file.
     * Those paths are not allowed to override existing artifacts by non admin users.
     */

    public void init(){

        def configFile = new ConfigSlurper().parse(new File("${System.properties.'artifactory.home'}/etc/plugins/reposName.properties").toURL())
        String[] repos = configFile.repos;
        for(int i=0; i<repos.length; i++){
            name.add(repos[i]);
        }

    }

    /**
     * Handle before create events.
     *
     * Closure parameters:
     * target (String) - the path of the item that is being created.
     *
     * @return true/false if the path exists in the list
     */

    public  boolean checkIfExsists(String target){
        for (String s: name){
            if(target.contains(s)){
                return true
            }
        }
        return false;
    }

}

storage {

    /**
     * Handle before create events.
     *
     * Closure parameters:
     * item (org.artifactory.fs.ItemInfo) - the original item being created.
     */
    beforeCreate { item ->
        if (!item.isFolder()) {
            if (!security.currentUser().isAdmin()) {
                ReposName reposName = new ReposName();
                reposName.init();
                if (item.getLastModified() != item.getCreated() && reposName.checkIfExsists(item.getRepoPath().toPath())) {
                    log.info("The file exists already, only Administrator can override")
                    throw new CancelException("This item already exists in the following path: "
                            + item.getRepoPath().getParent().toPath(), 403)
                }
            }
        }
    }
}