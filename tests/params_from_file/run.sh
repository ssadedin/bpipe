source ../testsupport.sh

bpipe run -p@params.txt test.groovy > test.out

grep -q "param foobar=froggledoggle" test.out || err "Failed to find expected parameter text in output"

true
