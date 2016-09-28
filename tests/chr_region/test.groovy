// genome 'hg19'
genome 'GRCh37'

hello = {
    exec """
        echo "The region for $chr is $region"
    """
}

run {
    chr(1,2,3) * [ hello ]
}
