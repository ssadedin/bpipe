source ../testsupport.sh

run test1.txt test2.txt

# Note: this used to be test1.hello.goo.txt, 
# but since the output is not derived from any input passing through the 
# 'hello' stage, it actually makes more sense to be test1.goo.txt.
# That change happened when another bug related to filters 
# was fixed - see filter_uses_correct_base_for_output test.
exists test1.goo.txt

grep -q "failed" test.out && err "Pipeline failed but should have succeeded"

true
