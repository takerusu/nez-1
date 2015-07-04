package nez.debugger;

import nez.lang.AnyChar;
import nez.lang.ByteChar;
import nez.lang.ByteMap;
import nez.lang.Expression;
import nez.lang.NonTerminal;

public class IRBuilder {
	private BasicBlock curBB;
	private Module module;

	public IRBuilder(Module m) {
		this.module = m;
	}

	public Module getModule() {
		return this.module;
	}

	public void setCurrentBB(BasicBlock bb) {
		this.curBB = bb;
	}

	public BasicBlock getCurrentBB() {
		return this.curBB;
	}

	public Instruction createIexit(Expression e) {
		return this.curBB.append(new Iexit(e));
	}

	public Instruction createIcall(NonTerminal e, BasicBlock jump, BasicBlock failjump) {
		return this.curBB.append(new Icall(e, jump, failjump));
	}

	public Instruction createIret(Expression e) {
		return this.curBB.append(new Iret(e));
	}

	public Instruction createIjump(Expression e, BasicBlock jump) {
		return this.curBB.append(new Ijump(e, jump));
	}

	public Instruction createIiffail(Expression e, BasicBlock jump) {
		return this.curBB.append(new Iiffail(e, jump));
	}

	public Instruction createIpush(Expression e) {
		return this.curBB.append(new Ipush(e));
	}

	public Instruction createIpop(Expression e) {
		return this.curBB.append(new Ipop(e));
	}

	public Instruction createIpeek(Expression e) {
		return this.curBB.append(new Ipeek(e));
	}

	public Instruction createIsucc(Expression e) {
		return this.curBB.append(new Isucc(e));
	}

	public Instruction createIfail(Expression e) {
		return this.curBB.append(new Ifail(e));
	}

	public Instruction createIchar(ByteChar e, BasicBlock jump) {
		return this.curBB.append(new Ichar(e, jump));
	}

	public Instruction createIcharclass(ByteMap e, BasicBlock jump) {
		return this.curBB.append(new Icharclass(e, jump));
	}

	public Instruction createIany(AnyChar e, BasicBlock jump) {
		return this.curBB.append(new Iany(e, jump));
	}
}
