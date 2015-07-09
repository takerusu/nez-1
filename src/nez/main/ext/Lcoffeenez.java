package nez.main.ext;

import nez.generator.GeneratorLoader;

public class Lcoffeenez {
	static {
		GeneratorLoader.regist("coffeenez", nez.generator.CoffeeParserGenerator.class);
		// File Extension
		GeneratorLoader.regist(".coffee", nez.generator.CoffeeParserGenerator.class);
	}
}
