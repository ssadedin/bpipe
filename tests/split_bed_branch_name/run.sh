source ../testsupport.sh

run test.txt

[ `ls test.[0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f].hello.csv | wc -l` -eq 3 ]  || err "Did not observe region branch specific output files as expected"

true
