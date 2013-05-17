source ../testsupport.sh

run test1.txt  test1.csv test2.txt 

exists test1.hello.xml

[ `cat test1.hello.xml | groovy -e 'print(System.in.readLines()[0..1] == ["2","3"])'` == true ] || err "Contents of output file test1.hello.xml didn't match expected"

true

