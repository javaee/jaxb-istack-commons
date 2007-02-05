package com.sun.istack.tools;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DynamicConfigurator;
import org.apache.tools.ant.IntrospectionHelper;
import org.apache.tools.ant.Task;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Executes a {@link Task} in a special class loader that allows
 * us to control where to load 2.1 APIs, even if we run in Java 6.
 *
 * @author Kohsuke Kawaguchi
 * @author Bhakti Mehta
 */
public abstract class ProtectedTask extends Task implements DynamicConfigurator {

    private final AntElement root = new AntElement("root");

    public void setDynamicAttribute(String name, String value) throws BuildException {
        root.setDynamicAttribute(name,value);
    }

    public Object createDynamicElement(String name) throws BuildException {
        return root.createDynamicElement(name);
    }

    public void execute() throws BuildException {
        //Leave XJC2 in the publicly visible place
        // and then isolate XJC1 in a child class loader,
        // then use a MaskingClassLoader
        // so that the XJC2 classes in the parent class loader
        //  won't interfere with loading XJC1 classes in a child class loader
        ClassLoader ccl = Thread.currentThread().getContextClassLoader();
        try {
            ClassLoader cl = createClassLoader();
            Class driver = cl.loadClass("com.sun.tools.ws.ant.WsImport");

            Task t = (Task)driver.newInstance();
            t.setProject(getProject());
            t.setTaskName(getTaskName());
            root.configure(t);

            Thread.currentThread().setContextClassLoader(cl);
            t.execute();
        } catch (UnsupportedClassVersionError e) {
            throw new BuildException("Requires JDK 5.0 or later. Please download it from http://java.sun.com/j2se/1.5/");
        } catch (ClassNotFoundException e) {
            throw new BuildException(e);
        } catch (InstantiationException e) {
            throw new BuildException(e);
        } catch (IllegalAccessException e) {
            throw new BuildException(e);
        } catch (IOException e) {
            throw new BuildException(e);
        } finally {
            Thread.currentThread().setContextClassLoader(ccl);
        }
    }

    /**
     * Creates a protective class loader that will host the actual task.
     */
    protected abstract ClassLoader createClassLoader() throws ClassNotFoundException, IOException;

    /**
     * Captures the elements and attributes.
     */
    private class AntElement implements DynamicConfigurator {
        private final String name;

        private final Map<String,String> attributes = new HashMap<String,String>();

        private final List<AntElement> elements = new ArrayList<AntElement>();

        public AntElement(String name) {
            this.name = name;
        }

        public void setDynamicAttribute(String name, String value) throws BuildException {
            attributes.put(name,value);
        }

        public Object createDynamicElement(String name) throws BuildException {
            AntElement e = new AntElement(name);
            elements.add(e);
            return e;
        }

        /**
         * Copies the properties into the Ant task.
         */
        public void configure(Object antObject) {
            IntrospectionHelper ih = IntrospectionHelper.getHelper(antObject.getClass());

            // set attributes first
            for (Entry<String, String> att : attributes.entrySet()) {
                ih.setAttribute(getProject(), antObject, att.getKey(), att.getValue());
            }

            // then nested elements
            for (AntElement e : elements) {
                Object child = ih.createElement(getProject(), antObject, e.name);
                e.configure(child);
                ih.storeElement(getProject(), antObject, child, e.name);
            }
        }
    }

    /**
     * Given the URL inside jar, returns the URL to the jar itself.
     */
    protected static URL toJarUrl(URL res) throws ClassNotFoundException, MalformedURLException {
        String url = res.toExternalForm();
        if(!url.startsWith("jar:"))
            throw new ClassNotFoundException("Loaded outside a jar "+url);
        url = url.substring(4); // cut off jar:
        url = url.substring(0,url.lastIndexOf('!'));    // cut off everything after '!'
        return new URL(url);
    }
}

