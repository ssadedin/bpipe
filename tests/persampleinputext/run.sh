source ../testsupport.sh

rm -f *align* *foo*

run s_*.txt

grep -q "Stage dedupe" test.out || err "Failed to find expected stage dedupe"
grep -q "Stage align" test.out || err "Failed to find expected stage align"


[ ! -f s_2_1.foo.txt ] && err "Failed to find expected output s_2_1.foo.txt"
[ ! -f s_3_1.foo.txt ] && err "Failed to find expected output s_3_1.foo.txt"

true
