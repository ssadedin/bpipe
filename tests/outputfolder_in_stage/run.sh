source ../testsupport.sh

run 

exists world_dir/hello.txt 
exists mars_dir/hello.foo.csv
exists hello.csv

true
