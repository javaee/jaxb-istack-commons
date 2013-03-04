/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2013 Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.net.Authenticator;
import java.net.Authenticator.RequestorType;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.xml.sax.Locator;
import org.xml.sax.helpers.LocatorImpl;

/**
 * @author Vivek Pandey
 * @author Lukas Jungmann
 */
public class DefaultAuthenticator extends Authenticator {

    private static DefaultAuthenticator instance;
    private static Authenticator systemAuthenticator = getCurrentAuthenticator();
    private String proxyUser;
    private String proxyPasswd;
    private final List<AuthInfo> authInfo = new ArrayList<AuthInfo>();
    private static int counter = 0;

    DefaultAuthenticator() {
        //try undocumented but often used properties
        if (System.getProperty("http.proxyUser") != null) {
            proxyUser = System.getProperty("http.proxyUser");
        } else {
            proxyUser = System.getProperty("proxyUser");
        }
        if (System.getProperty("http.proxyPassword") != null) {
            proxyPasswd = System.getProperty("http.proxyPassword");
        } else {
            proxyPasswd = System.getProperty("proxyPassword");
        }
    }

    public static synchronized DefaultAuthenticator getAuthenticator() {
        if (instance == null) {
            instance = new DefaultAuthenticator();
            Authenticator.setDefault(instance);
        }
        counter++;
        return instance;
    }

    public static synchronized void reset() {
        --counter;
        if (instance != null && counter == 0) {
            Authenticator.setDefault(systemAuthenticator);
        }
    }

    @Override
    protected PasswordAuthentication getPasswordAuthentication() {
        //If user sets proxy user and passwd and the RequestType is from proxy server then create
        // PasswordAuthentication using proxyUser and proxyPasswd;
        if ((getRequestorType() == RequestorType.PROXY) && proxyUser != null && proxyPasswd != null) {
            return new PasswordAuthentication(proxyUser, proxyPasswd.toCharArray());
        }
        for (AuthInfo auth : authInfo) {
            if (auth.matchingHost(getRequestingURL())) {
                return new PasswordAuthentication(auth.getUser(), auth.getPassword().toCharArray());
            }
        }
        return null;
    }

    /**
     * Proxy authorization string in form of username:password.
     *
     * @param proxyAuth
     */
    public void setProxyAuth(String proxyAuth) {
        if (proxyAuth == null) {
            this.proxyUser = null;
            this.proxyPasswd = null;
        } else {
            int i = proxyAuth.indexOf(':');
            if (i < 0) {
                this.proxyUser = proxyAuth;
                this.proxyPasswd = "";
            } else if (i == 0) {
                this.proxyUser = "";
                this.proxyPasswd = proxyAuth.substring(1);
            } else {
                this.proxyUser = proxyAuth.substring(0, i);
                this.proxyPasswd = proxyAuth.substring(i + 1);
            }
        }
    }

