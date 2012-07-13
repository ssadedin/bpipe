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

    File outputDirectory

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
            Process process = new ProcessBuilder(command).directory(outputDirectory).start()
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
