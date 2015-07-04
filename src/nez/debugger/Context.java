package nez.debugger;

import nez.ast.Source;

public abstract class Context implements Source {
	long pos;
	long longest_pos;
	boolean result;
	StackEntry[] stack = null;
	int StackTop;
	private static int StackSize = 128;

	public final void initContext() {
		this.result = true;
		this.stack = new StackEntry[StackSize];
		this.stack[0].jump = new Iexit(null);
		this.StackTop = 0;
	}

	public final long getPosition() {
		return this.pos;
	}

	final void setPosition(long pos) {
		this.pos = pos;
	}

	public boolean hasUnconsumed() {
		return this.pos != length();
	}

	public final boolean consume(int length) {
		this.pos += length;
		return true;
	}

	public final void rollback(long pos) {
		if (this.longest_pos < this.pos) {
			this.longest_pos = this.pos;
		}
		this.pos = pos;
	}

	public final String getSyntaxErrorMessage() {
		return this.formatPositionLine("error", this.longest_pos, "syntax error");
	}

	public final String getUnconsumedMessage() {
		return this.formatPositionLine("unconsumed", this.pos, "");
	}

	public final Instruction opIexit(Iexit inst) {
		return null;
	}

	public final Instruction opIcall(Icall inst) {
		return null;
	}

	public final Instruction opIret(Iret inst) {
		return null;
	}

	public final Instruction opIjump(Ijump inst) {
		return null;
	}

	public final Instruction opIiffail(Iiffail inst) {
		return null;
	}

	public final Instruction opIpush(Ipush inst) {
		return null;
	}

	public final Instruction opIpop(Ipop inst) {
		return null;
	}

	public final Instruction opIpeek(Ipeek inst) {
		return null;
	}

	public final Instruction opIsucc(Isucc inst) {
		return null;
	}

	public final Instruction opIfail(Ifail inst) {
		return null;
	}

	public final Instruction opIchar(Ichar inst) {
		return null;
	}

	public final Instruction opIcharclass(Icharclass inst) {
		return null;
	}

	public final Instruction opIany(Iany inst) {
		return null;
	}
}

class StackEntry {
	Instruction jump;
	long pos;
}
