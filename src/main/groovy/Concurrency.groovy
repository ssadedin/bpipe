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
import org.codehaus.groovy.transform.GroovyASTTransformationClass

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * Annotation to limit the number of instances of a pipeline stage that
 * are allowed to execute at the same time.
 * <p>
 * The stage:
 * <code>
 *    @Concurrency(4)
 *    align = {
 *       ...
 *    }
 * </code>
 * <p>
 * will never run more than 4 of its instances concurrently, no matter how
 * much parallelism the rest of the pipeline generates. Instances that arrive
 * when all slots are taken wait until one frees up. Stages are also sometimes
 * declared with the lowercase alias, ie:
 * <code>
 *    @concurrency(4)
 * </code>
 * <p>
 * The limit is enforced in addition to the overall concurrency set with the
 * <code>-n</code> flag, so the effective number of instances is the lesser of
 * the two. See {@link bpipe.ast.BpipeConcurrencyASTTransformation} for how the
 * annotation is implemented and {@link bpipe.PipelineContext#concurrency} for
 * the runtime side.
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.LOCAL_VARIABLE)
@GroovyASTTransformationClass( "bpipe.ast.BpipeConcurrencyASTTransformation" )
public @interface Concurrency {
    int value()
}
