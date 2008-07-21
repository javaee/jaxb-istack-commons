/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 * 
 * Contributor(s):
 * 
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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.shared.model.fileset.FileSet;
import org.apache.maven.shared.model.fileset.util.FileSetManager;

import com.sun.codemodel.*;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;


/**
 * Goal which generates source files from resource bundles.
 * You can then refer to resources as methods rather than hard-coding string constants.
 *
 * @author Jakub Podlesak
 * @author Kohsuke Kawaguchi
 *
 * @goal rs-gen
 * 
 * @phase process-sources
 */
public class ResourceGenMojo extends AbstractMojo {

    /**
     * Location of the destination directory.
     * @parameter
     * @required
     */
    private File destDir;

    /**
     * File set of the properties files to be processed.
     * @parameter
     * @required
     */
    private FileSet resources;

    
    public void execute() throws MojoExecutionException {

        if(resources == null) {
            throw new MojoExecutionException("No resource file is specified");
        }
        if(destDir == null) {
            throw new MojoExecutionException("No destdir attribute is specified");
        }
        
        if (!destDir.exists()) {
            destDir.mkdirs();
        }

        FileSetManager fileSetManager = new FileSetManager();
        
        String[] includedFiles = fileSetManager.getIncludedFiles(resources);
        
        System.out.println("Resources:");
        for(String s : includedFiles) {
            System.out.println(s);
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
                System.out.println("Skipping "+res);
                continue;
            }

            System.out.println("Processing "+res);
            JPackage pkg = cm._package(dirName);

            Properties props = new Properties();
            try {
                FileInputStream in = new FileInputStream(res);
                props.load(in);
                in.close();
            } catch (IOException e) {
                throw new MojoExecutionException(e.getMessage(), e);
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
                lmf_class = cm.parseType("com.sun.istack.localization.LocalizableMessageFactory").boxify();
                l_class = cm.parseType("com.sun.istack.localization.Localizer").boxify();
                lable_class = cm.parseType("com.sun.istack.localization.Localizable").boxify();
            } catch (ClassNotFoundException e) {
                throw new MojoExecutionException(e.getMessage(), e); // impossible -- but why parseType throwing ClassNotFoundExceptoin!?
            }

            JFieldVar $msgFactory = clazz.field(JMod.PRIVATE|JMod.STATIC|JMod.FINAL,
                lmf_class, "messageFactory", JExpr._new(lmf_class).arg(JExpr.lit(bundleName)));

            JFieldVar $localizer = clazz.field(JMod.PRIVATE|JMod.STATIC|JMod.FINAL,
                l_class, "localizer", JExpr._new(l_class));

            for (Object key : props.keySet()) {
                // [RESULT]
                // Localizable METHOD_localizable(Object arg1, Object arg2, ...) {
                //   return messageFactory.getMessage("servlet.html.notFound", message));
                // }
                // String METHOD(Object arg1, Object arg2, ...) {
                //   return localizer.localize(METHOD_localizable(arg1,arg2,...));
                // }
                String methodBaseName = NameConverter.smart.toConstantName(key.toString());

                JMethod method = clazz.method(JMod.PUBLIC | JMod.STATIC, lable_class, "localizable"+methodBaseName);

                int countArgs = countArgs(props.getProperty(key.toString()));

                JInvocation format = $msgFactory.invoke("getMessage").arg(
                    JExpr.lit(key.toString()));

                for( int i=0; i<countArgs; i++ ) {
                    format.arg( method.param(Object.class,"arg"+i));
                }
                method.body()._return(format);

                JMethod method2 = clazz.method(JMod.PUBLIC|JMod.STATIC, String.class, methodBaseName);
                method2.javadoc().add(props.get(key));

                JInvocation localize = JExpr.invoke(method);
                for( int i=0; i<countArgs; i++ ) {
                    localize.arg( method2.param(Object.class,"arg"+i));
                }

                method2.body()._return($localizer.invoke("localize").arg(localize));
            }
        }

        try {
            cm.build(destDir);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to generate code",e);
        }
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
}
