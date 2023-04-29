chrs=["chr1","chr2"]

hello = {
    exec """
        echo "Processing $chr"
    """
}

run {
    chr(chrs) * [ hello ]
}
