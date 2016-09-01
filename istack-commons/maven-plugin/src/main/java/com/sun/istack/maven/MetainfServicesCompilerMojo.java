/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2015 Oracle and/or its affiliates. All rights reserved.
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

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.impl.ArtifactResolver;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;

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

/**
 * Goal which compiles META-INF/services files from various dependencies
 *
 * @author japod
 *
 * Goal: metainf-services
 *
 * Phase: generate-sources
 */
@Mojo(name = "metainf-services", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class MetainfServicesCompilerMojo extends AbstractMojo {

    /**
     * Collection of ArtifactItems to take the META-INF/services from. (ArtifactItem contains groupId,
     * artifactId, version)
     *
     * parameter
     * required
     * @since 1.0
     */
    private List<ArtifactItem> artifactItems;
    /**
     * Collection of files from META-INF/services to compile
     *
     * parameter
     * required
     * @since 1.0
     */
    private List<String> providers;
    /**
     * Destination for the generated service registry files
     *
     * parameter
     * required
     * @since 1.0
     */
    private File destDir;

    /**
     * component role="org.apache.maven.artifact.resolver.ArtifactResolver"
     * required
     * readonly
     */
    protected ArtifactResolver artifactResolver;
    /**
     * parameter expression="${project.remoteArtifactRepositories}"
     * readonly
     * required
     */
    protected List<RemoteRepository> remoteRepositories;

    /**
     * The current repository/network configuration of Maven.
     *
     * @parameter default-value="${repositorySystemSession}"
     * @readonly
     */
    private RepositorySystemSession repoSession;

    /**
     * parameter expression="${localRepository}"
     * readonly
     * required
     */
    protected ArtifactRepository localRepository;

    @Override
    public void execute() throws MojoExecutionException {
        getLog().info("About to compile META-INF/services files");
        getLog().info("Artifact Items = " + artifactItems);
        getLog().info("SPIs = " + providers);
        getLog().info("dest dir = " + destDir);
        File msDir = new File(destDir, "META-INF/services");
        boolean created =msDir.mkdirs();
        if (!created) {
            Logger.getLogger(MetainfServicesCompilerMojo.class.getName()).log(Level.FINE, "Cannot create directory: {0}", msDir);
        }
        for (String spi : providers) {
            PrintWriter registryWriter = null;
            try {
                File spiRegistry = new File(msDir, spi);
                if (spiRegistry.exists()) {
                    boolean deleted = spiRegistry.delete();
                    if (!deleted) {
                        Logger.getLogger(MetainfServicesCompilerMojo.class.getName()).log(Level.FINE, "Cannot delete file: {0}", spiRegistry);
                    }
                }
                created = spiRegistry.createNewFile();
                if (!created) {
                    Logger.getLogger(MetainfServicesCompilerMojo.class.getName()).log(Level.FINE, "Cannot create file: {0}", spiRegistry);
                }
                registryWriter = new PrintWriter(spiRegistry);
                ZipFile zipFile = null;
                try {
                    BufferedReader reader = null;
                    InputStreamReader isReader = null;
                    for (ArtifactItem ai : artifactItems) {
                        Artifact artifact;
                        artifact = new DefaultArtifact(ai.getGroupId(), ai.getArtifactId(), null, ai.getVersion());
//                        artifact = artifactFactory.createExtensionArtifact(ai.getGroupId(), ai.getArtifactId(), VersionRange.createFromVersion(ai.getVersion()));

                        ArtifactRequest request = new ArtifactRequest();
                        request.setArtifact( artifact );
                        request.setRepositories(remoteRepositories);
                        artifactResolver.resolveArtifact(repoSession, request);
                        zipFile = new ZipFile(artifact.getFile());
                        final ZipEntry servicesEntry = zipFile.getEntry("META-INF/services/" + spi);
                        if (servicesEntry != null) {
                            try {
                                final InputStream inputStream = zipFile.getInputStream(servicesEntry);
                                isReader = new InputStreamReader(inputStream);
                                reader = new BufferedReader(isReader);
                                while (reader.ready()) {
                                    registryWriter.println(reader.readLine());
                                }
                            } finally {
                                if (reader != null) {
                                    reader.close();
                                }
                                if (isReader != null) {
                                    isReader.close();
                                }
                            }
                        }
                    }
                } catch (ArtifactResolutionException ex) {
                    Logger.getLogger(MetainfServicesCompilerMojo.class.getName()).log(Level.SEVERE, null, ex);
                    throw new MojoExecutionException("Can not resolve artifact!", ex);
                } finally {
                    if (zipFile != null) {
                        zipFile.close();
                    }
                }
            } catch (IOException ex) {
                    throw new MojoExecutionException("Can not create spi registry file!", ex);
            } finally {
                if (registryWriter != null) {
                    registryWriter.close();
                }
            }
        }
    }
}
