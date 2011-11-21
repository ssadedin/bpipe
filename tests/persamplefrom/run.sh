source ../testsupport.sh

rm -f *align*

run s_*.txt

grep -q "Stage dedupe" test.out || err "Failed to find expected stage dedupe"
grep -q "Stage align" test.out || err "Failed to find expected stage align"
grep -q "Stage compute_statistics" test.out || err "Failed to find expected stage compute_statistics"


[ ! -f s_1_1.align.txt ] && err "Failed to find expected output s_1_1.align.txt"
[ ! -f s_2_1.align.txt ] && err "Failed to find expected output s_2_1.align.txt"

[ ! -f s_1_1.align.dedupe.txt ] && err "Failed to find expected output s_1_1.align.dedupe.txt"
[ ! -f s_2_1.align.dedupe.txt ] && err "Failed to find expected output s_2_1.align.dedupe.txt"

[ ! -f s_1_1.align.dedupe.txt.compute_statistics ] && err "Failed to find expected output s_1_1.align.dedupe.txt.compute_statistics"

true
