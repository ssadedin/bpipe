source ../testsupport.sh

run test.txt

grep -q "could not be found" test.out && err "Incorrectly reported that output in non-default directory could not be found"

exists outdir/test.csv

true
