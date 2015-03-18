# The Requires Statement

    
    requires < variable name > : Message to display if value not provided

### Availability

Bpipe version 0.9.8.6

### Behavior

Specify a parameter or variable that must be provided for this pipeline stage to run. The variable can be provided with a [Using](Language/Using) construct, by passing a value on the command line (--param option), or simply by defining the variable directly in the pipeline script. If the variable is not defined by one of these mechanisms, Bpipe prints out an error message to the user, including the message defined by the `requires` statement.

### Examples

**This Pipeline will Print an Error because WOLRD is not defined anywhere**
```groovy 

hello = {
    requires WORLD : "Please specify which planet to say hello to"

    doc "Run hello with to $WORLD"

    exec """
        echo hello $WORLD
    """
}
run { hello }
```

**Say hello to mars**
Note that the script below is identical to the example above, except for the "run" portion.
```groovy 

hello = {
    requires WORLD : "Please specify which planet to say hello to"

    doc "Run hello with to $WORLD"

    exec """
        echo hello $WORLD
    """
}
run { hello.using(WORLD: "mars") }
```

**Say hello to mars with a directly defined variable **
Note that the script below is identical to the example above, except for the "run" portion.
```groovy 

hello = {
    requires WORLD : "Please specify which planet to say hello to"

    doc "Run hello with to $WORLD"

    exec """
        echo hello $WORLD
    """
}

WORLD="mars"

run { hello }
```
