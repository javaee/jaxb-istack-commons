/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2014 Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.artifact.DefaultArtifact;

/**
 *
 * @author lukas
 */
final class DependencyResolver {

    public static DependencyResult resolve(CollectRequest collectRequest, List<RemoteRepository> remoteRepos,
            RepositorySystem repoSystem, RepositorySystemSession repoSession) throws DependencyResolutionException {
        DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, null);
        return repoSystem.resolveDependencies(repoSession, dependencyRequest);
    }

    public static DependencyResult resolve(org.apache.maven.model.Dependency dependency, List<RemoteRepository> remoteRepos,
            RepositorySystem repoSystem, RepositorySystemSession repoSession) throws DependencyResolutionException {
        CollectRequest collectRequest = new CollectRequest(createDependency(dependency), remoteRepos);
        return resolve(collectRequest, remoteRepos, repoSystem, repoSession);
    }

    private static Dependency createDependency(org.apache.maven.model.Dependency d) {
        Artifact artifact = new DefaultArtifact(d.getGroupId(), d.getArtifactId(), "pom", d.getVersion());
        return new Dependency(artifact, d.getScope(), d.isOptional(), null);
    }

}
