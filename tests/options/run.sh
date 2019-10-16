source ../testsupport.sh

bpipe run test.groovy -bar 1 > test.out 2>&1

grep -q 'ERROR: One or more pipeline options were invalid or missing' test.out || err 'Did not print expected error message'

grep -q 'bar <arg>   The bar to foo with' test.out || err 'Did not print proper usage'

bpipe run test.groovy -bar 1 -foo 2 foobar.txt > test.out 2>&1

grep -q 'ERROR: One or more pipeline options were invalid or missing' test.out && err 'Printed error message incorrectly'

grep -q '2 => 1' test.out || err 'Did not display option values in output'

true

