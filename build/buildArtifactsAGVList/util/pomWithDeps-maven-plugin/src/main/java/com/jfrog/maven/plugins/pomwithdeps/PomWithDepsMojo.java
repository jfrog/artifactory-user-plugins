package com.jfrog.maven.plugins.pomwithdeps;

import com.jfrog.maven.plugins.pomwithdeps.utils.HttpUtil;
import com.jfrog.maven.plugins.pomwithdeps.utils.URI;
import org.apache.commons.codec.net.URLCodec;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.introspect.JacksonAnnotationIntrospector;
import org.codehaus.plexus.util.StringUtils;

import java.io.*;
import java.net.URLEncoder;
import java.util.*;

/**
 * Created by user on 30/03/2016.
 */
@Mojo(name = "createPom")
public class PomWithDepsMojo extends AbstractMojo {
    @Parameter
    private String artifactoryBaseUrl;
    @Parameter
    private String userPluginName;
    @Parameter
    private String buildName;
    @Parameter
    private String buildNumber;
    @Parameter
    private String user;
    @Parameter
    private String password;
    @Parameter(defaultValue = "300")
    private String timeout;
    @Parameter
    private String httpMethod;
    @Parameter
    private String templatePomPath;
    @Parameter
    private String templateDependencyPath;

    public void execute() throws MojoExecutionException {
        getLog().info("Fetching dependencies from: " + createUrl());
        List<Dependency> dependencies;
        try {
            dependencies = fetchDependencies();
        } catch (IOException e) {
            throw new MojoExecutionException("Failed while fetching dependencies", e);
        }
        try {
            createPom(dependencies);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed while creating pom file", e);
        }
    }

