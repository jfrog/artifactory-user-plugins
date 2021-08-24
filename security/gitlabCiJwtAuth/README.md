This User Plugin adds an authentication realm that can validate gitlab CI job jwt tokens
as an auth credential. The jwt token must be sent with a `{JWT}` prefix or artifactory
will recognize it as its own jwt and intercept the login attempt before this plugin sees it.

This effectively gives gitlab projects themselves an identity in artifactory's eyes by
leveraging signed metadata in the JWT token, and allows permissions to be dynamically attached
to requests from those identities without needing to create discrete users or credentials.

Once a JWT auth request is validated, the plugin will then look for a group in artifactory's
DB with a `gitlab-` prefix and a postfix matching the name of each level of the `project_path`
claim's hierarchy (with any specified root node removed and all characthers lowercased).

For example, with a `project_path: 'org/platform/k8s'` it would look for groups named
`gitlab-platform` and `gitlab-platform-k8s`, attaching any it finds to the request session.
The session would then continue with any permissions assigned to the attached groups.

If no groups are found, then the request continues with the context of an anonymous user.

An example of authenticating using the gitlab job jwt token for pushing docker images from
the `.gitlabci.yml` file:

```
stages:
  - build
variables:
  REGISTRY: docker-local.artifactory.example.com
  REGISTRY_IMAGE: ${REGISTRY}/platform/k8s/myimage
  ARTIFACTORY_URL: "https://docker-local.artifactory.example.com/"

build:
  stage: build
  image: docker:latest
  before_script:
  - docker login -u gitlabci -p "{JWT}${CI_JOB_JWT}" $REGISTRY
  script:
    - docker build --tag $REGISTRY_IMAGE:$CI_COMMIT_SHA .
    - docker push $REGISTRY_IMAGE:$CI_COMMIT_SHA
```

This would be paired with a group in artifactory that has a write permission to the `docker-local`
repo at path `platform/k8s/**` to limit this particular repo to push to a unique registry path.

jfrog plugin docs: https://www.jfrog.com/confluence/display/JFROG/User+Plugins#UserPlugins-Realms
