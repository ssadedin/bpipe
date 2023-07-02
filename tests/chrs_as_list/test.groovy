chrs=["chr1","chr2"]

hello = {
    exec """
        echo "Processing $chr"
    """

    Thread.sleep(1000) // ensure that output has time to spool before process ends
                       // was causing sporadic test failures due to no output
}

run {
    chr(chrs) * [ hello ]
}
