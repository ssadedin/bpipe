cp = {
 output.dir = "output"
 check {
   exec "cp $input.fa.gz  $output.fa.gz"
 } otherwise {
  succeed "Never fails"
 }
}
ls = {
 from(glob("$input/*")) {
     forward inputs
 }
}
run { ls + "%.fa.gz" * [ cp ] }
