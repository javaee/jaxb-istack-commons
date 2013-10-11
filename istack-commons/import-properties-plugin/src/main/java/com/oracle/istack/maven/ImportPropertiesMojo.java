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
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.repository.RemoteRepository;

/**
 * The import-properties goal imports all properties from scope-import poms
 * (boms) and sets them as project properties.
 *
 * @author <a href="mailto:martin.grebac@oracle.com">Martin Grebac</a>
 */
@Mojo(name = "import-pom-properties", defaultPhase = LifecyclePhase.COMPILE, requiresDependencyResolution = ResolutionScope.NONE)
public class ImportPropertiesMojo extends AbstractMojo {

    /**
     * The Maven Project Object.
     */
    @Component
    protected MavenProject project;
    
    /**
     * The entry point to Aether, i.e. the component doing all the work.
     *
     * @since 2.3.1
     */
    @Component
    private RepositorySystem repoSystem;

    /**
     * The current repository/network configuration of Maven.
     *
     * @since 2.3.1
     */
    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    private RepositorySystemSession repoSession;

    /**
     * The project's remote repositories to use for the resolution of project
     * and its dependencies.
     *
     * @since 2.3.1
     */
    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true)
    private List<RemoteRepository> projectRepos;

    private Properties projectProperties = null;
    
    @Override
    public void execute() throws MojoExecutionException {
        try {
            projectProperties = project.getProperties();  

            MavenProject bomProject = project;
            while (bomProject != null && !bomProject.getArtifactId().endsWith("-bom")) {
                bomProject = bomProject.getParent();
            }
                        
            if (bomProject == null || !bomProject.getArtifactId().endsWith("-bom")) {
                getLog().warn("No '*-bom' project found in project hierarchy, using this project's pom for import search.");
                bomProject = project;
            }

            getLog().warn("Searching project: " + bomProject.getArtifactId());

            PropertyResolver resolver = new PropertyResolver(new CommonLogger(getLog()), projectProperties, repoSession, repoSystem, projectRepos);
            resolver.resolveProperties(bomProject);
            
        } catch (FileNotFoundException ex) {
            Logger.getLogger(ImportPropertiesMojo.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(ImportPropertiesMojo.class.getName()).log(Level.SEVERE, null, ex);
        } catch (XmlPullParserException ex) {
            Logger.getLogger(ImportPropertiesMojo.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

}
