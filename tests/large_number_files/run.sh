source ../testsupport.sh

ulimit -n 256

../../bin/bpipe run -n 4 test.groovy > test.out 2>&1

OUTPUT_COUNT=`ls *.csv | wc | awk '{print $1}'`

[ "$OUTPUT_COUNT" == 1001 ] || err "Incorrect number of output CSV files observed: $OUTPUT_COUNT Expected: 1001"

true
