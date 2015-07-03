package nez.debugger;

import nez.lang.Expression;

public abstract class Instruction {
	Opcode op;
	Expression expr;
	public Instruction(Expression e) {
		this.expr = e;
	}
	
	public Expression getExpression() {
		return this.expr;
	}
	
	public abstract void stringfy(StringBuilder sb);
}
