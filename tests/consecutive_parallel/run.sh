source ../testsupport.sh

. ./cleanup.sh

run

grep -q "Stage hello" test.out || err "Failed to find expected stage hello"

cat test.out | grep -o "Stage hello_." | sort | uniq -c > counts.txt

for c in a b c d e ;
do
  grep -q "1 Stage hello_$c" counts.txt || err "Wrong counts for stage $c"
done

true 
