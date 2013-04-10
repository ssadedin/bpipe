hello = {
  produce("outdir/test.csv") {
    exec "mkdir outdir; cp $input.txt outdir/test.csv"
  }
}

run { hello }
