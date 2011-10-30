package bpipe.ast

import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.expr.BinaryExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;

class ASTDumper {

	static dumpNode(ASTNode n, int level=0) {
        
		println(("\t" * level) + " " + n.toString().replaceAll("org.codehaus.groovy.ast.",""))

		if(!n)
			return null

		if(n instanceof BlockStatement)
			n.statements.each {  dumpNode(it, level+1) }

		if(n instanceof ExpressionStatement)
			dumpNode(n.expression, level+1)

		if(n instanceof BinaryExpression)
			[n.leftExpression, n.rightExpression].each {  dumpNode(it, level+1 ) }

		if(n instanceof ClosureExpression)
			dumpNode(n.code, level+1)

		if(n instanceof PropertyExpression)
			[n.objectExpression, n.property].each {  dumpNode(it, level+1 ) }
	}

}
