source ../testsupport.sh

bpipe run -n 2 test.groovy data data/*.fa.gz > test.out 2>&1

COUNT_RUN=`grep -c fa.gz commandlog.txt`

[ "$COUNT_RUN" == "9" ] || err "Did not run cp command for every input file"

true
