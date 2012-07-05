source ../testsupport.sh

. ./cleanup.sh

../../bin/bpipe run -p foo=foooo -p bar=baaaar test.groovy > test.out

grep -q "Stage hello" test.out || err "Failed to find expected stage hello"
grep -q "Stage world" test.out || err "Failed to find expected stage world"
grep -q "Stage there" test.out || err "Failed to find expected stage there"

# This one is only specified as a command line param
grep -q "foo=foooo" test.out || err "Failed to find foo=foooo"

# This one is specified as a command line param AND in the script - command line should win
grep -q "bar=baaaar" test.out || err "Failed to find bar=baaaar"

# fubar is only defined in the script - this is legacy behavior!
grep -q "fubar=FUBAR" test.out || err "Failed to find fubar=FUBAR"

[ ! -f test.txt ] && err "Failed to find expected output test.txt"
[ ! -f test.world.txt ] && err "Failed to find expected output test.world.txt"
[ ! -f test.there.txt ] && err "Failed to find expected output test.there.txt"

true
