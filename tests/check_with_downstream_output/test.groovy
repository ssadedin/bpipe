hello = {
    check('always pass') { 
        exec """
            touch $output.txt
        """
    }
}

world = {
    exec """echo "The inputs are $inputs.txt" """
}

run {
    hello + world
}
