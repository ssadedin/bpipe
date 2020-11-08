
hello = {

    var filt_status : ""

    branch.pipeline = branch.name

    output.dir="verify_stuff"

    println "The output directory is: " + output.dir

    produce(output.dir  + '.log') {                                                                                                                                                                           
        exec """
            cd $output.dir

            date | tee ${file(output).absolutePath}
        """
    }
}

run {
 hello
}
