

var FAIL_BEFORE_RUN : false

if(FAIL_BEFORE_RUN)
    fail "I failed before execution"

hello = {
    fail "I failed at the first step"
}

run { hello }
