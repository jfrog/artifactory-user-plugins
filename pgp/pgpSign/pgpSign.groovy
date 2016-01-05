/*
 * Copyright (C) 2014 JFrog Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.artifactory.common.StatusHolder
import org.artifactory.repo.RepoPath
import org.artifactory.repo.RepoPathFactory
import org.artifactory.resource.ResourceStreamHandle
import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.bcpg.BCPGOutputStream
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.operator.bc.*
import org.bouncycastle.openpgp.*

import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.InputStream
import java.security.Security

/**
 * Artifactory user plugin that signs all incoming artifacts using the key
 * and passphrase specified in $ARTIFACTORY_HOME/etc/plugins/pgpSign.properties
 * and deploys the resulting signature in typical fashion as an .asc file
 * parallel to the original artifact.
 * The plugin will be activated only on repository keys ending with "-local".
 *
 * Purpose-built to meet the needs of promoting artifacts to Maven Central, i.e.
 * not intended in its current form for more general use.
 *
 * See http://wiki.jfrog.org/confluence/display/RTF/User+Plugins for background.
 *
 * @author Chris Beams
 */

storage {
    Properties props = new Properties()
    props.load(new FileReader(
        new File(ctx.artifactoryHome.haAwareEtcDir, "plugins/pgpSign.properties")))
    File secretKeyFile =
        new File(ctx.artifactoryHome.haAwareEtcDir, props.secretKeyFile)
    char[] passphrase = props.passphrase.toCharArray()

    afterCreate { item ->
        String itemKey = item.repoKey
        String itemPath = item.repoPath.path
        if (!itemKey.endsWith("-local")) {
            // Only local should be signed
            return
        } else if (item.isFolder()) {
            log.debug("Ignoring creation of new folder: ${itemKey}:${itemPath}")
            return
        } else if (itemPath.endsWith(".asc")) {
            log.debug("Ignoring deployment of signature file: ${itemKey}:${itemPath}")
            return
        }

        log.debug("Creating signature for: ${itemKey}:${itemPath}")

        ResourceStreamHandle content = repositories.getContent(item.repoPath)

        ByteArrayOutputStream signatureOut = new ByteArrayOutputStream()
        ArmoredOutputStream armoredSignatureOut = new ArmoredOutputStream(signatureOut)

        SimplePGPUtil.signFile(
            secretKeyFile, passphrase, content.getInputStream(), armoredSignatureOut)

        byte[] signatureBytes = signatureOut.toByteArray()
        ByteArrayInputStream signatureIn = new ByteArrayInputStream(signatureBytes)

        RepoPath signature = RepoPathFactory.create(itemKey, "${itemPath}.asc")
        StatusHolder status = repositories.deploy(signature, signatureIn)
        if (status.isError()) {
            log.info("Deploying signature: ${signature.repoKey}:${signature.path} failed with ${status.getLastError().getMessage()}")
        } else {
            log.info("Created and deployed signature: ${signature.repoKey}:${signature.path}")
        }
    }

    afterMove { item, targetRepoPath, properties ->
        handleCopyMoveAscs(item, targetRepoPath, true)
    }

    afterCopy { item, targetRepoPath, properties ->
        handleCopyMoveAscs(item, targetRepoPath, false)
    }
}

// Handle after copy/move events to follow with asc's
private void handleCopyMoveAscs(item, targetRepoPath, boolean move) {
    if (item.isFolder()) {
        log.debug("Ignoring copy/move of folder: ${item}")
        return
    }
    srcAscRepoPath = RepoPathFactory.create(item.repoKey, item.relPath + ".asc")
    if (repositories.exists(srcAscRepoPath)) {
        tgtAscRepoPath = RepoPathFactory.create(targetRepoPath.repoKey, targetRepoPath.path + ".asc")
        // Copy/move the asc to the target
        log.debug("Copy/move: ${srcAscRepoPath} to ${tgtAscRepoPath}")
        if (move) {
            // Note: with Artifactory 2.5.1.1 and below, folder-level moving when the folder contains ascs will cause an
            // error - an asc moved by the plugin does not exist for the folder children move iteration (RTFACT-4878)
            repositories.move(srcAscRepoPath, tgtAscRepoPath)
            srcParentDirRepoPath = srcAscRepoPath.parent
            while (!srcParentDirRepoPath.root && repositories.getChildren(srcParentDirRepoPath).empty) {
                repositories.delete(srcParentDirRepoPath)
                srcParentDirRepoPath = srcParentDirRepoPath.parent
            }
        } else {
            repositories.copy(srcAscRepoPath, tgtAscRepoPath)
        }
    }
}

/**
 * Simple PGP utility that takes a target file and produces a .asc-style signature for it.
 * Specifically designed not to depend on the Artifactory public API, such that it may
 * easily be tested and modified in a standalone way via copy-and-paste if need be.
 *
 * @author Chris Beams
 */
class SimplePGPUtil {
    /**
     * Sign the contents of the given input stream using the given secret key and
     * passphrase, writing the resulting signature to the given armored output stream,
     * i.e. suitable for .asc file suffix.
     *
     * @param secretKeyFile PGP secret key file that contains one and only one secret key
     * @param passphrase for the secret key
     * @param targetFileIn input stream of the file to be signed, closed by this method
     * @param signatureFileOut output stream of the signature file, closed by this method
     * @throws Exception if anything fails in the signing process
     */
    static void signFile(File secretKeyFile, char[] passphrase, InputStream targetFileIn,
                         ArmoredOutputStream signatureFileOut) throws Exception {
        if (secretKeyFile.exists() == false) {
            throw new IllegalArgumentException(
                "secretKeyFile does not exist: ${secretKeyFile.getPath()}")
        }

        FileInputStream secretKeyIn = new FileInputStream(secretKeyFile)
        BCPGOutputStream bcpgOut = null

        try {
            if (Security.getProvider("BC") == null) {
                Security.addProvider(new BouncyCastleProvider())
            }

            // as advertised, the .key file must have one and only one secret key within due
            // to the opinionated nature of the following call chain.
            def decodeStream = PGPUtil.getDecoderStream(secretKeyIn)
            def keyringColl = new PGPSecretKeyRingCollection(decodeStream, new BcKeyFingerprintCalculator())
            def pgpSec = keyringColl.getKeyRings().next().getSecretKeys().next()

            def digestCalc = new BcPGPDigestCalculatorProvider()
            def descBuilder = new BcPBESecretKeyDecryptorBuilder(digestCalc).build(passphrase)
            def pgpPrivKey = pgpSec.extractPrivateKey(descBuilder)

            def algorithm = pgpSec.getPublicKey().getAlgorithm()
            def signerBuilder = new BcPGPContentSignerBuilder(algorithm, PGPUtil.SHA1)
            def signatureGenerator = new PGPSignatureGenerator(signerBuilder)

            signatureGenerator.init(PGPSignature.BINARY_DOCUMENT, pgpPrivKey)

            bcpgOut = new BCPGOutputStream(signatureFileOut)

            int ch = 0
            while ((ch = targetFileIn.read()) >= 0) {
                signatureGenerator.update((byte) ch)
            }

            PGPSignature signature = signatureGenerator.generate()
            signature.encode(bcpgOut)
        } finally {
            secretKeyIn.close()
            targetFileIn.close()
            if (bcpgOut != null) {
                bcpgOut.close()
            }
            signatureFileOut.close()
        }
    }
}
