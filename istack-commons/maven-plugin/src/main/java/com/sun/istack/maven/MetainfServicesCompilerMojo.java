/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2012 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package com.sun.istack.maven;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

/**
 * Goal which compiles META-INF/services files from various dependencies
 *
 * @author japod
 *
 * @goal metainf-services
 *
 * @phase generate-sources
 */
public class MetainfServicesCompilerMojo extends AbstractMojo {

    /**
     * Collection of ArtifactItems to take the META-INF/services from. (ArtifactItem contains groupId,
     * artifactId, version)
     *
     * @parameter
     * @required
     * @since 1.0
     */
    private List<ArtifactItem> artifactItems;
    /**
     * Collection of files from META-INF/services to compile
     *
     * @parameter
     * @required
     * @since 1.0
     */
    private List<String> providers;
    /**
     * Destination for the generated service registry files
     *
     * @parameter
     * @required
     * @since 1.0
     */
    private File destDir;
    /**
     * @component role="org.apache.maven.artifact.factory.ArtifactFactory"
     * @required
     * @readonly
     */
    protected ArtifactFactory artifactFactory;
    /**
     * @component role="org.apache.maven.artifact.resolver.ArtifactResolver"
     * @required
     * @readonly
     */
    protected ArtifactResolver artifactResolver;
    /**
     * @parameter expression="${project.remoteArtifactRepositories}"
     * @readonly
     * @required
     */
    protected java.util.List remoteRepositories;
    /**
     * @parameter expression="${localRepository}"
     * @readonly
     * @required
     */
    protected ArtifactRepository localRepository;

    public void execute() throws MojoExecutionException {
        getLog().info("About to compile META-INF/services files");
        getLog().info("Artifact Items = " + artifactItems);
        getLog().info("SPIs = " + providers);
        getLog().info("dest dir = " + destDir);
        File msDir = new File(destDir, "META-INF/services");
        msDir.mkdirs();
        for (String spi : providers) {
            PrintWriter registryWriter = null;
            try {
                File spiRegistry = new File(msDir, spi);
                if (spiRegistry.exists()) {
                    spiRegistry.delete();
                }
                spiRegistry.createNewFile();
                registryWriter = new PrintWriter(spiRegistry);
                try {
                    for (ArtifactItem ai : artifactItems) {
                        Artifact artifact;
                        artifact = artifactFactory.createExtensionArtifact(ai.getGroupId(), ai.getArtifactId(), VersionRange.createFromVersion(ai.getVersion()));
                        artifactResolver.resolve(artifact, remoteRepositories, localRepository);
                        ZipFile zipFile = new ZipFile(artifact.getFile());
                        final ZipEntry servicesEntry = zipFile.getEntry("META-INF/services/" + spi);
                        if (servicesEntry != null) {
                            final InputStream inputStream = zipFile.getInputStream(servicesEntry);
                            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                            while (reader.ready()) {
                                registryWriter.println(reader.readLine());
                            }
                        }
                    }
                } catch (ArtifactResolutionException ex) {
                    throw new MojoExecutionException("Can not resolve artifact!", ex);
                } catch (ArtifactNotFoundException ex) {
                    Logger.getLogger(MetainfServicesCompilerMojo.class.getName()).log(Level.SEVERE, null, ex);
                }
            } catch (IOException ex) {
                    throw new MojoExecutionException("Can not create spi registry file!", ex);
            } finally {
                registryWriter.close();
            }
        }
    }
}
