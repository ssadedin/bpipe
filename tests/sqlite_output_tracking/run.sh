source ../testsupport.sh

rm -rf .bpipe doc test.txt.hello test.txt.hello.world test.out query.test.out cleanup.out

# Create the input file that the pipeline needs
echo "test data" > test.txt

# First run: pipeline should succeed and create outputs.db
echo "=== First run ==="
bpipe run -r test.groovy test.txt > test.out 2>&1

grep -q "Pipeline Succeeded" test.out || err "First pipeline run did not succeed"
grep -q "Stage hello" test.out || err "Did not find stage hello in output"
grep -q "Stage world" test.out || err "Did not find stage world in output"
[ -f test.txt.hello ] || err "First run did not create test.txt.hello"
[ -f test.txt.hello.world ] || err "First run did not create test.txt.hello.world"

# Verify SQLite database was created
[ -f .bpipe/outputs/outputs.db ] || err "SQLite database was not created at .bpipe/outputs/outputs.db"
[ -f .bpipe/outputs/outputs.db-journal ] || true  # WAL journal may or may not exist

# Verify NO property files were created (we're using SQLite backend now)
prop_count=$(ls .bpipe/outputs/*.properties 2>/dev/null | wc -l)
if [ "$prop_count" -gt 0 ]; then
    err "Found $prop_count property files despite using SQLite backend (expected 0)"
fi

# Verify query works
echo "=== Query test ==="
bpipe query > query.test.out 2>&1 || err "bpipe query failed"
grep -q "test.txt.hello" query.test.out || err "bpipe query did not show test.txt.hello"

# Verify cleanup works (no intermediate files - should find nothing to clean)
echo "=== Cleanup test ==="
bpipe cleanup -y > cleanup.out 2>&1

# Second run: should skip both stages (outputs up to date)
echo "=== Second run (should skip) ==="
bpipe run -r test.groovy test.txt > test.out 2>&1
execute_count=$(grep -c "execute" test.out || true)
if [ "$execute_count" -ne 0 ]; then
    err "Second run re-executed stages when outputs were up to date (executed $execute_count times)"
fi

true