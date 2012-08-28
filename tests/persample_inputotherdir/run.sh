source ../testsupport.sh

. ./cleanup.sh

run childdir/s_*.txt

grep -q "Stage dedupe" test.out || err "Failed to find expected stage dedupe"
grep -q "Stage align" test.out || err "Failed to find expected stage align"


[ ! -f s_1_1.align.csv ] && err "Failed to find expected output s_1_1.align.csv"
[ ! -f s_2_1.align.csv ] && err "Failed to find expected output s_2_1.align.csv"

[ ! -f s_1_1.txt.dedupe ] && err "Failed to find expected output s_1_1.txt.dedupe"
[ ! -f s_2_1.txt.dedupe ] && err "Failed to find expected output s_2_1.txt.dedupe"

true
