source ../testsupport.sh

run

grep -q 'Hello' test.out || err "Skipped unexpected stage Hello"

grep -q '======> Skipping stage world' test.out || err "Did not skip expected stage"

grep -q 'a red planet' test.out || err "Skipped unexpected stage mars"

grep -q '======> Skipping stage mars' test.out && err "printed unexpected skip message"

true


