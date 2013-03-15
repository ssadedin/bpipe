/*
 * When parallel branches are split, the files used for splitting 
 * the branches should get considered as the basis for outputs BEFORE
 * the other files that may have been inputs.
 */
hello = {
  exec """
    cat $input.txt $input.csv > $output.xml
  """
}

run {
  "%.csv" * [ hello ]
}
