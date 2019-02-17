source ../testsupport.sh

run test.csv

grep -q 'The hello foo is:$' test.out || err "missing optional input generated value"

grep -q 'The there foo is:--foo' test.out || err "provided optional input did not generate expected flag"

grep -q 'The world foo is:' test.out  && err "missing required input did not generate expected error"

grep -q 'ERROR: stage world failed: Unable to locate one or more specified inputs' test.out  || err "Expected error for missing input not produced"



