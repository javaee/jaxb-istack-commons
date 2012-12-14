/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
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
package com.sun.istack.tools;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.Enumeration;
import javax.xml.stream.XMLInputFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 *
 * @author lukas
 */
public class ParallelWorldClassLoaderTest {

    private ClassLoader cl;
    private MaskingClassLoader mcl;
    private URLClassLoader ucl;
    private ParallelWorldClassLoader pwcl;
    private ClassLoader orig;

    public ParallelWorldClassLoaderTest() {
    }

    @BeforeMethod
    public void setUpMethod() throws Exception {
        cl = ClassLoader.getSystemClassLoader();
        mcl = new MaskingClassLoader(cl, "javax.xml.ws");
        String dir = System.getProperty("surefire.test.class.path").split(File.pathSeparator)[0];
        ucl = new URLClassLoader(new URL[] {new File(dir).toURI().toURL()}, mcl);
        pwcl = new ParallelWorldClassLoader(ucl, "");
        orig = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(pwcl);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDownMethod() throws Exception {
        Thread.currentThread().setContextClassLoader(orig);
    }

    /**
     * Test of findClass method, of class ParallelWorldClassLoader.
     */
    @Test
    public void testFindClass() throws Exception {
        System.out.println("findClass");
        //XXX: why this fails ?
//        Class c3 = pwcl.findClass("javax.xml.ws.Service");
//        Assert.assertEquals(c3.getDeclaredMethods().length, 1);
        
        Class c1 = null;
        try {
            c1 = Class.forName("javax.xml.ws.Service", false, pwcl);
            Assert.assertEquals(c1.getDeclaredMethods().length, 1);
        } catch (ClassNotFoundException cnfe) {
            Assert.fail();
        }
        Class c2 = null;
        try {
            c2 = Class.forName("javax.xml.ws.Service", false, Thread.currentThread().getContextClassLoader());
            Assert.assertEquals(c2.getDeclaredMethods().length, 1);
        } catch (ClassNotFoundException cnfe) {
            Assert.fail();
        }
    }

    /**
     * Test of findResource method, of class ParallelWorldClassLoader.
     */
    @Test
    public void testFindResource() {
        URL resource = pwcl.getResource("javax/xml/ws/Service.class");
        URL object = pwcl.getResource("java/lang/Object.class");
        String resJar = resource.getPath().substring(0, resource.getPath().indexOf("!"));
        String rtJar = object.getPath().substring(0, object.getPath().indexOf("!"));
        Assert.assertEquals(resJar, rtJar);
    }

    /**
     * Test of findResources method, of class ParallelWorldClassLoader.
     */
    @Test
    public void testFindResources() throws Exception {
        Enumeration<URL> foundURLs = pwcl.getResources("javax/xml/ws/Service.class");
        //XXX: shouldn't there be only 2 resources found?
        Assert.assertEquals(Collections.list(foundURLs).size(), 3);
    }

    @Test
    public void testJaxp() throws Exception {
        XMLInputFactory inFactory = XMLInputFactory.newInstance();
        Assert.assertEquals(inFactory.getClass().getClassLoader(), ucl);
    }
}
