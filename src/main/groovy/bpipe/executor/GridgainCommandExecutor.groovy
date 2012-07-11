package bpipe.executor

import java.util.logging.Logger;

import bpipe.PipelineError

/**
 *  Execute a B-pipe task on a GridGrain cluster
 *  <p>
 *  Read more http://www.gridgain.com/
 *
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class GridgainCommandExecutor extends AbstractGridBashExecutor {
    
    /**
     * Logger to use with this class
     */
    private static Logger log = Logger.getLogger("bpipe.PipelineOutput");
    

    private static boolean classpathConfigured

    GridgainCommandExecutor() {
        // add GridGain dependencies to the classpath on-fly
        configureClasspathIfRequired()
        // set the provider instance
        provider = GridgainProvider.instance
    }


    static synchronized def configureClasspathIfRequired() {

        if( classpathConfigured ) return

        def root = this.classLoader.rootLoader
        assert root, "Cannot access to the B-pipe RootLoader instance"


        /*
         * Add to the classpath all the required GRIDGAIN jars
         */
        def home = System.getenv("GRIDGAIN_HOME")
        if( !home ) {
            throw new PipelineError("Missing GRIDGAIN_HOME variable. Make sure your GridGain distribution is correclty installed.")
        }

        home = new File(home)
        if( !home.exists() ) {
            throw new PipelineError("Missing GRIDGAIN_HOME path: '$home'")
        }

        def libs = new File(home,"libs")
        if( !libs.exists() ) {
            throw new PipelineError("Missing GridGain libs path: '${libs}'")
        }


        File mainJarFile = home.listFiles().find { File file -> file.name =~ /gridgain\-.*\.jar/ }
        if( !mainJarFile ) {
            throw new PipelineError("Cannot find the GridGain JAR file in the path: '${home}'")
        }

        // add the libraries to the root classloader
        root.addURL( mainJarFile.toURL() )
        def jars   = libs.listFiles().findAll { it.name.endsWith('.jar') }
        jars.each { File it ->
            if( acceptLib(it.name) ) {
                root.addURL( it.getAbsoluteFile().toURL() )
            }
        }


        // set the flag to skip the next time
        classpathConfigured = true

    }



    /** the list of GG provided libraries that should be added to the classpath */
    private static def SKIP_LIBRARIES = ['groovy-','groovypp-','commons-cli-']


    static boolean acceptLib( String fileName ) {
        for( String it : SKIP_LIBRARIES ) {
            if( fileName.startsWith(it)) return false
        }

        return true
    }
}
