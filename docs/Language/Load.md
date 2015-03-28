# The Load Statement
    
    load < file name >
### Behavior

Imports the pipeline stages, variables and functions in the specified 
file into your pipeline script.

Since 0.9.8.7, the `load` statement can be used at either the top level 
(importing whole files into global scope) or within a pipeline stage. 
When inside a pipeline stage, the `load` statement imports the variables
and pipeline stages inside the file into the branch scope of the current 
branch. This can allow a particular branch of your pipeline to override
pipeline stages and variables within that branch to have different 
values and behavior.

### Examples

**1. Include a file "dependencies.groovy" explicitly into your pipeline**
```groovy 

load "dependencies.groovy"

run {
  hello + world // hello and world are defined in dependencies.groovy
}
```

**2. Refer to a Variable Defined in an External File**

`config.groovy:`

```groovy 

INCLUDE_REALIGNMENT=true
```

`main pipeline script:`

```groovy 

load "config.groovy"

// Use realignment if the INCLUDE_REALIGNMENT was defined
if(INCLUDE_REALIGNMENT)
  alignment = segment { align + realign }
else
  alignment = align

run {
  alignment
}
```
