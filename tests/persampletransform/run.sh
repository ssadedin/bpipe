source ../testsupport.sh

. ./cleanup.sh

run s_1_1.txt  s_1_2.txt  s_2_1.txt  s_2_2.txt

grep -q "Stage align" test.out || err "Failed to find expected stage align"

[ ! -f s_1_1.chr1.bam ] && err "Failed to find expected output s_1_1.align.txt"

true
