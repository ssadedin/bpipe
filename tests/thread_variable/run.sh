source ../testsupport.sh

bpipe run -n 10 test.groovy > test.out

exists hello hello2 hello3 hello4

# each one should have gotten 3 threads (10/3)

[ `cat hello` == 3 ] || [ `cat hello` == 4 ] || echo "Wrong number of threads assigned to thread variable in multi statement"

[ `cat hello2` == 3 ] || [ `cat hello2` == 4 ] || echo "Wrong number of threads assigned to thread variable in multi statement"

[ `cat hello3` == 3 ] || [ `cat hello3` == 4 ] || echo "Wrong number of threads assigned to thread variable in multi statement"

# the last one gets 10 threads because only a single command ran (exec)
[ `cat hello4` == 10 ] || echo "Wrong number of threads assigned to thread variable in exec statement"

