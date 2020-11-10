source ../testsupport.sh

bpipe run -p TESTCASE=exec  -p REFERENCE_BAD_VARIABLE=false test.groovy > test.out 2>&1

grep -q "The home directory is $HOME" test.out || err 'Did not find expected output from $HOME variable'

bpipe run -p TESTCASE=exec  -p REFERENCE_BAD_VARIABLE=true test.groovy > test.out 2>&1

grep -q "No such property: bonkers" test.out || err "Failed to find error message about undefined variable in output"

bpipe run -p TESTCASE=produce test.groovy > test.out 2>&1

grep -q "No such property: hello_produce_bonkers" test.out || err "Failed to find error message about undefined variable in output"

bpipe run -p TESTCASE=nothing test.groovy > test.out 2>&1

grep -q "No such property: hello_nothing_bonkers" test.out || err "Failed to find error message about undefined variable in output"

true

