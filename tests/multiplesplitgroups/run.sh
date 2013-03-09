source ../testsupport.sh

run s_*.txt

grep -q "INPUTS: s_2_ignore_b_1.txt s_2_ignore_b_2.txt" test.out || err "Failed to find correct split group 2.b"
grep -q "INPUTS: s_1_ignore_b_1.txt s_1_ignore_b_2.txt" test.out || err "Failed to find correct split group 1.b"
grep -q "INPUTS: s_2_ignore_a_1.txt s_2_ignore_a_2.txt" test.out || err "Failed to find correct split group 2.a"
grep -q "INPUTS: s_1_ignore_a_1.txt s_1_ignore_a_2.txt" test.out || err "Failed to find correct split group 1.a"

true
