source ../testsupport.sh

run test1.txt  test1.csv test2.txt 

exists test1.hello.xml

([ `head -n 1 test1.hello.xml` == "2" ] && [ `tail -n 1 test1.hello.xml` == "3" ] && [ `wc -l test1.hello.xml | awk '{print $1}'` ==  2 ]) || err "Contents of output file test1.hello.xml didn't match expected"

true

