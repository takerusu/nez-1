package nez.debugger;

public class NezDebugger {
	public boolean exec(DebugVMInstruction code, DebugSourceContext sc) {
		boolean result = false;
		try {
			while(true) {
				code = code.exec(sc);
			}
		} catch (MachineExitException e) {
			result = e.result;
		}
		return result;
	}
}
