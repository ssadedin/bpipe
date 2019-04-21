/*
 * Copyright (c) 2019 MCRI, authors
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
 package bpipe.storage

import com.upplication.s3fs.AmazonS3Factory
import static com.upplication.s3fs.AmazonS3Factory.*

import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path

import bpipe.PipelineError
import bpipe.executor.CommandExecutor
import groovy.transform.CompileStatic
import groovy.transform.ToString

/**
 * An initial implementation of a storage layer for S3. 
 * <p>
 * Note: currently hard coded to mount into a directory called 'work' on 
 * cloud executors, which is also where they assume it will be. This needs to be 
 * made generic so that multiple buckets can be mounted.
 * 
 * @author simon.sadedin
 */
@CompileStatic
@ToString(includes=['bucket','region','path'])
class S3StorageLayer extends StorageLayer {
    
    String bucket 
    
    String region
    
    String path
    
    String workDirectory = "work"
    
    transient String accessKey 
    
    transient String accessSecret
    
    private transient FileSystem fileSystem

    /*
     * Initialises the file system for S3 java nio provider
     */
    private void init() {
        
        if(!fileSystem.is(null))
            return 
            
        if(accessKey == null || accessSecret == null) 
            throw new PipelineError("S3 file system requires provision of accessKey and accessSecret in bpipe.config")
        
        Map<String,?> env = [:]
        env[ACCESS_KEY] = accessKey
        env[SECRET_KEY] = accessSecret

        this.fileSystem = FileSystems.newFileSystem(new URI("s3:///"), env, Thread.currentThread().getContextClassLoader());
    }

    @Override
    public Path toPath(String path) {
        init()
        fileSystem.getPath("/$bucket/" + path)
    }

    @Override
    public String getMountCommand(CommandExecutor executor) {
        """
            mkdir -p ~/.aws

            mkdir work

            echo '
            [default]
            aws_access_key_id=$accessKey
            aws_secret_access_key=$accessSecret
            ' >  ~/.aws/credentials

           mkdir $workDirectory

            s3fs $bucket $workDirectory
        """
    }
}
