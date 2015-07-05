package nez.main;

import nez.debugger.DebugInputManager;
import nez.lang.Grammar;

public class LCndb extends Command {
	@Override
	public String getDesc() {
		return "Nez debugger";
	}

	@Override
	public void exec(CommandContext config) {
		// config.setNezOption(NezOption.DebugOption);
		Command.displayVersion();
		config.getNezOption().setOption("asis", true);
		config.getNezOption().setOption("intern", false);
		Grammar peg = config.getGrammar();
		DebugInputManager manager = new DebugInputManager(config.inputFileLists);
		manager.exec(peg);
	}
}