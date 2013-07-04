/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved.
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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Repository;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.internal.MavenServiceLocator;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.connector.wagon.WagonProvider;
import org.sonatype.aether.connector.wagon.WagonRepositoryConnectorFactory;
import org.sonatype.aether.impl.internal.DefaultServiceLocator;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.spi.connector.RepositoryConnectorFactory;

/**
 *
 * @author Martin Grebac
 */
@Component(role = AbstractMavenLifecycleParticipant.class, hint="import-properties")
public class ImportPropertiesExtension extends AbstractMavenLifecycleParticipant {

    @Requirement
    private Logger logger;
    
    private Properties projectProperties = null;
    
    @Override
    public void afterProjectsRead(MavenSession session) {
        MavenProject project = session.getCurrentProject();
        try {
            projectProperties = project.getProperties();  
            
            MavenProject bomProject = project;
            while (!bomProject.getArtifactId().endsWith("-bom")) {
                bomProject = bomProject.getParent();
            }
            
            if (!bomProject.getArtifactId().endsWith("-bom")) {
                logger.warn("No BOM found in project hierarchy.");
                return;
            }

            logger.warn("Found BOM project: " + bomProject.getArtifactId());

            DefaultServiceLocator locator = new MavenServiceLocator();
            locator.setServices( WagonProvider.class, new ManualWagonProvider() );
            locator.addService( RepositoryConnectorFactory.class, WagonRepositoryConnectorFactory.class );

            RepositorySystem repoSystem = locator.getService( RepositorySystem.class );

            List<Repository> repos = project.getRepositories();
            List<RemoteRepository> remoteRepos = new ArrayList();
            for (Repository r : repos) {
                RemoteRepository rr = new RemoteRepository(r.getId(), null, r.getUrl());
                remoteRepos.add(rr);
            }
            bomProject.getRepositories();
            PropertyResolver resolver = new PropertyResolver(
                    new CommonLogger(logger), 
                    projectProperties, 
                    session.getRepositorySession(), repoSystem, remoteRepos);
            resolver.resolveProperties(bomProject);
            
        } catch (FileNotFoundException ex) {
            java.util.logging.Logger.getLogger(ImportPropertiesMojo.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            java.util.logging.Logger.getLogger(ImportPropertiesMojo.class.getName()).log(Level.SEVERE, null, ex);
        } catch (XmlPullParserException ex) {
            java.util.logging.Logger.getLogger(ImportPropertiesMojo.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

}