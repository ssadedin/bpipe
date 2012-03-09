source ../testsupport.sh

rm foo.txt bar.txt foo.csv  foo.txt.world 

run test.txt

grep -q "Stage hello" test.out || err "Failed to find expected stage hello"
grep -q "Stage world" test.out || err "Failed to find expected stage world"

[ ! -f foo.txt.world ] && err "Failed to find expected output foo.txt.world"

sort foo.txt.world | uniq -c | grep -q "2 hello" || err "Expect hello repeated exactly 2 times because of 2 txt files in prior stage"

true
