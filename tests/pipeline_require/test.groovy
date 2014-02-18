
requires planet : "The planet to say hello to"

hello = {
    exec """ echo 'hello $planet' """
}

run { hello }
