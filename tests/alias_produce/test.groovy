hello = {
    produce("crazy.txt") {
        if(inputs.txt.size() == 1) 
            alias(input.txt) to(output.txt)
    }
}

world = {
    exec """
        cp $input.txt $output.xml
    """
}

run {
    hello + world
}
