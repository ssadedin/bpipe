source ../testsupport.sh

bpipe run -n 1 test.groovy > test.out

grep -q "I am using 1 threads" test.out || err "Did not use expected number of threads"

bpipe run -n 3 test.groovy > test.out

grep -q "I am using 3 threads" test.out || err "Did not use expected number of threads"

bpipe run -n 10 test.groovy > test.out

# limited by bpipe.config range 1..5
grep -q "I am using 5 threads" test.out || err "Did not use expected number of threads"

true
