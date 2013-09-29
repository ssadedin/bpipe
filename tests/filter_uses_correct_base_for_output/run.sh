source ../testsupport.sh

run foo.txt test.bed 

# the output file is a filter, which acts on test.bed, so the output should be 
# test.hello.bed and NOT foo.hello.bed

exists test.hello.bed
