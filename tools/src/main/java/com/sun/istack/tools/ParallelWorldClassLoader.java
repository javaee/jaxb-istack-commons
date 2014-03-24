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

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.MalformedURLException;
import java.net.URLConnection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Load classes/resources from a side folder, so that
 * classes of the same package can live in a single jar file.
 *
 * <p>
 * For example, with the following jar file:
 * <pre>
 *  /
 *  +- foo
 *     +- X.class
 *  +- bar
 *     +- X.class
 * </pre>
 * <p>
 * {@link ParallelWorldClassLoader}("foo/") would load <tt>X.class<tt> from
 * <tt>/foo/X.class</tt> (note that X is defined in the root package, not
 * <tt>foo.X</tt>.
 *
 * <p>
 * This can be combined with  {@link MaskingClassLoader} to mask classes which are loaded by the parent
 * class loader so that the child class loader
 * classes living in different folders are loaded
 * before the parent class loader loads classes living the jar file publicly
 * visible
 * For example, with the following jar file:
 * <pre>
 *  /
 *  +- foo
 *     +- X.class
 *  +- bar
 *     +-foo
 *        +- X.class
 * </pre>
 * <p>
 * {@link ParallelWorldClassLoader}(MaskingClassLoader.class.getClassLoader()) would load <tt>foo.X.class<tt> from
 * <tt>/bar/foo.X.class</tt> not the <tt>foo.X.class<tt> in the publicly visible place in the jar file, thus
 * masking the parent classLoader from loading the class from  <tt>foo.X.class<tt>
 * (note that X is defined in the  package foo, not
 * <tt>bar.foo.X</tt>.
 *
 * @author Kohsuke Kawaguchi
 */
public class ParallelWorldClassLoader extends ClassLoader implements Closeable {

    /**
     * Strings like "prefix/", "abc/", or "" to indicate
     * classes should be loaded normally.
     */
    private final String prefix;
    private final Set<JarFile> jars;

    public ParallelWorldClassLoader(ClassLoader parent,String prefix) {
        super(parent);
        this.prefix = prefix;
        jars = Collections.synchronizedSet(new HashSet<JarFile>());
    }

    protected Class findClass(String name) throws ClassNotFoundException {
        
        StringBuffer sb = new StringBuffer(name.length()+prefix.length()+6);
        sb.append(prefix).append(name.replace('.','/')).append(".class");

        URL u  = getParent().getResource(sb.toString());
        if (u == null) {
            throw new ClassNotFoundException(name);
        }

        InputStream is = null;
        URLConnection con = null;

        try {
            con = u.openConnection();
            is = con.getInputStream();
        } catch (IOException ioe) {
            throw new ClassNotFoundException(name);
        }
        
        if (is==null)
            throw new ClassNotFoundException(name);

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int len;
            while((len=is.read(buf))>=0)
                baos.write(buf,0,len);

            buf = baos.toByteArray();
            int packIndex = name.lastIndexOf('.');
            if (packIndex != -1) {
                String pkgname = name.substring(0, packIndex);
                // Check if package already loaded.
                Package pkg = getPackage(pkgname);
                if (pkg == null) {
                    definePackage(pkgname, null, null, null, null, null, null, null);
                }
            }
            return defineClass(name,buf,0,buf.length);
        } catch (IOException e) {
            throw new ClassNotFoundException(name,e);
        } finally {
            try {
                if (con != null && con instanceof JarURLConnection) {
                    jars.add(((JarURLConnection) con).getJarFile());
                }
            } catch (IOException ioe) {
                //ignore
            }
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ioe) {
                    //ignore
                }
            }
        }
    }

    @Override
    protected URL findResource(String name) {
        URL u = getParent().getResource(prefix + name);
        if (u != null) {
            try {
                jars.add(new JarFile(new File(toJarUrl(u).toURI())));
            } catch (URISyntaxException ex) {
                Logger.getLogger(ParallelWorldClassLoader.class.getName()).log(Level.WARNING, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(ParallelWorldClassLoader.class.getName()).log(Level.WARNING, null, ex);
            } catch (ClassNotFoundException ex) {
                //ignore - not a jar
            }
        }
        return u;
    }

    @Override
    protected Enumeration<URL> findResources(String name) throws IOException {
        Enumeration<URL> en = getParent().getResources(prefix + name);
        while (en.hasMoreElements()) {
            try {
                jars.add(new JarFile(new File(toJarUrl(en.nextElement()).toURI())));
            } catch (URISyntaxException ex) {
                //should not happen
                Logger.getLogger(ParallelWorldClassLoader.class.getName()).log(Level.WARNING, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(ParallelWorldClassLoader.class.getName()).log(Level.WARNING, null, ex);
            } catch (ClassNotFoundException ex) {
                //ignore - not a jar
            }
        }
        return en;
    }

    public synchronized void close() throws IOException {
        for (JarFile jar : jars) {
            jar.close();
        }
    }
    
    /**
     * Given the URL inside jar, returns the URL to the jar itself.
     */
    public static URL toJarUrl(URL res) throws ClassNotFoundException, MalformedURLException {
        String url = res.toExternalForm();
        if(!url.startsWith("jar:"))
            throw new ClassNotFoundException("Loaded outside a jar "+url);
        url = url.substring(4); // cut off jar:
        url = url.substring(0,url.lastIndexOf('!'));    // cut off everything after '!'
        url = url.replaceAll(" ", "%20"); // support white spaces in path
        return new URL(url);
    }
}
