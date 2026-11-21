source ../testsupport.sh

# 1. make_bam's declared outputs are staged, along with index files for them that are
#    not declared outputs: test.bam.bai (default bam => bai) and test.didx (dat => didx
#    from bpipe.config, using the "replace extension" naming form)
bpipe run -r --staged staged test.groovy test.txt > test.out 2>&1 || err "Pipeline failed with staged outputs"

[ -e doc/index.html ] || err "No HTML report was generated for test"

grep -q "Stage make_bam using staged outputs" test.out || err "Failed to find staging message for stage make_bam"
grep -q "also copying staged index files: test.bam.bai, test.didx" test.out || err "Failed to find message listing staged index files"

grep -q "STAGED bam" test.bam || err "test.bam was not copied from staging"
grep -q "STAGED bai" test.bam.bai || err "Default index test.bam.bai was not copied from staging"
grep -q "STAGED didx" test.didx || err "Configured index test.didx was not copied from staging"
notexists test.bam.flagstat

grep -q "STAGED bai" result.txt || err "Downstream stage did not see the staged index"
grep -q "REAL" result.txt && err "Downstream stage saw real outputs, but make_bam should have been staged"

# The index must not be older than the file it indexes
[ test.bam -nt test.bam.bai ] && err "Index file is older than the file it indexes"

# 2. Test mode reports the index files that would be copied, without copying anything
rm -rf .bpipe test.bam test.bam.bai test.dat test.didx result.txt
bpipe test --staged staged test.groovy test.txt > test.out2 2>&1
grep -q "with index files: test.bam.bai, test.didx" test.out2 || err "Test mode did not report staged index files"
notexists test.bam.bai

true
