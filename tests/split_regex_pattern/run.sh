source ../testsupport.sh

run test_*.txt

exists test_bar.hello.csv 

exists test_foo.hello.csv

true
