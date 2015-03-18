# Branch Variables

A common need is to communicate information between disparate parts of your pipeline. Often this information is scoped to the context of a particular branch or segment of your pipeline. Bpipe supports this by the use of "branch variables".

### Creating Branch Variables

Each branch of a pipeline (including the root), is passed an implicit variable called 'branch'. If referenced as a value this variable evaluates to the name of the branch in which the stage is running. For example, if you have split by chromosome using the [chr](Language/Chr) command, the name of the branch is the chromosome for which the branch is executing. If you have split by file then it is the name of the file (or unique portion thereof) that is specific to the branch.

The `branch` variable, however, also acts as a container for your own properties. Thus you can set properties on it using normal Groovy code:

```groovy 

hello = {
    branch.planet = "mars"
}
```

### Referencing Branch Variables

Once set, a branch variable becomes referenceable directly by all subsequent stages that are part of the branch, including any child branches. The value can be referenced as a property on the `branch` object, for example '`branch.planet`', but can also be referenced without the `branch.` prefix.  For example, the following pipeline prints "hello mars":

```groovy 

hello = {
    branch.planet = "mars"
}

world = {
    exec """echo "Hello $planet" """
}

run { hello + world }
```
