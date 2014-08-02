source ../testsupport.sh

run test.txt

exists test.aaa.hello.csv
exists 	test.bbb.hello.csv
exists 	test.ccc.hello.csv
exists 	test.ddd.hello.csv
exists 	test.hello.csv

true
