source ../testsupport.sh

run test.txt

exists test.chr1.foo.hello.txt 
exists test.chr2.foo.hello.txt 

true

