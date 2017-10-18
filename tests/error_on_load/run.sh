source ../testsupport.sh

bpipe run test.groovy > test.out 2>&1

grep -q 'Hello there' test.out && err "Message printed even though loaded script has error"

grep -q 'An error occurred executing your pipeline' test.out || err "Error message not printed even though loaded script has error"

true


