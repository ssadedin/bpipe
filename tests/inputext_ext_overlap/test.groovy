
// This test is for a regression, where searching for 
// input ending with ".cnv" would match files called
// ".xcnv"

hello = {
    exec """
        cp $input.cnv $output.csv
    """
}

run { hello }
