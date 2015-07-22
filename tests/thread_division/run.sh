source ../testsupport.sh

../../bin/bpipe run -n 6 test.groovy > test.out

grep -q "hello using 5" test.out || err "Wrong number of threads used by hello"

grep -q "hello2 using 2" test.out || err "Wrong number of threads used by hello2"

../../bin/bpipe run -n 2 test.groovy > test2.out

grep -q "hello using 1" test2.out || err "Wrong number of threads used by hello from total 2"

grep -q "hello2 using 1" test2.out || err "Wrong number of threads used by hello2 from total 2"

../../bin/bpipe run -n 30 test.groovy > test3.out

grep -q "hello using 10" test3.out || err "Wrong number of threads used by hello from total 30"

grep -q "hello2 using 2" test3.out || err "Wrong number of threads used by hello2 from total 30"

true

