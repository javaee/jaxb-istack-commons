
package com.sun.istack.build;

import java.io.File;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.selectors.FilenameSelector;

/**
 * Generate source files from resource bundles, via CLI
 * Wrapper for ResourceGenTask
 *
 * @author William L. Thomson Jr. <wlt@o-sinc.com>
 */
public class ResourceGenCLI {

    public static void main(String[] args) {
        new ResourceGenCLI(args);
    }

    public ResourceGenCLI(String[] args) {
        FileSet fs = new FileSet();
        Project project = new Project();
        project.setName("ResourceGenCLI");
        ResourceGenTask rgt = new ResourceGenTask();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-b":
                case "--base-dir":
                    i++;
                    fs.setDir(new File(args[i]));
                    project.setBasedir(args[i]);
                    rgt.setProject(project);
                    break;
                case "-d":
                case "--dest-dir":
                    i++;
                    rgt.setDestDir(new File(args[i]));
                    break;
                case "-e":
                case "--encoding":
                    i++;
                    rgt.setEncoding(args[i]);
                    break;
                case "-h":
                case "--help":
                    help();
                    break;
                case "-l":
                case "--license":
                    i++;
                    rgt.setLicense(new File(args[i]));
                    break;
                case "-p":
                case "--package":
                    i++;
                    rgt.setLocalizationUtilitiesPkgName(args[i]);
                    break;
                case "-r":
                case "--resource":
                    i++;
                    FilenameSelector selector = new FilenameSelector();
                    selector.setName(args[i]);
                    fs.add(selector);
                    rgt.addConfiguredResource(fs);
                    break;
                default:
                    break;
            }
        }
        try {
            rgt.execute();
        } catch (BuildException be) {
            System.out.print(be);
        }
    }

    private void help() {
        String help = "ResourceGenCLI - "
            + "Generate source files from resource bundles%n"
            + "Usage:%n"
            + "com.sun.istack.build.ResourceGenCLI "
            + "-b /project/src/main/resources "
            + "-d /project/src/main/java "
            + "-l /project/license.txt "
            + "-p org.package.my "
            + "-r **/my_package.properties%n"
            + "%n"
            + "Global Options:%n"
            + "  -b, --base-dir             base directory (resources) required%n"
            + "  -d, --dest-dir             destination directory (src) required%n"
            + "  -e, --encoding             encoding (UTF-8) optional%n"
            + "  -l, --license              absolute path to license required%n"
            + "  -p, --package              package name required%n"
            + "  -r, --resource             resource bundle (*.properties)%n"
            + "%n"
            + "  -h, --help                 print help%n"
            + "";
        System.out.printf(help);
    }
}
