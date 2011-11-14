echo = {
        exec "echo $inputs"
}

Bpipe.run {
    // eg:  SureSelect_Capture_11MG2107_AD0AN0ACXX_GATCAG_L002_R1.fastq

    "CXX_%_*.fastq" * [
        echo
   ]
}
