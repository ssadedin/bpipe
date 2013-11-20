source ../testsupport.sh

run test.txt

exists test.chr2.hello.csv

notexists test.chr1.hello.csv

true
