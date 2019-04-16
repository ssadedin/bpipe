/*
 * Copyright (c) Murdoch Childrens Research Institute and Contributers
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice, this
 * list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package bpipe.executor

import groovy.util.logging.Log;

import bpipe.PipelineError
import bpipe.storage.StorageLayer

/**
 *  Execute a B-pipe task on a GridGrain cluster
 *  <p>
 *  Read more http://www.gridgain.com/
 *
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Log
class GridgainCommandExecutor extends AbstractGridBashExecutor {
    
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

    @Override
    public String localPath(String storageName) {
        return null;
    }


    @Override
    public void mountStorage(StorageLayer storage) {
        // Nooop
    }
}
