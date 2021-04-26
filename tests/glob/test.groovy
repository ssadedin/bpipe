/**
 * Test that 'glob' function works and can be used with 'from'
 * to match files in the local directory.
 */
hello = {
  exec "cp $input $output"
}

world = {
  from("*.xls","*.csv") {
    exec "cat $inputs.xls > $output.xml"
    exec "cat $inputs.csv > $output.tsv"
  }
}

run { hello + world }
