source ../testsupport.sh

BPIPE_LIB=mylib ../../bin/bpipe run test.groovy > test.out

grep -q "^hi" test.out || err "Did not find message 'hi' shoud have been printed from from library stage"

true
