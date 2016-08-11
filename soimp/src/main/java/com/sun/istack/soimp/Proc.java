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

package com.sun.istack.soimp;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class Proc {
    private final Process proc;
    private final Thread t1,t2;

    public Proc(String cmd,String[] env,OutputStream out, File workDir) throws IOException {
        this( Runtime.getRuntime().exec(cmd,env,workDir), null, out );
    }

    public Proc(String[] cmd,String[] env,OutputStream out, File workDir) throws IOException {
        this( Runtime.getRuntime().exec(cmd,env,workDir), null, out );
    }

    public Proc(String[] cmd,String[] env,InputStream in,OutputStream out) throws IOException {
        this( Runtime.getRuntime().exec(cmd,env), in, out );
    }

    private Proc( Process proc, InputStream in, OutputStream out ) throws IOException {
        this.proc = proc;
        t1 = new Copier(proc.getInputStream(), out);
        t1.start();
        t2 = new Copier(proc.getErrorStream(), out);
        t2.start();
        if(in!=null)
            new ByteCopier(in,proc.getOutputStream()).start();
        else
            proc.getOutputStream().close();
    }

    public int join() {
        try {
            t1.join();
            t2.join();
            return proc.waitFor();
        } catch (InterruptedException e) {
            // aborting. kill the process
            proc.destroy();
            return -1;
        }
    }

    private static class Copier extends Thread {
        private final InputStream in;
        private final OutputStream out;

        public Copier(InputStream in, OutputStream out) {
            this.in = in;
            this.out = out;
        }

        public void run() {
            try {
                copyStream(in,out);
                in.close();
            } catch (IOException e) {
                // TODO: what to do?
            }
        }
    }

    private static class ByteCopier extends Thread {
        private final InputStream in;
        private final OutputStream out;

        public ByteCopier(InputStream in, OutputStream out) {
            this.in = in;
            this.out = out;
        }

        public void run() {
            try {
                while(true) {
                    int ch = in.read();
                    if(ch==-1)  break;
                    out.write(ch);
                }
                in.close();
                out.close();
            } catch (IOException e) {
                // TODO: what to do?
            }
        }
    }

    public static void copyStream(InputStream in,OutputStream out) throws IOException {
        byte[] buf = new byte[256];
        int len;
        while((len=in.read(buf))>0)
            out.write(buf,0,len);
    }
}
