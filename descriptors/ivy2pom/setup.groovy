// install dependency libraries
def url1 = new URL('https://jcenter.bintray.com/org/apache/ant/ant/1.8.3/ant-1.8.3.jar')
def url2 = new URL('https://jcenter.bintray.com/org/apache/ant/ant-launcher/1.8.3/ant-launcher-1.8.3.jar')
def url3 = new URL('https://jcenter.bintray.com/org/jwaresoftware/antxtras/jw-antxtras/3.0.0/jw-antxtras-3.0.0.jar')
def url4 = new URL('https://jcenter.bintray.com/org/jwaresoftware/antxtras/jw-log4ant/3.0.0/jw-log4ant-3.0.0.jar')
def libdir = new File('./etc/plugins/lib')
libdir.mkdir()
def artdir = new File('./artifactory').listFiles().find {
    it.name.startsWith('artifactory-powerpack')
}
if (artdir == null) artdir = libdir
else artdir = new File(artdir, 'tomcat/webapps/artifactory/WEB-INF/lib')
def file1 = new File(artdir, 'ant-1.8.3.jar')
def file2 = new File(artdir, 'ant-launcher-1.8.3.jar')
def file3 = new File(libdir, 'jw-antxtras-3.0.0.jar')
def file4 = new File(libdir, 'jw-log4ant-3.0.0.jar')
if (!file1.exists()) file1.newOutputStream() << url1.openStream()
if (!file2.exists()) file2.newOutputStream() << url2.openStream()
if (!file3.exists()) file3.newOutputStream() << url3.openStream()
if (!file4.exists()) file4.newOutputStream() << url4.openStream()
