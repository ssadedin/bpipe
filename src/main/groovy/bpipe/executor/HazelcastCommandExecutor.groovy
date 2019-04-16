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
 *  Execute B-bipe commands on a Hazelcast distributed grid
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Log
class HazelcastCommandExecutor extends AbstractGridBashExecutor {


    /** The configuration obj */
    def Map cfg

    /** The ID for this command provided by Bpipe */
    def String id

    /** The stage name to which this command belong */
    def String name

    /** The bash command to be executed */
    def String cmd

    /**
     * Configure the class path
     */
    HazelcastCommandExecutor() {
        // configure the classpath to be able to support Hazelcast
        addHazelcastToRootLoader()

        // configure the provider
        provider = HazelcastGridProvider.instance

    }


    private static boolean classpathConfigured = false

    /*
    * Update the root class loader adding the Hazelcast library hazelcast-all-x.x.x.jar
    * <p>
    * It requires the variable {@code HAZELCAST_HOME} to be defined and pointing to the root
    * Hazelcast distribution folder
    */
    static synchronized def void addHazelcastToRootLoader() {

        if( classpathConfigured ) return

        /*
         * Check if Hazelcast is already on the classpath
         */
        try {
            Class.forName("com.hazelcast.client.HazelcastClient")
            Class.forName("com.hazelcast.core.HazelcastInstance")
            // since the required class are on the class path
            // it is not required to update the class, so just return
            return
        }
        catch( Exception e ) {
            log.info("Trying to find out Hazelcast library ..")
        }


        def root = this.classLoader.rootLoader
        assert root, "Cannot access to the B-pipe RootLoader instance"

        def home = System.getenv("HAZELCAST_HOME")
        if( !home ) {
            throw new PipelineError("Missing HAZELCAST_HOME variable. Define a variable name HAZELCAST_HOME pointing to the root folder of the Hazelcast distribution")
        }

        File libs = new File(home, "lib")
        if( !libs.exists() ) {
            throw new PipelineError("Missing Hazelcast libraries path: '$libs'")
        }

        File mainJarFile = libs.listFiles().find { File file -> file.name =~ /hazelcast\-all\-.*\.jar/ }
        if( !mainJarFile ) {
            throw new PipelineError("Cannot find the hazelcast-all-x.x.jar file in the path: '${libs}'")
        }

        /*
         * add the Hazelcast to the root class loader
         */
        root.addURL( mainJarFile.toURL() )

        /*
         * set the flag to TRUE to skip
         */
        classpathConfigured = true
    }
    @Override
    public String localPath(String storageName) {
        // noop
        return null;
    }
    @Override
    public void mountStorage(StorageLayer storage) {
        // noop
    }
}
