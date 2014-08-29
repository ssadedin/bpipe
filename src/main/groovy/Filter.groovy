import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.codehaus.groovy.transform.GroovyASTTransformationClass;

/**
 * Annotation to indicate that a pipeline stage transforms an input
 * to a file type with a different extension
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.LOCAL_VARIABLE)
@GroovyASTTransformationClass("bpipe.ast.BpipeFilterASTTransformation")
public @interface Filter {
    String value()
}
