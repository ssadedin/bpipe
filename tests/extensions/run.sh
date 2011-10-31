source ../testsupport.sh

rm -f test.txt.* *.xml hello.*

# Without an input should get a good message back
run 
grep -q "Input expected but not provided" test.out || err "Expected error message asking for input"

# Now run with multiple inputs
run test.csv test.txt

grep -q "Stage hello" test.out || err "Failed to find expected stage hello"
grep -q "Stage world" test.out || err "Failed to find expected stage world"

[ ! -f hello.out.csv ] && err "Failed to find expected output hello.out.csv"
[ ! -f hello.out.txt ] && err "Failed to find expected output hello.out.txt"

[ ! -f hello.out.txt.world ] && err "Failed to find expected output hello.out.txt.world"

[ "hello" == `cat hello.out.txt.world` ] || err "Bad contents of output file: expected 'hello'"
