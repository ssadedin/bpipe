source ../testsupport.sh

run

grep -q 'hello bar.sample1.txt -> sample1/house' test.out  || err "Expected message not printed for channel / branch"

grep -q 'hello bar.sample2.txt -> sample2/house' test.out  || err "Expected message not printed for channel / branch"

exists bar.sample1.sample1.house.txt

true

