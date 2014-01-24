source ../testsupport.sh

bpipe run -l db=1 test.groovy > test.out

for i in foo bar tree;
do
    grep -A 1 "Using resource $i" test.out | grep -q "End using resource $i" || err "Failed to find end using resource directly after start using resource with concurrency 1"
done

true


