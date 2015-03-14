# The Load Statement

    
    load < file name >
### Behavior

Imports the pipeline stages, variables and functions in the specified file into your pipeline script.

*Note*: currently the contents of files loaded using `load` are not imported directly into the file as if included literally. Instead they are scheduled to be imported when you construct a pipline. This means that when you execute a `run` or `segment` command, Bpipe loads them at that point to make them available within the scope of the run or segment statement. The result of this is that you cannot refer to them with global scope directly. See Example 2 to understand this more.

*Note*: As of Bpipe 0.9.8.5, a file loaded with `load` can itself contain `load` statements so that you can build multiple levels of dependencies. Use this feature with caution, however, as there is no checking for cyclic depenencies, and thus it is possible to put Bpipe into an infinite loop by having two files that load each other.

### Examples

**1. Include a file "dependencies.groovy" explicitly into your pipeline**
```groovy 

load "dependencies.groovy"

run {
  hello + world // hello and world are defined in dependencies.groovy
}
```

**2. Refer to a Variable Defined in an External File**

`dependencies.groovy:`

```groovy 

INCLUDE_REALIGNMENT=true
```

```groovy 

load "dependencies.groovy"

// This will not work! The INCLUDE_REALIGNMENT variable is not defined yet
//if(INCLUDE_REALIGNMENT)
//  alignment = segment { align + realign }
//else
//  alignment = segment { align }

// This will work - INCLUDE_REALIGNMENT is defined inside segment {}
alignment = segment {
  if(INCLUDE_REALIGNMENT)
    align + realign
  else
    align
}

run {
  alignment
}
```
