/**
 * Copyright (C) 2009 STMicroelectronics
 *
 * This file is part of "Mind Compiler" is free software: you can redistribute 
 * it and/or modify it under the terms of the GNU Lesser General Public License 
 * as published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Contact: mind@ow2.org
 *
 * Authors: Matthieu Leclercq
 * Contributors: Julien Tous
 */

package org.ow2.mind;

import static org.ow2.mind.PathHelper.fullyQualifiedNameToPath;
import static org.ow2.mind.PathHelper.isRelative;
import static org.ow2.mind.compilation.DirectiveHelper.splitOptionString;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.objectweb.fractal.adl.ADLException;
import org.objectweb.fractal.adl.Definition;
import org.objectweb.fractal.adl.Loader;
import org.objectweb.fractal.adl.error.Error;
import org.ow2.mind.adl.DefinitionCompiler;
import org.ow2.mind.adl.GraphCompiler;
import org.ow2.mind.adl.graph.ComponentGraph;
import org.ow2.mind.adl.graph.Instantiator;
import org.ow2.mind.cli.CmdOptionBooleanEvaluator;
import org.ow2.mind.cli.CommandLine;
import org.ow2.mind.cli.CommandLineOptionExtensionHelper;
import org.ow2.mind.cli.InvalidCommandLineException;
import org.ow2.mind.cli.Options;
import org.ow2.mind.compilation.CompilationCommand;
import org.ow2.mind.compilation.CompilationCommandExecutor;
import org.ow2.mind.compilation.CompilerCommand;
import org.ow2.mind.compilation.CompilerContextHelper;
import org.ow2.mind.error.ErrorCollection;
import org.ow2.mind.error.ErrorManager;
import org.ow2.mind.inject.GuiceModuleExtensionHelper;
import org.ow2.mind.io.BasicOutputFileLocator;
import org.ow2.mind.io.OutputFileLocator;
import org.ow2.mind.plugin.PluginLoaderModule;
import org.ow2.mind.plugin.PluginManager;
import org.testng.Assert;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class DumpDefinitionGraphCompilerRunner {

	public static final String              DEFAULT_CFLAGS    = "-g -Wall -Werror -Wredundant-decls -Wunreachable-code -Wstrict-prototypes -Wwrite-strings";
	public static final String              CFLAGS_PROPERTY   = "mind.test.cflags";

	public static final String              COMPILER_PROPERTY = "mind.test.compiler";

	public final ErrorManager               errorManager;
	public final Loader                     adlLoader;

	public final Instantiator               graphInstantiator;

	public final OutputFileLocator          outputFileLocator;
	public final DefinitionCompiler         definitionCompiler;
	public final GraphCompiler              graphCompiler;

	public final CompilationCommandExecutor executor;

	public final PluginManager              pluginManager;

	public final File                       buildDir;
	public Map<Object, Object>              context;
	private final Map<Object, Object>       initialContext;

	public DumpDefinitionGraphCompilerRunner() throws ADLException {
		this(new HashMap<Object, Object>());
	}

	public DumpDefinitionGraphCompilerRunner(final Map<Object, Object> initialContext)
			throws ADLException {
		this.initialContext = initialContext;
		final Injector pluginManagerInjector = Guice
				.createInjector(new PluginLoaderModule());
		pluginManager = pluginManagerInjector.getInstance(PluginManager.class);

		// Addition to the original tests setup:
		// Since the mind-plugin.xml descriptor contains a CLI flag (--flatten)
		// the compiler needs a CmdOptionBooleanEvaluator in the context or it will
		// crash otherwise.
		// Behavior inspired by the standard Launcher.
		Options options = new Options();
		options.addOptions(CommandLineOptionExtensionHelper.getCommandOptions(pluginManager));
		// parse arguments to a CommandLine.
		try {
			// If needed, we could use this variation to enable --flatten everytime 
			//final CommandLine cmdLine = CommandLine.parseArgs(options, false, "--flatten");
			
			final CommandLine cmdLine = CommandLine.parseArgs(options, false, "");
			initialContext.put(CmdOptionBooleanEvaluator.CMD_LINE_CONTEXT_KEY, cmdLine);
		} catch (InvalidCommandLineException e) {
			// We should never get here anyway as we are the ones building the command line.
			e.printStackTrace();
		}
		//

		final Injector injector = Guice.createInjector(GuiceModuleExtensionHelper
				.getModules(pluginManager, initialContext));
		
		errorManager = injector.getInstance(ErrorManager.class);
		adlLoader = injector.getInstance(Loader.class);
		graphInstantiator = injector.getInstance(Instantiator.class);
		graphCompiler = injector.getInstance(GraphCompiler.class);
		outputFileLocator = injector.getInstance(OutputFileLocator.class);
		definitionCompiler = injector.getInstance(DefinitionCompiler.class);
		executor = injector.getInstance(CompilationCommandExecutor.class);

		buildDir = new File("target/build");

		// init context
		initContext();
	}

	public void initContext() throws ADLException {
		context = new HashMap<Object, Object>(initialContext);
		if (!buildDir.exists()) {
			buildDir.mkdirs();
		}
		context.put(BasicOutputFileLocator.OUTPUT_DIR_CONTEXT_KEY, buildDir);

		final String cFlags = System.getProperty(CFLAGS_PROPERTY, DEFAULT_CFLAGS);
		CompilerContextHelper.setCFlags(context, splitOptionString(cFlags));

		final String compiler = System.getProperty(COMPILER_PROPERTY);
		if (compiler != null) {
			CompilerContextHelper.setCompilerCommand(context, compiler);
			CompilerContextHelper.setAssemblerCommand(context, compiler);
			CompilerContextHelper.setLinkerCommand(context, compiler);
		}

	}

	public Definition load(final String adlName) throws ADLException {
		errorManager.clear();
		final Definition d = adlLoader.load(adlName, context);
		final List<Error> errors = errorManager.getErrors();
		if (!errors.isEmpty()) {
			throw new ADLException(new ErrorCollection(errors));
		}
		return d;
	}

	public Collection<File> compileDefinition(final String adlName)
			throws ADLException, InterruptedException {
		final Definition d = load(adlName);
		final Collection<CompilationCommand> c = definitionCompiler.visit(d,
				context);
		executor.exec(c, context);
		final List<File> result = new ArrayList<File>();
		for (final CompilationCommand command : c) {
			if (command instanceof CompilerCommand)
				result.addAll(command.getOutputFiles());
		}
		return result;
	}

	public File compile(final String adlName) throws ADLException,
	InterruptedException {
		return compile(adlName, null);
	}

	public File compile(final String adlName, final String executableName)
			throws ADLException, InterruptedException {
		String outputPath;
		if (executableName != null) {
			context.put("executable-name", executableName);
			outputPath = executableName;
			if (isRelative(outputPath)) {
				outputPath = "/" + outputPath;
			}
		} else {
			outputPath = fullyQualifiedNameToPath(adlName, null);
		}
		final File outputFile = outputFileLocator.getCExecutableOutputFile(
				outputPath, context);

		final Definition d = load(adlName);

		final ComponentGraph componentGraph = graphInstantiator.instantiate(d,
				context);
		List<Error> errors = errorManager.getErrors();
		if (!errors.isEmpty()) {
			throw new ADLException(new ErrorCollection(errors));
		}

		final Collection<CompilationCommand> commands = graphCompiler.visit(
				componentGraph, context);
		final boolean execOK = executor.exec(commands, context);

		errors = errorManager.getErrors();
		if (!errors.isEmpty()) {
			throw new ADLException(new ErrorCollection(errors));
		}

		assertTrue(
				execOK,
				"Execution of compilation commands returns false, but no error has been logged in error manager ");

		return outputFile;
	}

	public int run(final File executable, final String... params)
			throws Exception {
		final Process p;
		if (params != null && params.length > 0) {
			final List<String> args = new ArrayList<String>(params.length + 1);
			args.add(executable.getAbsolutePath());
			for (final String param : params) {
				args.add(param);
			}
			p = new ProcessBuilder(args).redirectErrorStream(true).start();
		} else {
			p = new ProcessBuilder(executable.getAbsolutePath()).redirectErrorStream(
					true).start();
		}
		final Thread readerThread = new Thread() {
			@Override
			public void run() {
				// output output and error stream on the logger.
				final BufferedReader reader = new BufferedReader(new InputStreamReader(
						p.getInputStream()));
				try {
					String line = reader.readLine();
					while (line != null) {
						System.out.println(line);
						line = reader.readLine();
					}
					reader.close();
				} catch (final IOException e) {
					throw new java.lang.Error("Can't read error stream of process", e);
				}
			}
		};
		readerThread.start();
		final int rValue = p.waitFor();
		readerThread.join();

		return rValue;
	}

	public int compileAndRun(final String adlName, final String executableName,
			final String... params) throws Exception {
		final File executable = compile(adlName, executableName);
		assertNotNull(executable);
		return run(executable, params);
	}

	public void compileRunAndCheck(final String adlName,
			final String executableName, final String... params) throws Exception {
		Assert.assertEquals(compileAndRun(adlName, executableName, params), 0,
				"Unexpected result returned by test");
	}

	public void compileRunAndCheck(final String adlName) throws Exception {
		compileRunAndCheck(adlName, null);
	}

}
