source ../test_support.sh

run test.txt

grep -q 'test.b.hello.tsv.*->.*test.b.hello.world.xml' test.out || err "Wrong output or branch selected for stage with from pattern and branch spec"
