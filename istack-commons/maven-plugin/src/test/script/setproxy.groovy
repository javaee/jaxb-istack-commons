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

log.info("Checking proxy...")
def itsettings = new XmlParser().parse(project.build.testResources.directory[0] + "/it-settings.xml")
def itproxy = ""
if (settings?.proxies) {
    Node proxies = new Node(itsettings, "proxies")
    settings?.proxies?.each { proxy ->
        if (proxy.active) {
            if ("http".equals(proxy.protocol)) {
                itproxy =  "-Dhttp.proxyHost=" + proxy.host
                if (proxy.port) {
                    itproxy += " -Dhttp.proxyPort=" + proxy.port
                }
            }
            def p = new Node(proxies, "proxy")
            new Node(p, "protocol", proxy.protocol)
            new Node(p, "port", proxy.port)
            if (proxy.username) {new Node(p, "username", proxy.username)}
            if (proxy.password) {new Node(p, "password", proxy.password)}
            new Node(p, "host", proxy.host)
            new Node(p, "active", proxy.active)
            new Node(p, "nonProxyHosts", proxy.nonProxyHosts)
        }
    }
}

if (itproxy.trim().length() > 0) {
    log.info("Setting: " + itproxy)
} else {
    log.info("No proxy found")
}

def writer = new FileWriter(new File(project.build.directory, "it-settings.xml"))
XmlNodePrinter printer = new XmlNodePrinter(new PrintWriter(writer))
printer.setPreserveWhitespace(true);
printer.print(itsettings)

project.getModel().addProperty("ittest-proxy", itproxy)
