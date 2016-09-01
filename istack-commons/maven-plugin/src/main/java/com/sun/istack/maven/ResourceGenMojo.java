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

import com.sun.codemodel.CodeWriter;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JClassAlreadyExistsException;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JPackage;
import com.sun.codemodel.writer.FileCodeWriter;
import com.sun.codemodel.writer.FilterCodeWriter;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.model.fileset.FileSet;
import org.apache.maven.shared.model.fileset.util.FileSetManager;
import org.codehaus.plexus.util.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;


/**
 * Goal which generates source files from resource bundles.
 * You can then refer to resources as methods rather than hard-coding string constants.
 *
 * @author Lukas Jungmann
 * @author Jakub Podlesak
 * @author Kohsuke Kawaguchi
 *
 * Goal: rs-gen
 * RequiresProject: false
 * Phase: process-sources
 */
@Mojo(name = "rs-gen", requiresProject = false, defaultPhase = LifecyclePhase.PROCESS_SOURCES)
public class ResourceGenMojo extends AbstractMojo {

    /**
     * Location of the destination directory.
     * @parameter expression="${destDir}" default-value="${project.build.directory}/generated-sources/resources
     * @required
     */
    @Parameter
    private File destDir;

    /**
     * File set of the properties files to be processed.
     * @parameter
     */
    @Parameter
    private FileSet resources;

    /**
     * Directory with properties files to be processed from the command line.
     * @parameter expression="${resources}"
     * @since 2.12
     */
    @Parameter
    private String cliResource;

    /**
     * package to be used for the localization utility classes
     * @parameter expression="${localizationUtilitiesPkgName}" default-value="com.sun.istack.localization"
     */
    @Parameter
    private String localizationUtilitiesPkgName;

    /**
     * @parameter expression="${license}"
     * @since 2.12
     */
    @Parameter
    private File license;

    /**
     * @parameter expression="${encoding}" default-value="${project.build.sourceEncoding}"
     * @since 2.12
     */
    @Parameter
    private String encoding;

    /**
     * @parameter expression="${project}"
     * @readonly
     * @since 2.12
     */
    private MavenProject project;
    
    public void execute() throws MojoExecutionException {

        if(resources == null && cliResource == null) {
            throw new MojoExecutionException("No resource file is specified");
        }
        if(destDir == null) {
            throw new MojoExecutionException("No destdir attribute is specified");
        }
        
        if(localizationUtilitiesPkgName == null) {
            localizationUtilitiesPkgName = "com.sun.istack.localization";
        }

        if (!destDir.exists() && !destDir.mkdirs()) {
                throw new MojoExecutionException("Cannot create destdir");
        }

        if (!destDir.canWrite()) {
            throw new MojoExecutionException("Cannot write to destdir");
        }

        if (StringUtils.isEmpty(encoding)) {
            encoding =  System.getProperty("file.encoding");
            getLog().warn("File encoding has not been set, using platform encoding "
                    + encoding + ", i.e. build is platform dependent!");
        }

        FileSetManager fileSetManager = new FileSetManager();
        
        if (resources == null) {
            FileSet fs = new FileSet();
            fs.setDirectory(System.getProperty("user.dir"));
            List l = new ArrayList();
            l.add(cliResource);
            fs.setIncludes(l);
            resources = fs;
        }
        
        String[] includedFiles = fileSetManager.getIncludedFiles(resources);

        getLog().info("Resources:");
        for(String s : includedFiles) {
            getLog().info(s);
        }
        
        JCodeModel cm = new JCodeModel();

        for (String value : includedFiles) {

            File res;
            
            if (null == resources.getDirectory()) {
                res = new File(value);
            } else {
                res = new File(resources.getDirectory(), value);
            }

            if(res.getName().contains("_"))
                continue;   // this is a localized bundle, so ignore.

            String className = getClassName(res);

            String bundleName = value.substring(0, value.lastIndexOf('.')).replace('/', '.').replace('\\', '.');// cut off '.properties'
            String dirName = bundleName.substring(0, bundleName.lastIndexOf('.'));

            File destFile = new File(new File(destDir,dirName.replace('.','/')),className+".java");
            if(destFile.exists() && (destFile.lastModified() >= res.lastModified())) {
                getLog().info("Skipping " + res);
                continue;
            }

            getLog().info("Processing "+res);
            JPackage pkg = cm._package(dirName);

            Properties props = new Properties();
            FileInputStream in = null;
            try {
                in = new FileInputStream(res);
                props.load(in);
            } catch (IOException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException ioe) {
                        throw new MojoExecutionException(ioe.getMessage(), ioe);
                    }
                }
            }

            JDefinedClass clazz;
            try {
                clazz = pkg._class(JMod.PUBLIC | JMod.FINAL, className);
            } catch (JClassAlreadyExistsException e) {
                throw new MojoExecutionException("Name conflict "+className);
            }

