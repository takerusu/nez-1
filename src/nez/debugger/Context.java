package nez.debugger;

import nez.ast.Source;

public abstract class Context implements Source {
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
