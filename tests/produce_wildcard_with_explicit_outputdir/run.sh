source ../testsupport.sh

# Run first time - should create files
bpipe run test.groovy > test.out

exists output_dir/test1.foo test*.txt

# Run second time - should SKIP creating files
rm commandlog.txt

echo "============" >> test.out
echo "============ RUN 2" >> test.out
echo "============" >> test.out

bpipe run test.groovy  >> test.out

grep -q touch commandlog.txt && err "Command to create output_dir/test1.foo was issued again on second run"

true
