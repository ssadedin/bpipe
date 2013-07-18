source ../testsupport.sh

. ./cleanup.sh

run s_1_1.txt  s_1_2.txt  s_2_1.txt  s_2_2.txt

grep -q "Stage align" test.out || err "Failed to find expected stage align"

exists s_1_1.chr1.bam 

true
