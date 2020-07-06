source ../testsupport.sh

run

grep -q "I will use 2 threads" test.out || err "Incorrect environment procs value used - expected 2"

bpipe run -e home test.groovy > test.out

grep -q "I will use 4 threads" test.out || err "Incorrect environment procs value used - expected 4 for environment home"

bpipe run -e work test.groovy > test.out

grep -q "I will use 8 threads" test.out || err "Incorrect environment procs value used - expected 8"

true
