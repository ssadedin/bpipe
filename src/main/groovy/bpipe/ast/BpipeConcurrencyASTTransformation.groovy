/*
 * Copyright (c) Murdoch Childrens Research Institute and Contributers
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice, this
 * list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package bpipe.ast

import bpipe.PipelineError;

import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.transform.GroovyASTTransformation

/**
 * Implements the <code>@Concurrency</code> / <code>@concurrency</code> annotation,
 * which restricts the number of instances of a pipeline stage that can run at the
 * same time.
 * <p>
 * Like the other stage annotations this rewrites the annotated stage into a call
 * to a "magic" method that wraps the original stage body:
 * <p>
 * <code>
 *     align = bpipe.Pipeline.declarePipelineStage("align", {
 *         concurrency__bpipe_annotation(4, {
 *             .. original stage body ..
 *         })
 *     })
 * </code>
 * <p>
 * The call is routed through {@link bpipe.PipelineDelegate} to
 * {@link bpipe.PipelineContext#concurrency(Object,Closure)}, which is where the
 * actual limiting happens.
 *
 *  @author ssadedin@mcri.edu.au
 */
@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
class BpipeConcurrencyASTTransformation extends BpipeASTTransformation {

    public BpipeConcurrencyASTTransformation() {
        super("concurrency");
    }

    /**
     * The concurrency annotation takes an integer (the maximum number of instances
     * allowed to run at once) rather than the file patterns taken by the other
     * annotations, so we can't use the value processing of the parent class.
     */
    @Override
    protected String getAnnotationValue(String closureUpper, AnnotationNode ann) {

        def member = ann.members.value

        if(!member)
            throw new PipelineError("The @concurrency annotation requires a maximum number of instances, eg @concurrency(4)")

        if(!(member instanceof ConstantExpression))
            throw new PipelineError("The value of the @concurrency annotation must be a constant number, eg @concurrency(4)")

        def value = ((ConstantExpression)member).value

        if(!(value instanceof Number))
            throw new PipelineError("The value of the @concurrency annotation must be a number, but it is a ${value?.getClass()?.name ?: 'null'}")

        int maxInstances = ((Number)value).intValue()

        if(maxInstances < 1)
            throw new PipelineError("The @concurrency annotation specifies $maxInstances instances: please specify a number of 1 or more")

        return String.valueOf(maxInstances)
    }
}
