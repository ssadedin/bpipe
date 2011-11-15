source ../testsupport.sh

rm -f test.txt.* 
rm -rf output

bpipe run -d output test.groovy test.txt > test.out 2>&1


grep -q "Stage hello" test.out || err "Failed to find expected stage hello"
grep -q "Stage world" test.out || err "Failed to find expected stage world"

[ ! -f output/test.txt.hello ] && err "Failed to find expected output output/test.txt"
[ ! -f output/test.txt.hello.world ] && err "Failed to find expected output output/test.world.txt"

true
