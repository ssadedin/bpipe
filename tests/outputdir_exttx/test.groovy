
@transform("csv")
hello = {
    output.dir="out"
    exec "cp $input.txt $output.csv"
}

run { hello }

