hello = {
   exec "cp $input.txt $output.csv ; false"
}

run { hello }
