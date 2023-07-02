hello = {
    exec """
        echo "I will not make $output.txt.optional"
    """
        // date > $output.tsv
}

there = {
    exec """
        echo "I will make $output.txt" > $output.txt
    """
}



world = {
   exec """
       cat $inputs.txt > $output.all.txt
   """
}

run {
    [ hello, there] + world
}
