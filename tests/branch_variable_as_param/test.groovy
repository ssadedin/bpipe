
set_sample = {
    branch.sample = branch.name
}


hello = {
    requires sample : "The sample name"

    exec """
        echo $sample > $output.txt
    """
}

run {
    ["foo","bar"] * [ set_sample + hello ]
}
