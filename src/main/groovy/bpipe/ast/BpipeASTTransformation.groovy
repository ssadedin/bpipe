package bpipe.ast
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.VariableScope;
import org.codehaus.groovy.ast.builder.AstBuilder;
import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.BinaryExpression;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.ConstructorCallExpression;
import org.codehaus.groovy.ast.expr.DeclarationExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.syntax.Token;
import org.codehaus.groovy.syntax.Types;
import org.codehaus.groovy.transform.ASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;

/**
 * Support for declaring filter and transform modes for pipeline
 * stages using annotations.  As annotations on local variables
 * are not retained at runtime to do this we have to do some
 * voodoo with the AST in the compile phase.  In fact, we replace
 * the closure that comes after the annotation with a new one
 * that calls the appropriate method (filter or transform) 
 * and wraps the old one.
 * 
 * @author simon.sadedin@mcri.edu.au
 */
class BpipeASTTransformation implements ASTTransformation {
    
    String closureName
    
    BpipeASTTransformation(String closureName) {
        super();  
        this.closureName = closureName
    }

    public void visit(ASTNode[] nodes, SourceUnit sourceUnit) {
        
        if(!nodes) {
            println "No nodes"
            return
        }
        
//        println "Processing annotation : "
//        nodes.each { 
//            println it
//        }
        
        if(nodes.size() < 2) {
            return
        }
        
        if(!(nodes[0] instanceof AnnotationNode)) 
            throw new IllegalArgumentException("Expected annotation node but got ${nodes[0]}")
            
        AnnotationNode ann = nodes[0]
        
        String closureUpper = closureName.substring(0,1).toUpperCase() + closureName[1..-1]
        
        String annotationValue = getAnnotationValue(closureUpper, ann)
        
        BlockStatement block = sourceUnit.getAST()?.getStatementBlock()
        if(!block) {
            println "WARN: No main statement block found for annotation"
            return
        }
        
        ASTNode parent = searchExpression(null, block, nodes[1])
        if(!parent) {
            println "ERROR: Could not locate variable defined for $closureUpper"
            return
        }
        
        // Find the Closure
        if(parent.class != ExpressionStatement) {
            println "ERROR: $closureUpper annotates expression of unexpected type"
            return
        }
        
        Expression expr = parent.expression
        if(expr.class != DeclarationExpression) {
            println "ERROR: $closureUpper annotates expression that is not a declaration"
            return
        }
        
        VariableExpression ve = expr.variableExpression
        String stageName = ve.name
        
        if(expr.rightExpression?.class != ClosureExpression) {
            println "ERROR: $closureUpper annotates expression that is not assigned to a closure"
            return
        } 
        
        executeTransformation(stageName, expr, annotationValue)
    }
    
    /**
     * Synthesises an outer closure that invokes the closure that is the right hand side of the 
     * given expression. Essentially we are turning this:
     * <code>
     *   foo = {
     *   }
     * </code>
     *into this:
     *  <code>
     *      foo = {
     *          transform {
     *              [original closure]
     *          }
     *      }
     *  </code>
     * 
     * @param stageName
     * @param expr
     * @param annotationValue
     */
    protected void executeTransformation(String stageName, Expression expr, String annotationValue) {
        // println "================ AST PROCESSING : $closureName ============="
        
        ClosureExpression cls = expr.rightExpression
        if(cls.code?.class != BlockStatement)  {
            println "ERROR: $closureName annotates closure without block (?!)"
            
            return
        } 
        
        BlockStatement clsBlock = cls.code
        def emptyParams = [] as Parameter[]
        def innerClosure = 
	        new ClosureExpression(emptyParams,
                clsBlock
	        )
            
        // Note: failing to set this causes mysterious untraceable NullPointerExceptions later on
        innerClosure.variableScope = new VariableScope()
//        innerClosure.variableScope = new VariableScope(clsBlock.variableScope.parent)
//        clsBlock.variableScope.parent = innerClosure.variableScope
        
        def transformStatement = 
            new ExpressionStatement(
                new MethodCallExpression(new VariableExpression("this"), 
                         new ConstantExpression(this.closureName + '__bpipe_annotation'), 
                         new ArgumentListExpression(
                             new ConstantExpression(annotationValue),
                             innerClosure
                         )
                     )
				)
        
        def createPropertyStatement = createSetProperty(stageName)    
        cls.code = transformStatement

        expr.rightExpression = createStageDeclaration(stageName, expr.rightExpression)
        
    }