    public void setAuth(File f, Receiver l) {
        Receiver listener = l == null ? new DefaultRImpl() : l;
        BufferedReader in = null;
        FileInputStream fi = null;
        InputStreamReader is = null;
        try {
            String text;
            LocatorImpl locator = new LocatorImpl();
            locator.setSystemId(f.getAbsolutePath());
            try {
                fi = new FileInputStream(f);
                is = new InputStreamReader(fi, "UTF-8");
                in = new BufferedReader(is);
            } catch (UnsupportedEncodingException e) {
                listener.onError(e, locator);
                return;
            } catch (FileNotFoundException e) {
                listener.onError(e, locator);
                return;
            }
            try {
                int lineno = 1;
                locator.setSystemId(f.getCanonicalPath());
                while ((text = in.readLine()) != null) {
                    locator.setLineNumber(lineno++);
                    //ignore empty lines and treat those starting with '#' as comments
                    if ("".equals(text.trim()) || text.startsWith("#")) {
                        continue;
                    }
                    try {
                        AuthInfo ai = parseLine(text);
                        authInfo.add(ai);
                    } catch (Exception e) {
                        listener.onParsingError(text, locator);
                    }
                }
            } catch (IOException e) {
                listener.onError(e, locator);
                Logger.getLogger(DefaultAuthenticator.class.getName()).log(Level.SEVERE, e.getMessage(), e);
            }
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
                if (is != null) {
                    is.close();
                }
                if (fi != null) {
                    fi.close();
                }
            } catch (IOException ex) {
                Logger.getLogger(DefaultAuthenticator.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    private AuthInfo parseLine(String text) throws Exception {
        URL url;
        try {
            url = new URL(text);
        } catch (MalformedURLException mue) {
            //possible cause of this can be that password contains
            //character which has to be encoded in URL,
            //such as '@', ')', '#' and few others
            //so try to recreate the URL with encoded string
            //between 2nd ':' and last '@'
            int i = text.indexOf(':', text.indexOf(':') + 1) + 1;
            int j = text.lastIndexOf('@');
            String encodedUrl =
                    text.substring(0, i)
                    + URLEncoder.encode(text.substring(i, j), "UTF-8")
                    + text.substring(j);
            url = new URL(encodedUrl);
        }

        String authinfo = url.getUserInfo();

        if (authinfo != null) {
            int i = authinfo.indexOf(':');

            if (i >= 0) {
                String user = authinfo.substring(0, i);
                String password = authinfo.substring(i + 1);
                return new AuthInfo(
                        new URL(url.getProtocol(), url.getHost(), url.getPort(), url.getFile()),
                        user, URLDecoder.decode(password, "UTF-8"));
            }
        }
        throw new Exception();
    }

    static Authenticator getCurrentAuthenticator() {
        final Field f = getTheAuthenticator();
        if (f == null) {
            return null;
        }

        try {
            AccessController.doPrivileged(new PrivilegedAction<Void>() {
                @Override
                public Void run() {
                    f.setAccessible(true);
                    return null;
                }
            });
            return (Authenticator) f.get(null);
        } catch (Exception ex) {
            return null;
        } finally {
            AccessController.doPrivileged(new PrivilegedAction<Void>() {
                @Override
                public Void run() {
                    f.setAccessible(false);
                    return null;
                }
            });
        }
    }

    private static Field getTheAuthenticator() {
        try {
            return Authenticator.class.getDeclaredField("theAuthenticator");
        } catch (Exception ex) {
            return null;
        }
    }

    public static interface Receiver {

        void onParsingError(String line, Locator loc);

        void onError(Exception e, Locator loc);
    }

    private static class DefaultRImpl implements Receiver {

        @Override
        public void onParsingError(String line, Locator loc) {
            System.err.println(getLocationString(loc) + ": " + line);
        }

        @Override
        public void onError(Exception e, Locator loc) {
            System.err.println(getLocationString(loc) + ": " + e.getMessage());
            Logger.getLogger(DefaultAuthenticator.class.getName()).log(Level.SEVERE, e.getMessage(), e);
        }

        private String getLocationString(Locator l) {
            return "[" + l.getSystemId() + "#" + l.getLineNumber() + "]";
        }
    }

    /**
     * Represents authorization information needed by
     * {@link DefaultAuthenticator} to authenticate access to remote resources.
     *
     * @author Vivek Pandey
     * @author Lukas Jungmann
     */
    final static class AuthInfo {

        private final String user;
        private final String password;
        private final Pattern urlPattern;

        public AuthInfo(URL url, String user, String password) {
            String u = url.toExternalForm().replaceFirst("\\?", "\\\\?");
            this.urlPattern = Pattern.compile(u.replace("*", ".*"), Pattern.CASE_INSENSITIVE);
            this.user = user;
            this.password = password;
        }

        public String getUser() {
            return user;
        }

        public String getPassword() {
            return password;
        }

        /**
         * Returns if the requesting host and port are associated with this
         * {@link AuthInfo}
         */
        public boolean matchingHost(URL requestingURL) {
            return urlPattern.matcher(requestingURL.toExternalForm()).matches();
        }
    }
}
