source ../testsupport.sh

run test.txt

exists out/test.csv
notexists test.csv

true
