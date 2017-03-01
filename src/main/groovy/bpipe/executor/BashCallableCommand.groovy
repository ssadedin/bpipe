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

import java.util.concurrent.Callable

/**
 *  Generic executor for 'bash' command implementing the {@link Callable} interface
 *  <p>
 *  See {@link java.util.concurrent.ExecutorService}
 *
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class BashCallableCommand implements Callable<BashResult>, Serializable {

    List<String> command

    BashCallableCommand( String command ) {
        this.command = ['sh','-c', command]
    }

    BashCallableCommand( String... command ) {
        this.command = (command as List<String>)
    }


    private def String text( InputStream input ) {
        assert input

        StringWriter result = new StringWriter()
        while( input.available() ) {
            result.append(input.read() as char)
        }

        return result.toString()
    }

    @Override
    BashResult call() {
        BashResult result = new BashResult()

        try {
            Process process = new ProcessBuilder(command).start()
            result.exitCode = process.waitFor()
            result.stdOutput = text(process.getInputStream())
            result.stdError = text(process.getErrorStream())

        }
        catch( Exception exception ) {
            result.exitCode = -1
            result.exception = exception
        }
        finally {
            return result
        }

    }

    /**
     * Only for test
     *
     * @param args
     */
    public static void main(String[] args ) {

        def test= new BashCallableCommand("echo hola")
        def result = test.call()


        println "Exit code: ${result.exitCode}"
        println "Stdout   : ${result.stdOutput}"
        println "StdErr   : ${result.stdError} "
        result.exception?.printStackTrace()

    }
}
