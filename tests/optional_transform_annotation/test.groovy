
@Transform('csv')
hello = {

    println "This stage is optional - it will not run but it is annotated as transform"

    return 

    exec """
        cp $input.txt $output.csv
    """
}

run {
    hello
}
