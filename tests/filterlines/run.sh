source ../testsupport.sh

. ./cleanup.sh

run test.txt

grep -q "Stage hello" test.out || err "Failed to find expected stage hello"

[ ! -f test.hello.txt ] && err "Failed to find expected output test.hello.txt"

[ ! -f .bpipe/outputs/hello.test.hello.txt.properties ] && err "Failed to find expected output meta data file hello.test.hello.txt.properties"

[ `cat test.hello.txt` == 'hello' ] || err "Failed to find correct contents in output file"

true
