/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2005-2013 Oracle and/or its affiliates. All rights reserved.
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
import java.lang.reflect.Field;
import java.net.Authenticator;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import static org.testng.Assert.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

/**
 * @author Rama Pulavarthi
 * @author Lukas Jungmann
 */
public class DefaultAuthenticatorTest {

    private static final Logger logger = Logger.getLogger(DefaultAuthenticatorTest.class.getName());

    public DefaultAuthenticatorTest() {
    }

    private static class MyAuthenticator extends DefaultAuthenticator {

        private String requestingURL;

        @Override
        protected URL getRequestingURL() {
            try {
                return new URL(requestingURL);
            } catch (MalformedURLException e) {
                logger.log(Level.SEVERE, null, e);
            }
            return null;
        }

        void setRequestingURL(String url) {
            requestingURL = url;
        }
    }

    @AfterMethod
    public void after() {
        clearTestAuthenticator();
    }

    @Test
    public void testDefaultAuth() throws Exception {
        URL url = getResourceAsUrl("com/sun/istack/tools/.auth");
        MyAuthenticator ma = createTestAuthenticator();
        ma.setRequestingURL("http://foo.com/myservice?wsdl");
        assertNull(DefaultAuthenticator.getCurrentAuthenticator());
        assertEquals(0, getCounter());
        try {
            DefaultAuthenticator da = DefaultAuthenticator.getAuthenticator();
            assertEquals(ma, da);
            assertEquals(1, getCounter());
            da.setAuth(new File(url.toURI()), null);
            PasswordAuthentication pa = da.getPasswordAuthentication();
            assertTrue(pa != null && pa.getUserName().equals("duke") && Arrays.equals(pa.getPassword(), "test".toCharArray()));
        } finally {
            DefaultAuthenticator.reset();
            assertNotEquals(ma, DefaultAuthenticator.getCurrentAuthenticator());
            assertEquals(0, getCounter());
        }
    }

    @Test
    public void testGetDefaultAuth() throws Exception {
        Authenticator orig = DefaultAuthenticator.getCurrentAuthenticator();
        try {
            DefaultAuthenticator da = DefaultAuthenticator.getAuthenticator();
            assertFalse(da.equals(orig));
            assertEquals(1, getCounter());
            Authenticator auth = DefaultAuthenticator.getCurrentAuthenticator();
            assertNotNull(auth);
            assertEquals(da, auth);
        } finally {
            DefaultAuthenticator.reset();
            assertEquals(orig, DefaultAuthenticator.getCurrentAuthenticator());
            assertEquals(0, getCounter());
        }
    }

    @Test
    public void testJaxWs_1101() throws Exception {
        URL url = getResourceAsUrl("com/sun/istack/tools/auth_test.resource");
        MyAuthenticator ma = createTestAuthenticator();

        try {
            DefaultAuthenticator da = DefaultAuthenticator.getAuthenticator();
            assertEquals(1, getCounter());
            assertEquals(ma, da);
            da.setAuth(new File(url.toURI()), null);

            ma.setRequestingURL("http://server1.myserver.com/MyService/Service.svc?wsdl");
            PasswordAuthentication pa = da.getPasswordAuthentication();
            assertEquals("user", pa.getUserName());
            assertEquals(")/_@B8M)gDw", new String(pa.getPassword()));

            ma.setRequestingURL("http://server1.myserver.com/MyService/Service.svc?xsd=xsd0");
            pa = da.getPasswordAuthentication();
            assertEquals("user", pa.getUserName());
            assertEquals(")/_@B8M)gDw", new String(pa.getPassword()));

            ma.setRequestingURL("http://server1.myserver.com/MyService/Service.svc");
            pa = da.getPasswordAuthentication();
            assertEquals("user", pa.getUserName());
            assertEquals(")/_@B8M)gDw", new String(pa.getPassword()));

            ma.setRequestingURL("http://server1.myserver.com/encoded/MyService/Service.svc?wsdl");
            pa = da.getPasswordAuthentication();
            assertEquals("user2", pa.getUserName());
            assertEquals(")/_@B8M)gDw", new String(pa.getPassword()));
        } finally {
            DefaultAuthenticator.reset();
            assertEquals(0, getCounter());
        }
    }

    private static URL getResourceAsUrl(String resourceName) throws RuntimeException {
        URL input = Thread.currentThread().getContextClassLoader().getResource(resourceName);
        if (input == null) {
            throw new RuntimeException("Failed to find resource \"" + resourceName + "\"");
        }
        return input;
    }

    private MyAuthenticator createTestAuthenticator() {
        Field f1 = null;
        try {
            f1 = DefaultAuthenticator.class.getDeclaredField("instance");
            f1.setAccessible(true);
            MyAuthenticator auth = new MyAuthenticator();
            f1.set(null, auth);
            return auth;
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        } finally {
            if (f1 != null) {
                f1.setAccessible(false);
            }
        }
        return null;
    }

    private void clearTestAuthenticator() {
        Field f1, f2 = f1 = null;
        try {
            f1 = DefaultAuthenticator.class.getDeclaredField("instance");
            f1.setAccessible(true);
            MyAuthenticator auth = new MyAuthenticator();
            f1.set(null, null);
            f2 = DefaultAuthenticator.class.getDeclaredField("counter");
            f2.setAccessible(true);
            f2.setInt(null, 0);
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        } finally {
            if (f1 != null) {
                f1.setAccessible(false);
            }
            if (f2 != null) {
                f2.setAccessible(false);
            }
        }
    }

    private int getCounter() {
        Field f = null;
        try {
            f = DefaultAuthenticator.class.getDeclaredField("counter");
            f.setAccessible(true);
            return f.getInt(null);
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        } finally {
            if (f != null) {
                f.setAccessible(false);
            }
        }
        return -1;
    }
}