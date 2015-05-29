# The uses statement

### Synopsis

    
    
      uses(<resource>,<resource>...) {
         < statements that use resources >
      }
    

### Availability

0.9.8_beta_3 and higher

### Behavior

The *uses* statement declares that the enclosed block will use the resources declared in brackets. The resources are specified in the form *type*:integer where predefined types are "threads", "MB" or "GB". The latter two refer to memory. Custom resource types can be used by simply specifying arbitrary names for the resources.

Once resources have been declared, any [exec](Language/Exec) statement that appears in the body is assumed to require the resources and will block if the more of the resource are concurrently in use than allowed by the maximums specified on the command line, or in configuration (bpipe.config).

The purpose of this statement is to help you control concurrency to achieve better utilization and prevent over-utilization of server resources, particularly when different parts of your pipeline have different resource needs. Some stages may be able to run many instances in parallel without overloading your server, while others may only be able to run a small number in parallel without overloading the system. You can solve these problems to get consistently high utilization throughout your pipeline by adding *uses* blocks around key parts of your pipeline.

### Examples

**Run bwa with 4 threads, ensuring that no more than 16GB, 12 threads and 3 temporary files are in use at any one time**
```groovy 

  run_bwa = {
    uses(threads:4,GB:4,tempfiles:3) {
        exec "bwa aln -t 4 ref.fa $input.fq"
    }
  }
```

Note that "tempfiles" is a custom resource (bwa does not really create temporary files, we just do this for the sake of example).

The pipeline would be executed with:

```groovy 

    bpipe run pipeline.groovy -n 12 -m 16384 -l tempfiles=3 test.fq 
```
