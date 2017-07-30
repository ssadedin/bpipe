
bed_file = bed("test.bed",padding:20)

hello = {
    exec """
        echo "Processing $region"
    """
}

run {
    bed_file.split(4, allowBreaks:false) * [ hello ]
}
