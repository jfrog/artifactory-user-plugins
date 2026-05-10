import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.artifactory.repo.RepoPathFactory
import org.artifactory.resource.ResourceStreamHandle

import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Artifactory User Plugin: downloadSelectedArtifactsAsZip
 *
 * Endpoint : POST /artifactory/api/plugins/execute/downloadSelectedArtifactsAsZip
 * Info     : GET  /artifactory/api/plugins/execute/downloadSelectedArtifactsAsZipInfo
 * Auth     : Standard Artifactory credentials (Basic / API-key header)
 *
 * Request body:
 * {
 *   "files": [
 *     { "pattern": "zos-repo/lv1-release-1/MVS/JCL/JMON" },
 *     { "pattern": "zos-repo/lv1-release-1/HFS/USS/bharat.text" }
 *   ]
 * }
 *
 * Pattern format  : <repoKey>/<repoRelativePath>
 * ZIP entry paths : stripped to MVS/... or HFS/...; full path kept as fallback.
 *
 * 200 – all files packed  |  206 – partial (X-Bundle-Missing header set)
 * 400 – bad request       |  404 – nothing found  |  500 – server error
 */

executions {

    downloadSelectedArtifactsAsZip(httpMethod: 'POST', groups: ['readers']) { params, ResourceStreamHandle body ->

        def (files, parseError) = parseRequest(body, log)
        if (parseError) { badRequest(parseError); return }

        def (entries, missing) = resolveEntries(files, repositories, log)
        if (entries.isEmpty()) {
            status  = 404
            message = toJson([error: 'None of the requested artifacts were found.', missing: missing])
            return
        }

        int httpStatus = missing ? 206 : 200

        message             = buildZip(entries, repositories, log)
        responseContentType = 'application/zip'
        status              = httpStatus
    
    }

    downloadSelectedArtifactsAsZipInfo(httpMethod: 'GET') { params ->
        status              = 200
        responseContentType = 'application/json'
        message             = toJson([
            plugin  : 'downloadSelectedArtifactsAsZip',
            version : '1.0.0',
            endpoint: 'POST /artifactory/api/plugins/execute/downloadSelectedArtifactsAsZip',
            info    : 'GET  /artifactory/api/plugins/execute/downloadSelectedArtifactsAsZipInfo',
            body    : '{ "files": [ { "pattern": "<repoKey>/<path>" }, ... ] }'
        ])
    }
}

// ── Request parsing ─────────────────────────────────────────────────────────

private List parseRequest(ResourceStreamHandle body, log) {
    if (!body) return [null, 'Request body is empty.']

    String text
    try {
        text = new InputStreamReader(body.inputStream, 'UTF-8').text?.trim()
        if (!text) return [null, 'Request body is empty.']
        def json = new JsonSlurper().parseText(text)
        if (!(json?.files instanceof List) || json.files.isEmpty())
            return [null, '"files" array is missing or empty.']
        return [json.files, null]
    } catch (Exception e) {
        log.error("parseRequest failed: ${e.message}")
        return [null, "Could not parse request body: ${e.message}"]
    }
}

// ── Artifact resolution ─────────────────────────────────────────────────────

private List resolveEntries(List files, repositories, log) {
    def entries = []
    def missing = []

    files.each { f ->
        String pattern = f?.pattern?.trim()
        if (!pattern) return

        int sep = pattern.indexOf('/')
        if (sep < 0) { missing << pattern; return }

        String repoKey  = pattern[0..<sep]
        String filePath = pattern[(sep + 1)..-1]
        def    repoPath = RepoPathFactory.create(repoKey, filePath)

        if (!repositories.exists(repoPath)) {
            log.warn("Not found: $pattern")
            missing << pattern
            return
        }

        entries << [pattern: pattern, repoPath: repoPath, entry: zipEntryName(filePath)]
    }

    return [entries, missing]
}

// ── ZIP building ────────────────────────────────────────────────────────────

private byte[] buildZip(List entries, repositories, log) {
    def baos = new ByteArrayOutputStream()
    def zos  = new ZipOutputStream(baos)
    try {
        writeEntries(zos, entries, repositories, log)
        zos.finish()
    } finally {
        try { zos.close() } catch (ignored) {}
    }
    log.info("Bundle: ${entries.size()} file(s), ${baos.size()} bytes")
    return baos.toByteArray()
}

private void writeEntries(ZipOutputStream zos, List entries, repositories, log) {
    byte[] buf = new byte[16384]
    entries.each { e ->
        def handle = repositories.getContent(e.repoPath)
        try {
            zos.putNextEntry(new ZipEntry(e.entry))
            def is = handle.inputStream
            int n
            while ((n = is.read(buf)) != -1) zos.write(buf, 0, n)
            zos.closeEntry()
            log.info("Packed '${e.pattern}' → '${e.entry}'")
        } finally {
            handle.close()
        }
    }
}

// ── ZIP entry naming ────────────────────────────────────────────────────────

private String zipEntryName(String filePath) {
    def m = filePath =~ /(?:.*?)\/(MVS|HFS)\/(.*)/
    if (m) return sanitize("${m[0][1]}/${m[0][2]}")
    if (filePath =~ /^(MVS|HFS)\//) return sanitize(filePath)
    return sanitize(filePath)
}

private String sanitize(String name) {
    name?.replace('\\', '/')
        ?.replaceAll(/^\/+/, '')
        ?.replace('../', '')
        ?.replace('..', '') ?: 'unknown'
}

// ── Utilities ───────────────────────────────────────────────────────────────

private String toJson(Object obj) { JsonOutput.toJson(obj) }

private void badRequest(String msg) {
    status  = 400
    message = toJson([error: msg])
}
