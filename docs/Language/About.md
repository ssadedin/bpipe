# The about statement

### Synopsis

    
    
        about title: <title for pipeline>
    
  

### Behavior

The *about* statement defines pipeline level documentation for a pipeline file.  It can be used any where at the top level of a pipeline file.  At the moment, *title* is the only supported attribute for pipeline documentation.

Pipeline documentation is used in the HTML report that can be generated using the [run](Commands/run) command.

### Examples

**Add a title to a pipeline**
```groovy 

about title: "Exome Variant Calling Pipeline"

run { align_bwa + picard_dedupe + gatk_call_variants }
```
