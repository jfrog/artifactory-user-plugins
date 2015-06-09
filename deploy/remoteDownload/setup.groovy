// install dependency libraries
def libdir = new File('./etc/plugins/lib')
libdir.mkdir()
def url1 = new URL('https://jcenter.bintray.com/org/codehaus/groovy/modules/http-builder/http-builder/0.7.2/http-builder-0.7.2.jar')
def url2 = new URL('https://jcenter.bintray.com/net/sf/json-lib/json-lib/2.4/json-lib-2.4-jdk15.jar')
new File(libdir, 'http-builder-0.7.2.jar').newOutputStream() << url1.openStream()
new File(libdir, 'json-lib-2.4-jdk15.jar').newOutputStream() << url2.openStream()
