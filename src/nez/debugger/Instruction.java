package nez.debugger;

import nez.lang.ByteChar;
import nez.lang.ByteMap;
import nez.lang.Expression;
import nez.lang.NonTerminal;
import nez.util.StringUtils;

public abstract class Instruction {
	Opcode op;
	Expression expr;
	Instruction next;

	public Instruction(Expression e) {
		this.expr = e;
	}

	public Expression getExpression() {
		return this.expr;
	}

	public void setNextInstruction(Instruction next) {
		this.next = next;
	}

	public abstract void stringfy(StringBuilder sb);

	@Override
	public abstract String toString();

	public abstract Instruction exec(Context ctx) throws MachineExitException;
}

class Iexit extends Instruction {
	public Iexit(Expression e) {
		super(e);
		this.op = Opcode.Iexit;
	}

	@Override
	public void stringfy(StringBuilder sb) {
		sb.append("Iexit");
	}

	@Override
	public String toString() {
		return "Iexit";
	}

	@Override
	public Instruction exec(Context ctx) throws MachineExitException {
		return ctx.opIexit(this);
	}
}

class Icall extends Instruction {
	NonTerminal ne;
	int jump;

	public Icall(NonTerminal e) {
		super(e);
		this.op = Opcode.Icall;
		this.ne = e;
	}

	public void setJump(int jump) {
		this.jump = jump;
	}

	@Override
	public void stringfy(StringBuilder sb) {
		sb.append("Icall " + ne.getLocalName());
	}

	@Override
	public String toString() {
		return "Icall " + ne.getLocalName() + " (" + this.jump + ")";
	}

	@Override
	public Instruction exec(Context ctx) throws MachineExitException {
		return ctx.opIcall(this);
	}
}

class Iret extends Instruction {
	public Iret(Expression e) {
		super(e);
		this.op = Opcode.Iret;
	}

	@Override
	public void stringfy(StringBuilder sb) {
		sb.append("Iret");
	}

	@Override
	public String toString() {
		return "Iret";
	}

	@Override
	public Instruction exec(Context ctx) throws MachineExitException {
		return ctx.opIret(this);
	}
}

abstract class JumpInstruction extends Instruction {
	BasicBlock jump;

	public JumpInstruction(Expression e, BasicBlock jump) {
		super(e);
		this.jump = jump;
	}

	public BasicBlock getJumpBB() {
		return this.jump;
	}
}

class Ijump extends JumpInstruction {

	public Ijump(Expression e, BasicBlock jump) {
		super(e, jump);
		this.op = Opcode.Ijump;
	}

	@Override
	public void stringfy(StringBuilder sb) {
		sb.append("Ijump (").append(this.jump.getName()).append(")");
	}

	@Override
	public String toString() {
		return "Ijump (" + this.jump.codePoint + ")";
	}

	@Override
	public Instruction exec(Context ctx) throws MachineExitException {
		return ctx.opIjump(this);
	}
}

class Iiffail extends JumpInstruction {
	public Iiffail(Expression e, BasicBlock jump) {
		super(e, jump);
		this.op = Opcode.Iiffail;
	}

	@Override
	public void stringfy(StringBuilder sb) {
		sb.append("Iiffail (").append(this.jump.getName()).append(")");
	}

	@Override
	public String toString() {
		return "Iiffail (" + this.jump.codePoint + ")";
	}

	@Override
	public Instruction exec(Context ctx) throws MachineExitException {
		return ctx.opIiffail(this);
	}
}

class Ipush extends Instruction {
	public Ipush(Expression e) {
		super(e);
		this.op = Opcode.Ipush;
	}

	@Override
	public void stringfy(StringBuilder sb) {
		sb.append("Ipush");
	}

	@Override
	public String toString() {
		return "Ipush";
	}

	@Override
	public Instruction exec(Context ctx) throws MachineExitException {
		return ctx.opIpush(this);
	}
}

class Ipop extends Instruction {
	public Ipop(Expression e) {
		super(e);
		this.op = Opcode.Ipop;
	}

	@Override
	public void stringfy(StringBuilder sb) {
		sb.append("Ipop");
	}

	@Override
	public String toString() {
		return "Ipop";
	}

	@Override
	public Instruction exec(Context ctx) throws MachineExitException {
		return ctx.opIpop(this);
	}
}

class Ipeek extends Instruction {
	public Ipeek(Expression e) {
		super(e);
		this.op = Opcode.Ipeek;
	}

	@Override
	public void stringfy(StringBuilder sb) {
		sb.append("Ipeek");
	}

	@Override
	public String toString() {
		return "Ipeek";
	}

	@Override
	public Instruction exec(Context ctx) throws MachineExitException {
		return ctx.opIpeek(this);
	}
}

class Isucc extends Instruction {
	public Isucc(Expression e) {
		super(e);
		this.op = Opcode.Isucc;
	}

	@Override
	public void stringfy(StringBuilder sb) {
		sb.append("Isucc");
	}

	@Override
	public String toString() {
		return "Isucc";
	}

	@Override
	public Instruction exec(Context ctx) throws MachineExitException {
		return ctx.opIsucc(this);
	}
}

class Ifail extends Instruction {
	public Ifail(Expression e) {
		super(e);
		this.op = Opcode.Ifail;
	}

	@Override
	public void stringfy(StringBuilder sb) {
		sb.append("Ifail");
	}

	@Override
	public String toString() {
		return "Ifail";
	}

	@Override
	public Instruction exec(Context ctx) throws MachineExitException {
		return ctx.opIfail(this);
	}
}

class Ichar extends JumpInstruction {
	int byteChar;

	public Ichar(ByteChar e, BasicBlock jump) {
		super(e, jump);
		this.op = Opcode.Ichar;
		this.byteChar = e.byteChar;
	}

	@Override
	public void stringfy(StringBuilder sb) {
		sb.append("Ichar ").append(StringUtils.stringfyCharacter(this.byteChar)).append(" ")
				.append(this.jump.getName());
	}

	@Override
	public String toString() {
		return "Ichar " + StringUtils.stringfyCharacter(this.byteChar) + " (" + this.jump.codePoint + ")";
	}

	@Override
	public Instruction exec(Context ctx) throws MachineExitException {
		return ctx.opIchar(this);
	}
}

class Icharclass extends JumpInstruction {
	boolean[] byteMap;

	public Icharclass(ByteMap e, BasicBlock jump) {
		super(e, jump);
		this.op = Opcode.Icharclass;
		this.byteMap = e.byteMap;
	}

	@Override
	public void stringfy(StringBuilder sb) {
		sb.append("Icharclass ").append(StringUtils.stringfyCharacterClass(this.byteMap)).append(" ")
				.append(this.jump.getName());
	}

	@Override
	public String toString() {
		return "Icharclass " + StringUtils.stringfyCharacterClass(this.byteMap) + " (" + this.jump.codePoint + ")";
	}

	@Override
	public Instruction exec(Context ctx) throws MachineExitException {
		return ctx.opIcharclass(this);
	}
}

class Iany extends JumpInstruction {
	public Iany(Expression e, BasicBlock jump) {
		super(e, jump);
		this.op = Opcode.Iany;
	}

	@Override
	public void stringfy(StringBuilder sb) {
		sb.append("Iany ").append(this.jump.getName());
	}

	@Override
	public String toString() {
		return "Iany (" + this.jump.codePoint + ")";
	}

	@Override
	public Instruction exec(Context ctx) throws MachineExitException {
		return ctx.opIany(this);
	}
}