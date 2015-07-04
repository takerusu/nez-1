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
		for(int i = 0; i < this.stack.length; i++) {
			this.stack[i] = new StackEntry();
		}
		this.stack[0].jump = new Iexit(null);
		this.stack[0].failjump = new Iexit(null);
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
		if(this.longest_pos < this.pos) {
			this.longest_pos = this.pos;
		}
		this.pos = pos;
	}

	public final StackEntry newStackEntry() {
		this.StackTop++;
		if(this.StackTop == this.stack.length) {
			StackEntry[] newStack = new StackEntry[this.stack.length * 2];
			System.arraycopy(this.stack, 0, newStack, 0, stack.length);
			for(int i = this.stack.length; i < newStack.length; i++) {
				newStack[i] = new StackEntry();
			}
			this.stack = newStack;
		}
		return this.stack[this.StackTop];
	}

	public final StackEntry popStack() {
		return this.stack[this.StackTop--];
	}

	public final StackEntry peekStack() {
		return this.stack[this.StackTop];
	}

	public final String getSyntaxErrorMessage() {
		return this.formatPositionLine("error", this.longest_pos, "syntax error");
	}

	public final String getUnconsumedMessage() {
		return this.formatPositionLine("unconsumed", this.pos, "");
	}

	public final DebugVMInstruction opIexit(Iexit inst) throws MachineExitException {
		throw new MachineExitException(result);
	}

	public final DebugVMInstruction opIcall(Icall inst) {
		StackEntry top = this.newStackEntry();
		top.jump = inst.jump;
		top.failjump = inst.failjump;
		return inst.next;
	}

	public final DebugVMInstruction opIret(Iret inst) {
		StackEntry top = this.popStack();
		if(this.result) {
			return top.jump;
		}
		return top.failjump;
	}

	public final DebugVMInstruction opIjump(Ijump inst) {
		return inst.jump;
	}

	public final DebugVMInstruction opIiffail(Iiffail inst) {
		if(this.result) {
			return inst.next;
		}
		return inst.jump;
	}

	public final DebugVMInstruction opIpush(Ipush inst) {
		StackEntry top = this.newStackEntry();
		top.pos = this.pos;
		return inst.next;
	}

	public final DebugVMInstruction opIpop(Ipop inst) {
		this.popStack();
		return inst.next;
	}

	public final DebugVMInstruction opIpeek(Ipeek inst) {
		this.pos = this.peekStack().pos;
		return inst.next;
	}

	public final DebugVMInstruction opIsucc(Isucc inst) {
		this.result = true;
		return inst.next;
	}

	public final DebugVMInstruction opIfail(Ifail inst) {
		this.result = false;
		return inst.next;
	}

	public final DebugVMInstruction opIchar(Ichar inst) {
		if(this.byteAt(this.pos) == inst.byteChar) {
			this.consume(1);
			return inst.next;
		}
		this.result = false;
		return inst.jump;
	}

	public final DebugVMInstruction opIcharclass(Icharclass inst) {
		int byteChar = this.byteAt(this.pos);
		if(inst.byteMap[byteChar]) {
			this.consume(1);
			return inst.next;
		}
		this.result = false;
		return inst.jump;
	}

	public final DebugVMInstruction opIany(Iany inst) {
		if(hasUnconsumed()) {
			this.consume(1);
			return inst.next;
		}
		this.result = false;
		return inst.jump;
	}
}

class StackEntry {
	DebugVMInstruction jump;
	DebugVMInstruction failjump;
	long pos;
}
