/**
 * Test that 'glob' function works and can be used with 'from'
 * to match files in the local directory.
 */
hello = {
  exec "cp $input $output"
}

world = {
  from(glob("*.xls","*.csv")) {
    exec "cat $inputs.xls > $output.xls"
    exec "cat $inputs.csv > $output.csv"
  }
}

run { hello + world }
