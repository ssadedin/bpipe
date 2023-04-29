source ../testsupport.sh

run

grep -q 'Processing chr2' test.out || err 'Should have processed chr2'

grep -q 'Processing chr1' test.out || err 'Should have processed chr1'

rm test.out

bpipe run -L chr2 test.groovy > test.out 2>&1

grep -q 'Processing chr1' test.out &&  err 'Should not have processed chr1'

grep -q 'Processing chr2' test.out || err 'Should have processed chr2'

true

