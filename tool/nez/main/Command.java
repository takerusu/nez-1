package nez.main;

import java.io.IOException;

import nez.ParserGenerator;
import nez.Version;
import nez.ast.Source;
import nez.ext.CommandContext;
import nez.io.CommonSource;
import nez.lang.Grammar;
import nez.parser.Parser;
import nez.parser.ParserStrategy;
import nez.util.ConsoleUtils;
import nez.util.FileBuilder;
import nez.util.UList;
import nez.util.Verbose;

public abstract class Command {

	public final static String ProgName = "Nez";
	public final static String CodeName = "yokohama";
	public final static int MajorVersion = 1;
	public final static int MinerVersion = 0;
	public final static int PatchLevel = nez.Version.REV;

	public static void main2(String[] args) {
		try {
			CommandContext c = new CommandContext();
			c.parseCommandOption(args, true/* nezCommand */);
			Command com = c.newCommand();
			com.exec(c);
		} catch (IOException e) {
			ConsoleUtils.println(e);
			System.exit(1);
		}
	}

	public static void main(String[] args) {
		try {
			Command com = newCommand(args);
			if (Verbose.enabled) {
				Verbose.println("nez-%d.%d.%d %s", MajorVersion, MinerVersion, PatchLevel, com.getClass().getName());
				Verbose.println("strategy: %s", com.strategy);
			}
			com.exec();
		} catch (IOException e) {
			ConsoleUtils.println(e);
			Verbose.traceException(e);
			System.exit(1);
		}
	}

	private static Command newCommand(String[] args) {
		try {
			String className = args[0];
			if (className.indexOf('.') == -1) {
				className = "nez.main.C" + className;
			}
			Command cmd = (Command) Class.forName(className).newInstance();
			cmd.parseCommandOption(args);
			return cmd;
		} catch (Exception e) {
			// Verbose.traceException(e);
			showUsage("unknown command");
		}
		return null;
	}

	public void exec(CommandContext config) throws IOException {
		System.out.println(strategy);
	}

	public void exec() throws IOException {
		System.out.println(strategy);
	}

	protected String command = null;
	protected ParserStrategy strategy = new ParserStrategy();
	protected String grammarFile = null;
	protected String grammarSource = null;
	protected String grammarType = null;
	protected UList<String> grammarFiles = new UList<String>(new String[4]);
	protected String startProduction = null;
	protected UList<String> inputFiles = new UList<String>(new String[4]);
	protected String outputFormat = null;
	protected String outputDirectory = null;

	private void parseCommandOption(String[] args) {
		for (int index = 1; index < args.length; index++) {
			String as = args[index];
			if (index + 1 < args.length) {
				if (as.equals("-g") || as.equals("--grammar") || as.equals("-p")) {
					grammarFile = args[index + 1];
					grammarSource = null;
					index++;
					continue;
				}
				if (as.equals("-e") || as.equals("--expression")) {
					grammarFile = null;
					grammarSource = args[index + 1];
					grammarType = "nez";
					index++;
					continue;
				}
				if (as.equals("-a") || as.equals("--aux")) {
					grammarFiles.add(args[index + 1]);
					index++;
					continue;
				}
				if (as.equals("-s") || as.equals("--start")) {
					startProduction = args[index + 1];
					index++;
					continue;
				}
				if (as.equals("-f") || as.equals("--format")) {
					outputFormat = args[index + 1];
					index++;
					continue;
				}
				if (as.equals("-d") || as.equals("--dir")) {
					outputDirectory = args[index + 1];
					index++;
					continue;
				}
			}
			if (as.equals("--verbose")) {
				Verbose.enabled = true;
				continue;
			}
			if (!strategy.setOption(as)) {
				if (as.equals("-") && as.length() > 1) {
					showUsage("undefined option: " + as);
				}
				this.inputFiles.add(as);
			}
		}
	}

	public final static void displayVersion() {
		ConsoleUtils.println(ProgName + "-" + nez.Version.Version + " (" + CodeName + ") on Java JVM-" + System.getProperty("java.version"));
		ConsoleUtils.println(Version.Copyright);
	}

	protected static void showUsage(String msg) {
		displayVersion();
		ConsoleUtils.println("Usage: nez <command> options inputs");
		ConsoleUtils.println("  -g | --grammar <file>      Specify a grammar file");
		ConsoleUtils.println("  -f | --format <string>     Specify an output format");
		// ConsoleUtils.println("  -e <text>      Specify a Nez parsing expression");
		// ConsoleUtils.println("  -a <file>      Specify a Nez auxiliary grammar files");
		ConsoleUtils.println("  -s | --start <NAME>        Specify a starting production");
		ConsoleUtils.println("  -d | --dir <dirname>       Specify an output dir");
		ConsoleUtils.println("Example:");
		ConsoleUtils.println("  nez parse -g js.nez jquery.js --format json");
		ConsoleUtils.println("  nez match -g js.nez *.js");
		ConsoleUtils.println("  nez parser -g math.nez --format c");
		ConsoleUtils.println("");

		ConsoleUtils.println("The most commonly used nez commands are:");
		ConsoleUtils.println("  parse      parse inputs and construct ASTs");
		ConsoleUtils.println("  match      match inputs without ASTs");
		ConsoleUtils.println("  shell      an interactive parser");
		ConsoleUtils.println("  parser     generate a parser source specified with --format");
		ConsoleUtils.println("  cnez       generate a C-based fast parser (.c)");
		ConsoleUtils.println("  peg        translate a grammar into PEG specified with --format");
		ConsoleUtils.println("  compile    compile a grammar into Nez bytecode .moz");
		ConsoleUtils.println("  bench      perform benchmark tests");
		ConsoleUtils.println("  example    display examples in a grammar");
		ConsoleUtils.println("  test       perform grammar tests");
		ConsoleUtils.exit(0, msg);
	}

	public final Grammar newGrammar() throws IOException {
		ParserGenerator pg = new ParserGenerator();
		Grammar grammar = pg.loadGrammar(grammarFile);
		for (String f : this.grammarFiles) {
			pg.updateGrammar(grammar, f);
		}
		return grammar;
	}

	public final Parser newParser() throws IOException {
		return this.strategy.newParser(newGrammar());
	}

	private int fileIndex = 0;

	public final void checkInputSource() {
		if (inputFiles.size() == 0) {
			ConsoleUtils.exit(1, "no input specified");
		}
	}

	public final boolean hasInputSource() {
		return fileIndex < inputFiles.size();
	}

	public final Source nextInputSource() throws IOException {
		if (hasInputSource()) {
			String path = this.inputFiles.ArrayValues[fileIndex];
			fileIndex++;
			return CommonSource.newFileSource(path);
		}
		return CommonSource.newStringSource(""); // empty input
	}

	public final String getOutputFileName(Source input, String ext) {
		if (outputDirectory != null) {
			return FileBuilder.toFileName(input.getResourceName(), outputDirectory, ext);
		} else {
			return null; // stdout
		}
	}

	public final Object newExtendedOutputHandler(String classPath, String options) {
		if (outputFormat.indexOf('.') > 0) {
			classPath = outputFormat;
		} else {
			classPath += outputFormat;
		}
		try {
			return Class.forName(classPath).newInstance();
		} catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
			Verbose.traceException(e);
			ConsoleUtils.println("Available format: " + options);
			ConsoleUtils.exit(1, "undefined format: " + outputFormat);
		}
		return null;
	}

}
