source ../testsupport.sh

run test1.csv test2.csv test3.csv test4.csv

exists *.world.xml

grep -q test3 test3.world.xml || err "Wrong contents of test3.world.xml - wrong input file used?"
grep -q test4 test3.world.xml || err "Wrong contents of test3.world.xml - wrong input file used?"

true