    private String readFile(String path) throws IOException {
        File f = new File(path);
        if (!f.isFile()) {
            throw new IOException("File does not exists: " + path);
        }
        FileInputStream inputStream = null;
        try {
            inputStream = new FileInputStream(path);
            return IOUtils.toString(inputStream);
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
    }

    private void createPom(List<Dependency> dependencies) throws IOException {
        String dependencyTemplate = readFile(templateDependencyPath);

        StringBuilder xml = new StringBuilder("<dependencyManagement><dependencies>");
        for(Dependency d : dependencies) {
            String dependencyXml = dependencyTemplate.replace("$groupId", d.getGroupId())
                .replace("$artifactId", d.getArtifactId())
                .replace("$version", d.getVersion());
            xml.append("\n").append(dependencyXml);
        }
        xml.append("\n</dependencies></dependencyManagement>");

        String pomTemplate = readFile(templatePomPath);
        String pomContent = pomTemplate.replace("$dependencyManagement", xml.toString());

        String path = "new-pom.xml";
        getLog().info("Creating pom file at: " + path);

        File file = new File(path);
        FileWriter fw = null;
        PrintWriter pw = null;
        try {
            fw = new FileWriter(file);
            pw = new PrintWriter(fw);
            pw.println(pomContent);
        } finally {
            if (pw != null) {
                pw.close();
            }
            if (fw != null) {
                fw.close();
            }
        }
    }

    private List<Dependency> fetchDependencies() throws IOException {
        Dependency[] dependencies = fetchDependenciesFromArtifactory();

        getLog().info("Received " + dependencies.length + " dependencies,");
        return clearInvalidDependencies(dependencies);
    }

    private Dependency[] fetchDependenciesFromArtifactory() throws IOException {
        DefaultHttpClient httpClient =
                HttpUtil.createHttpClient(user, password, Integer.parseInt(timeout));
        HttpPost httpPost = new HttpPost(createUrl());
//        HttpResponse response = httpClient.execute(httpPost);
        HttpResponse response = HttpUtil.execute(httpPost,httpClient);
        StatusLine statusLine = response.getStatusLine();

        getLog().info("Artifactory responded with HTTP status code: " + statusLine.getStatusCode());
        if (statusLine.getStatusCode() != 200) {
            throw new IOException("Artifactory responded with HTTP status code: " +
                    statusLine.getStatusCode() + ". " + statusLine.getReasonPhrase());
        }

        HttpEntity entity = response.getEntity();
        if (entity == null) {
            throw new IOException("Received null entity in Artifactory response.");
        }
        InputStream in = entity.getContent();
        if (in == null) {
            throw new IOException("Received null input stream in Artifactory response.");
        }

        try {
//            String content = IOUtils.toString(in, "UTF-8");
            InputStream input = entity.getContent();
            String content = IOUtils.toString(input, "UTF-8");
            getLog().info("Response is : " + content);
            JsonParser parser = createJsonParser(content);
//            Map m = parser.readValueAs(Map.class);
//            getLog().info("Artifactory version: " + m.get("version"));

//            content = "[{\"groupId\":\"com.mkyong\",\"artifactId\":\"spring3-mvc-maven-xml-hello-world\",\"version\":\"20160330.234333-18\"},{\"groupId\":\"org.jfrog.test\",\"artifactId\":\"multi2\",\"version\":\"20160329.213502-14\"},{\"groupId\":\"org.jfrog.test\",\"artifactId\":\"multi1\",\"version\":\"20160329.205725-10\"},{\"groupId\":\"org.jfrog.test\",\"artifactId\":\"multi\",\"version\":\"20160329.213915-15\"},{\"groupId\":\"org.jfrog.test\",\"artifactId\":\"multi3\",\"version\":\"20160330.234255-17\"},{\"groupId\":\"org.jfrog.test\",\"artifactId\":\"multi2\",\"version\":null},{\"groupId\":\"org.jfrog.test\",\"artifactId\":\"multi\",\"version\":null},{\"groupId\":\"org.jfrog.test\",\"artifactId\":\"multi1\",\"version\":null},{\"groupId\":\"org.jfrog.test\",\"artifactId\":\"multi3\",\"version\":null},{\"groupId\":\"org.jfrog.test\",\"artifactId\":\"bintray-info\",\"version\":null},{\"groupId\":\"com.example.maven-samples\",\"artifactId\":\"single-module-project\",\"version\":\"20160330.234349-1\"},{\"groupId\":\"com.example.maven-samples\",\"artifactId\":\"server\",\"version\":\"20160330.234349-1\"},{\"groupId\":\"com.example.maven-samples\",\"artifactId\":\"webapp\",\"version\":\"20160330.234349-1\"},{\"groupId\":\"com.example.maven-samples\",\"artifactId\":\"parent\",\"version\":\"20160329.212249-11\"},{\"groupId\":\"com.example.maven-samples\",\"artifactId\":\"multi-module-parent\",\"version\":\"20160329.213557-14\"}]";
//                content = response.toString();
//            parser = createJsonParser(content);
            return parser.readValueAs(Dependency[].class);
        } finally {
            in.close();
        }
    }

    private String createUrl(){
//     example url:    http://localhost:8080/artifactory/api/plugins/execute/MavenDep?params=buildName=build-aggregator|buildNumber=70
//        String params = URLEncoder.encode("?params=buildName=build-aggregator|buildNumber=70");
        HashMap<String,String> params = new HashMap<String, String>();
        params.put("buildName",buildName);
        params.put("buildNumber",buildNumber);
        StringBuilder stb = new StringBuilder(artifactoryBaseUrl + "/api/plugins/execute/" + userPluginName + "?");

        try {
            appendParamsToUrl(params,stb);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return stb.toString();
    }


    private void appendParamsToUrl(Map<String, String> requestParams, StringBuilder urlBuilder)
            throws UnsupportedEncodingException {
        if ((requestParams != null) && !requestParams.isEmpty()) {
            urlBuilder.append("params=");
            Iterator<Map.Entry<String, String>> paramEntryIterator = requestParams.entrySet().iterator();
            String encodedPipe = encodeUrl("|");
            while (paramEntryIterator.hasNext()) {
                Map.Entry<String, String> paramEntry = paramEntryIterator.next();
                urlBuilder.append(encodeUrl(paramEntry.getKey()));
                String paramValue = paramEntry.getValue();
                if (StringUtils.isNotBlank(paramValue)) {

                    urlBuilder.append("=").append(encodeUrl(paramValue));
                }

                if (paramEntryIterator.hasNext()) {

                    urlBuilder.append(encodedPipe);
                }
            }
        }
    }


    public static String encodeUrl(String unescaped) {
        byte[] rawdata = URLCodec.encodeUrl(URI.allowed_query,
                org.apache.commons.codec.binary.StringUtils.getBytesUtf8(unescaped));
        return org.apache.commons.codec.binary.StringUtils.newStringUsAscii(rawdata);
    }

    private List<Dependency> clearInvalidDependencies(Dependency[] dependencies) {
        List<Dependency> validDependencies = new ArrayList<Dependency>();
        for(Dependency d : dependencies) {
            if (d.isValid(getLog())) {
                validDependencies.add(d);
            }
        }
        return validDependencies;
    }

    public JsonParser createJsonParser(String content) throws IOException {
        JsonFactory jsonFactory = createJsonFactory();
        return jsonFactory.createJsonParser(content);
    }

    public JsonFactory createJsonFactory() {
        JsonFactory jsonFactory = new JsonFactory();
        ObjectMapper mapper = new ObjectMapper(jsonFactory);
        mapper.getSerializationConfig().setAnnotationIntrospector(new JacksonAnnotationIntrospector());
        mapper.getSerializationConfig().setSerializationInclusion(JsonSerialize.Inclusion.NON_NULL);
        jsonFactory.setCodec(mapper);
        return jsonFactory;
    }
}
