source ../testsupport.sh

run 

exists hello.txt hello.there.csv hello.there.world.xml

bpipe remake hello.there.csv > test.out

[ `grep -c hello hello.txt` == 1 ] || err "hello.txt was remade but it shouldn't have been"

[ `grep -c hello hello.there.csv` == 2 ] || err "csv was not remade as expected"

[ `grep -c hello hello.there.world.xml` == 3 ] || err "xml was not remade as expected"

true


