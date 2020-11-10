
REFERENCE_BAD_VARIABLE=false


hello_exec = {

    if(REFERENCE_BAD_VARIABLE) {
        def x = bonkers

        println "x = $x"
    }

    exec """
        echo "The home directory is $HOME"
    """, "foo"
}

hello_nothing = {

    def x = hello_nothing_bonkers

    println 'x = $x'
}

hello_produce = {

    def x = hello_produce_bonkers

    println 'x = $x'

    produce('bonkers.txt') {
        exec "touch $output.txt"
    }
}

hello_success = {
    exec """
        cp -v $input.txt $output.csv
    """
}

run { 
    switch(TESTCASE) { 
        case 'exec':
            hello_exec
            break
        case 'nothing':
            hello_nothing
            break
        case 'produce':
            hello_produce
            break
        case 'success':
            hello_success
            break
    }
}
