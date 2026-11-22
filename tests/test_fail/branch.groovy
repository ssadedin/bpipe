hello = {
    fail "Sample $branch.name is bad"
}

run { ['s1','s2'] * [ hello ] }
