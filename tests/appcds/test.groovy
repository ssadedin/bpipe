// Simple pipeline to verify AppCDS does not break normal execution
hello = {
    exec "echo hello"
}

run { hello }