	protected String getAnnotationValue(String closureUpper, AnnotationNode ann) {
		if(!ann.members.value)
			throw new IllegalArgumentException("$closureUpper annotation requires a value")

		if(!(ann.members.value instanceof ConstantExpression))
			throw new IllegalArgumentException("$closureUpper annotation requires constant value")

		String transformExtension = ann.members.value.value
		return transformExtension
	}
    
    /**
     * Creates a call to bpipe.Bpipe.declarePipelineStage that wraps 
     * the given closure and returns it.  Results in code like:
     * <p>
     * bpipe.Pipeline.declarePipelineStage("align", cls)
     * <p>
     * which in turn can be used in the assignment to a local variable:
     * <p><code>
     * align = bpipe.Pipeline.declarePipelineStage("align", {
     *  ... code for pipeline stage ...
     * })</code>
     */
    public MethodCallExpression createStageDeclaration(String name, ClosureExpression cls) {
        return new MethodCallExpression(
	            new ClassExpression(new ClassNode("bpipe.Pipeline",1, ClassHelper.OBJECT_TYPE)), 
	            new ConstantExpression("declarePipelineStage"), 
                new ArgumentListExpression(new ConstantExpression(name), cls)
		    )
	}
    
    /**
     * Just for debugging - create a print statement with given message 
     * 
     * @param message
     */
    private Statement createPrintlnAst(String message) {
        return new ExpressionStatement(
            new MethodCallExpression(
                new VariableExpression("this"),
                new ConstantExpression("println"),
                new ArgumentListExpression(
                    new ConstantExpression(message)
                )
            )
        )
    }
    
    /**
     * Trying to set the property directly doesn't seem to work.  Unfortunately it 
     * resolve the variable as not found even though it can be referenced by 
     * the same code if literally entered into the script as
     * 
     * setProperty("foo", foo)
     * 
     * I never unravelled why it doesn't work, there must be some difference
     * between the AST trees produced but I couldn't see any.
     */
    public Statement createSetProperty(String name) {
        return new ExpressionStatement(
            new MethodCallExpression(
                new VariableExpression("this"),
                new ConstantExpression("setProperty"),
                new ArgumentListExpression(
                    new ConstantExpression(name),
                     new VariableExpression(name)
                )
            )
        )
	}
    
    /**
     * Not actually used but a nice example of a generic static method invocation
     */
    public Statement createTransformDeclaration(String name, String ext) {
        
        
        
        
        return new ExpressionStatement(
            new MethodCallExpression(
	            new ClassExpression(new ClassNode("bpipe.Bpipe", 1, new ClassNode("bpipe.Pipeline",1, ClassHelper.OBJECT_TYPE))), 
	            new ConstantExpression("declareTransform"), 
                new ArgumentListExpression(new ConstantExpression(name), new ConstantExpression(ext))
		    )
        )
	}
    
    /**
     * Search for the specified target within the given 
     * parent node.
     */
    def searchExpression(ASTNode parent, ASTNode n, ASTNode target) {
        
//        println "Searching for $target :  parent = $parent, target = $target"
        
        if(!n)
            return null
        
        if(n.is(target))
            return parent
            
        if(n instanceof BlockStatement)
            return n.statements.find {  searchExpression(n, it, target) }
            
        if(n instanceof ExpressionStatement)
            return searchExpression(n, n.expression, target)
        
        if(n instanceof BinaryExpression) 
            return [n.leftExpression, n.rightExpression].find {  searchExpression(n, it, target) }
        
        if(n instanceof ClosureExpression)
            return searchExpression(n, n.code, target)
    }
}