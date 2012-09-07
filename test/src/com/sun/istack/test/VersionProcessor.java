/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2010 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
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

package com.sun.istack.test;

import org.dom4j.Document;
import org.dom4j.Element;

import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * Represents a range of versions.
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public final class VersionProcessor {
    /**
     * This test is only applicable to the RI of this version or later.
     * can be null.
     */
    private final VersionNumber since;

    /**
     * This test is only applicable to the RI of this version or younger.
     * can be null.
     */
    private final VersionNumber until;

    /**
     * This test shall be excluded from the RI of versions listed here.
     */
    private final Set<Object> excludeVersions;

    /**
     * Special version number constant to represent ALL in
     * {@link #excludeVersions}.
     */
    private static final Object ALL_VERSION = new Object();

    /**
     * Creates a default {@link VersionProcessor} that accepts
     * any version.
     */
    private VersionProcessor() {
        since = null;
        until = null;
        excludeVersions = null;
    }

    public VersionProcessor( String sinceValue, String untilValue, String excludeFromValue ) {
        if( sinceValue!=null )
            since = new VersionNumber( sinceValue );
        else
            since = null;

        if( untilValue!=null )
            until = new VersionNumber( untilValue );
        else
            until = null;

        if( excludeFromValue!=null ) {
            excludeVersions = new HashSet<Object>();
            String v = excludeFromValue.trim();
            if(v.equals("all")) {
                excludeVersions.add(ALL_VERSION);
            } else {
                StringTokenizer tokens = new StringTokenizer( v );
                while(tokens.hasMoreTokens())
                    excludeVersions.add( new VersionNumber( tokens.nextToken() ) );
            }
        } else
            excludeVersions = null;
    }

    public VersionProcessor( Document testSpecMeta ) {
        this(testSpecMeta.getRootElement());
    }

    public VersionProcessor( Element e ) {
        this(
            e.attributeValue("since",null),
            e.attributeValue("until",null),
            e.attributeValue("excludeFrom",null) );
    }

    /**
     * Checks if the test is valid against the JAXB RI of
     * the specified version.
     */
    public boolean isApplicable(VersionNumber v) {
        if( excludeVersions!=null ) {
            if( excludeVersions.contains(ALL_VERSION)
            ||  excludeVersions.contains(v) )
                return false;
        }

        if(since!=null && since.isNewerThan(v))
            return false;

        if(until!=null && v.isNewerThan(until))
            return false;

        return true;
    }

    /**
     * Default {@link VersionProcessor} that accepts any version.
     */
    public static final VersionProcessor DEFAULT = new VersionProcessor();
}
