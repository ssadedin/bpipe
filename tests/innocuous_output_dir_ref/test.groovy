hello = {

    // This is a regression test: there was a bug where referencing
    // the output directory like this below would CHANGE the output directory
    // resolved further down and cause pipeline to fail. Of course, that
    // should not happen.
    println "The output directory is " + output.dir

    output.dir="foo"


    exec """
        echo frog > $output.rpkm 
        
        echo "${inputs.bam.withFlag('x')}"
    """
}

run { hello }
