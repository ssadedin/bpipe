# The Load Statement
    
    debug()

### Behavior

Opens a [Groovy Console](http://groovy-lang.org/groovyconsole.html) at a point in the pipeline
flow to manual allow inspection of variables and pipeline state.

The variables 'pipeline' and 'context' are made available for exploration of the pipeline
state.

### Examples

**1. Include a file "dependencies.groovy" explicitly into your pipeline**
```groovy 

hello = {
    exec """
        echo hello
    """

    debug()
}

run {
  hello 
}
```

