/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2016 Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.istack.maven;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Martin Grebac
 */
public class PropertyResolver {
    
    private final CommonLogger logger;
    
    private final MavenXpp3Reader mavenreader = new MavenXpp3Reader();

    private final Properties properties;

    private final RepositorySystemSession repoSession;
    
    private final RepositorySystem repoSystem;
    
    private final List<RemoteRepository> pluginRepos;
    
    PropertyResolver(CommonLogger logger, Properties properties, RepositorySystemSession session, 
            RepositorySystem repoSystem, List<RemoteRepository> pluginRepositories) {
        this.logger = logger;
        this.properties = properties;
        this.repoSession = session;
        this.repoSystem = repoSystem;
        this.pluginRepos = pluginRepositories;
    }
            
    /**
     *
     * @param project maven project
     * @throws FileNotFoundException properties not found
     * @throws IOException IO error
     * @throws XmlPullParserException error parsing xml
     */
    public void resolveProperties(MavenProject project) throws FileNotFoundException, IOException, XmlPullParserException {
        logger.info("Resolving properties for " + project.getGroupId() + ":" + project.getArtifactId());
 
        Model model = null;
        FileReader reader;
        try {
            reader = new FileReader(project.getFile());
            model = mavenreader.read(reader);
        } catch (FileNotFoundException ex) {
             Logger.getLogger(ImportPropertiesMojo.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(ImportPropertiesMojo.class.getName()).log(Level.SEVERE, null, ex);
        }
        MavenProject loadedProject = new MavenProject(model);

        DependencyManagement dm = loadedProject.getDependencyManagement();        
        if (dm == null) {
            logger.warn("No dependency management section found in: "  + loadedProject.getGroupId() + ":" + loadedProject.getArtifactId());
            return;
        }

        List<Dependency> depList = dm.getDependencies();

        DependencyResult result;
        for (Dependency d : depList) {
            if ("import".equals(d.getScope())) {
                try {
                    String version = d.getVersion();
                    logger.info("Imported via import-scope: " + d.getGroupId() + ":" + d.getArtifactId() + ":" + version);
                    if (version.contains("$")) {
                        version = properties.getProperty(version.substring(version.indexOf('{')+1, version.lastIndexOf('}')));
                        logger.info("Imported version resolved to: " + version);
                    }
                    d.setVersion(version);
                    d.setType("pom");
                    d.setClassifier("pom");
                    result = DependencyResolver.resolve(d, pluginRepos, repoSystem, repoSession);                                        
                    Artifact a = result.getArtifactResults().get(0).getArtifact();
                    reader = new FileReader(a.getFile());
                    Model m = mavenreader.read(reader);
                    MavenProject p = new MavenProject(m);
                    p.setFile(a.getFile());
                    for (Map.Entry<Object,Object> e : p.getProperties().entrySet()) {
                        logger.info("Setting property: " + (String)e.getKey() + ":" + (String)e.getValue());
                        properties.setProperty((String)e.getKey(), (String)e.getValue());
                    }

                    resolveProperties(p);
                } catch (DependencyResolutionException ex) {
                    Logger.getLogger(ImportPropertiesMojo.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }        
    }

    
}