            clazz.javadoc().add(
                "Defines string formatting method for each constant in the resource file"
            );

            /*
              [RESULT]

                LocalizableMessageFactory messageFactory =
                    new LocalizableMessageFactory("com.sun.xml.ws.resources.client");
                Localizer localizer = new Localizer();
            */

            JClass lmf_class;
            JClass l_class;
            JClass lable_class;
            try {
                lmf_class = cm.parseType(addLocalizationUtilityPackageName("LocalizableMessageFactory")).boxify();
                l_class = cm.parseType(addLocalizationUtilityPackageName("Localizer")).boxify();
                lable_class = cm.parseType(addLocalizationUtilityPackageName("Localizable")).boxify();
            } catch (ClassNotFoundException e) {
                throw new MojoExecutionException(e.getMessage(), e); // impossible -- but why parseType throwing ClassNotFoundExceptoin!?
            }

            JFieldVar $msgFactory = clazz.field(JMod.PRIVATE|JMod.STATIC|JMod.FINAL,
                lmf_class, "messageFactory", JExpr._new(lmf_class).arg(JExpr.lit(bundleName)));

            JFieldVar $localizer = clazz.field(JMod.PRIVATE|JMod.STATIC|JMod.FINAL,
                l_class, "localizer", JExpr._new(l_class));

            for (Map.Entry<Object,Object> e : props.entrySet()) {
                // [RESULT]
                // Localizable METHOD_localizable(Object arg1, Object arg2, ...) {
                //   return messageFactory.getMessage("servlet.html.notFound", message));
                // }
                // String METHOD(Object arg1, Object arg2, ...) {
                //   return localizer.localize(METHOD_localizable(arg1,arg2,...));
                // }
                String methodBaseName = NameConverter.smart.toConstantName(e.getKey().toString());

                JMethod method = clazz.method(JMod.PUBLIC | JMod.STATIC, lable_class, "localizable"+methodBaseName);

                int countArgs = countArgs(e.getValue().toString());

                JInvocation format = $msgFactory.invoke("getMessage").arg(
                    JExpr.lit(e.getKey().toString()));

                for( int i=0; i<countArgs; i++ ) {
                    format.arg( method.param(Object.class,"arg"+i));
                }
                method.body()._return(format);

                JMethod method2 = clazz.method(JMod.PUBLIC|JMod.STATIC, String.class, methodBaseName);
                method2.javadoc().add(e.getValue());

                JInvocation localize = JExpr.invoke(method);
                for( int i=0; i<countArgs; i++ ) {
                    localize.arg( method2.param(Object.class,"arg"+i));
                }

                method2.body()._return($localizer.invoke("localize").arg(localize));
            }
        }

        try {
            CodeWriter core = new FileCodeWriter(destDir, encoding);
            if (license != null) {
                core = new LicenseCodeWriter(core, license, encoding);
            }
            cm.build(core);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to generate code",e);
        }

        if (project != null) {
            project.addCompileSourceRoot(destDir.getAbsolutePath());
        }
    }

    private String addLocalizationUtilityPackageName(final String className) {
        return String.format("%s.%s", localizationUtilitiesPkgName, className);
    }

    private int countArgs(String value) {
        List<String> x = new ArrayList<String>();

        while(true) {
            String r1 = MessageFormat.format(value, x.toArray());
            x.add("xxxx");
            String r2 = MessageFormat.format(value, x.toArray());

            if(r1.equals(r2))
                return x.size()-1;
        }
    }

    /**
     * Computes the class name from the resource bundle name.
     */
    private String getClassName(File res) {
        String name = res.getName();
        int suffixIndex = name.lastIndexOf('.');
        name = name.substring(0,suffixIndex);
        return NameConverter.smart.toClassName(name)+"Messages";
    }

    /**
     * Writes all the source files under the specified file folder and inserts a
     * license file each java source file.
     *
     * @author Jitendra Kotamraju
     *
     */
    public static class LicenseCodeWriter extends FilterCodeWriter {

        private final File license;

        /**
         * @param core This CodeWriter will be used to actually create a storage
         * for files. LicenseCodeWriter simply decorates this underlying
         * CodeWriter by adding prolog comments.
         * @param license license File
         */
        public LicenseCodeWriter(CodeWriter core, File license, String encoding) {
            super(core);
            this.license = license;
            this.encoding = encoding;
        }

        @Override
        public Writer openSource(JPackage pkg, String fileName) throws IOException {
            Writer w = super.openSource(pkg, fileName);

            PrintWriter out = new PrintWriter(w);
            FileInputStream fin = null;
            try {
                fin = new FileInputStream(license);
                byte[] buf = new byte[8192];
                int len;
                while ((len = fin.read(buf)) != -1) {
                    out.write(new String(buf, 0, len));
                }
            } finally {
                if (fin != null) {
                    fin.close();
                }
            }
            out.flush();    // we can't close the stream for that would close the undelying stream.

            return w;
        }
    }
}
