source ../testsupport.sh

set -e

bpipe run test2.groovy test.txt   > test.out

grep -q 'Output is test.hello.csv' test.out || err "did not print expected message about output"
grep -q 'pre-existing' test.out && err "printed output as pre-existing when not"

bpipe run -p worldy=true test2.groovy test.txt > test2.out

grep -q 'Outputs are:' test2.out || err "did not print expected message about output"
grep -q 'test.world.xml' test2.out || err "did not print expected message about output file"
grep -q 'xml.*pre-existing' test2.out && err "printed output as pre-existing when not"
grep -q 'csv.*pre-existing' test2.out || err "did not print CSV outout as pre-existing when it was"

true





