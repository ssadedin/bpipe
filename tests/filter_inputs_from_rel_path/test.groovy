@filter("merge")
merge_av = {
    exec """                                                                                                        
        cat $inputs.av > $output.av
    """
}

run { merge_av }
