source ../testsupport.sh

bpipe run test.groovy test.tsv > test.out

grep -q '.*test.tsv.* ->.*test.xlsx.*' test.out || err "Copy of expected test.tsv output not observed"

rm -f test.xlsx

bpipe run test.groovy test.csv > test.out

grep -q '.*test.csv.* ->.*test.xlsx.*' test.out || err "Copy of expected test.csv output not observed"

bpipe run test.groovy test.xml > test.out

grep -q 'Unable to locate one or more specified inputs from pipeline with the' test.out || err "did not observe expected error message about missing input"

true
