/**
 * Copyright (C) 2012 Schneider Electric
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
 * Authors: Julien TOUS
 * Contributors: Stephane Seyvoz
 */

package org.ow2.mind.adl.annotations;

import java.io.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.objectweb.fractal.adl.ADLException;
import org.objectweb.fractal.adl.ContextLocal;
import org.objectweb.fractal.adl.Definition;
import org.objectweb.fractal.adl.Loader;
import org.objectweb.fractal.adl.Node;
import org.objectweb.fractal.adl.interfaces.Interface;
import org.objectweb.fractal.adl.interfaces.InterfaceContainer;
import org.objectweb.fractal.adl.types.TypeInterface;
import org.ow2.mind.adl.annotation.ADLLoaderPhase;
import org.ow2.mind.adl.annotation.AbstractADLLoaderAnnotationProcessor;
import org.ow2.mind.adl.anonymous.AnonymousDefinitionExtractor;
import org.ow2.mind.adl.anonymous.ast.AnonymousDefinitionContainer;
import org.ow2.mind.adl.ast.ASTHelper;
import org.ow2.mind.adl.ast.MindDefinition;
import org.ow2.mind.adl.ast.Component;
import org.ow2.mind.adl.ast.ComponentContainer;
import org.ow2.mind.adl.ast.DefinitionReference;
import org.ow2.mind.adl.ast.DefinitionReferenceContainer;
import org.ow2.mind.adl.ast.MindInterface;
import org.ow2.mind.adl.generic.ast.FormalTypeParameter;
import org.ow2.mind.adl.generic.ast.FormalTypeParameterContainer;
import org.ow2.mind.adl.generic.ast.FormalTypeParameterReference;
import org.ow2.mind.annotation.Annotation;
import org.ow2.mind.idl.IDLLoader;
import org.ow2.mind.idl.ast.IDL;
import org.ow2.mind.idl.ast.InterfaceDefinition;
import org.ow2.mind.io.BasicOutputFileLocator;

import com.google.inject.Inject;
import com.google.inject.Injector;


/**
 * @author Julien TOUS
 */
public class DumpDefinitionGraphAnnotationProcessor extends
AbstractADLLoaderAnnotationProcessor {

	/*
	 * Works because our Loader is itself loaded by Google Guice.
	 */
	@Inject
	protected Injector injector;

	@Inject 
	protected IDLLoader idlLoaderItf;

	@Inject
	protected Loader adlLoaderItf;

	private Map<Object,Object> context;
	private String buildDir;

	Set<String> dotLines = new HashSet<String>();

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.ow2.mind.adl.annotation.ADLLoaderAnnotationProcessor#processAnnotation
	 * (org.ow2.mind.annotation.Annotation, org.objectweb.fractal.adl.Node,
	 * org.objectweb.fractal.adl.Definition,
	 * org.ow2.mind.adl.annotation.ADLLoaderPhase, java.util.Map)
	 */
	public Definition processAnnotation(final Annotation annotation,
			final Node node, final Definition definition,
			final ADLLoaderPhase phase, final Map<Object, Object> context)
					throws ADLException {
		assert annotation instanceof DumpDefinitionGraph;
		this.context = context;


		buildDir = ((File) context.get(BasicOutputFileLocator.OUTPUT_DIR_CONTEXT_KEY)).getPath() +  File.separator;
		File outputFile = new File(buildDir, definition.getName().replace('.', '_')+".gv");

		PrintStream output;
		try {
			output = new PrintStream(new BufferedOutputStream(new FileOutputStream(outputFile)));

			output.println("digraph {");
			output.println("rankdir=BT");
			printDefinitionDeps(definition);
			for (String s : dotLines){
				output.println(s);
			}
			output.println("}");
			output.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	private void printDefinitionDeps(Definition definition) {

		//Declare current definition
		dotLines.add(dotName(definition) + "[URL=\""+ getSource(definition) +"\"shape=box,label=<"+dotLabel(definition)+">];");


		interfaceInstancesDeps(definition);

		definitionInheritanceDeps(definition);

		compositionDeps(definition);

	}

	private void interfaceInheritanceDeps(String itfSig) {
		try {
			String itfDotName = itfSig.replace('.', '_');

			IDL idl = idlLoaderItf.load(itfSig ,context);

			String itfRefSig = ((InterfaceDefinition)idl).getExtends();

			if (itfRefSig != null ) {
				String itfRefDotName = itfRefSig.replace('.', '_');
				//dotLines.add(itfRefSig);
				dotLines.add(itfRefDotName + "[label=<" + itfRefSig + ">];");
				dotLines.add(itfRefDotName+"->"+itfDotName+"[arrowhead=none, arrowtail=empty, style=dashed, dir=back];");
			}

		} catch (ADLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void interfaceInstancesDeps(Definition definition){
		try {
			//Interfaces
			for (Interface itf : ((InterfaceContainer) definition).getInterfaces()) {

				String itfSource = idlLoaderItf.load(((MindInterface)itf).getSignature(), context).astGetSource();
				int i = itfSource.lastIndexOf(":");
				itfSource = itfSource.substring(0,i);
				File itfFile=new File(itfSource);
				itfSource = itfFile.getAbsolutePath();
				if (((MindInterface)itf).getRole().equals(TypeInterface.SERVER_ROLE)) {
					String itfDotName = ((MindInterface)itf).getSignature().replace('.', '_').replace('$', '_');
					dotLines.add(itfDotName + "[label=<<i>" + ((MindInterface)itf).getSignature() + "</i>>,URL=\"" + itfSource + "\"];");
					dotLines.add(itfDotName+"->"+dotName(definition)+"[arrowhead=none, arrowtail=empty, style=dashed, dir=back, color=red];");
				}
				if (((MindInterface)itf).getRole().equals(TypeInterface.CLIENT_ROLE)) {

					String itfDotName = ((MindInterface)itf).getSignature().replace('.', '_').replace('$', '_');
					dotLines.add(itfDotName + "[label=<<i>" + ((MindInterface)itf).getSignature() + "</i>>,URL=\"" + itfSource + "\"];");
					dotLines.add(itfDotName+"->"+dotName(definition)+"[dir=back, arrowtail=vee, style=dashed, color=green];");
				}
				interfaceInheritanceDeps(((MindInterface)itf).getSignature());
			}
		} catch (ADLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void definitionInheritanceDeps(Definition definition){
		//Inheritance
		DefinitionReferenceContainer extendz = ((MindDefinition) definition).getExtends();
		if (extendz != null) {
			DefinitionReference[] defRefs = extendz.getDefinitionReferences();
			for (DefinitionReference defRef : defRefs){
				try {
					Definition def = adlLoaderItf.load(defRef.getName(), context);
					dotLines.add(dotName(defRef) + "[URL=\""+ getSource(def) + "\"shape=box,label=<" + dotLabel(defRef) + ">];");
					dotLines.add(dotName(defRef) + "->"+dotName(definition) + "[arrowhead=none, arrowtail=empty, dir=back];");
					//adlLoaderItf.load(defRef.getName(), context);
					printDefinitionDeps(def);
				} catch (ADLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}

	private void compositionDeps(Definition definition){
		//Composition
		final ContextLocal<Map<String, Integer>> contextualCounters = new ContextLocal<Map<String, Integer>>();
		if (ASTHelper.isComposite(definition)) {	
			final Component[] subComponents = ((ComponentContainer) definition).getComponents();
			if (subComponents != null) {
				for (Component subComp : subComponents) {
					try {
						DefinitionReference defRef = subComp.getDefinitionReference();
						Definition def = null;
						if (defRef == null) {
							//subComp conforms to a Type passed as template 
							if (subComp instanceof FormalTypeParameterReference) {
								String paramRef = ((FormalTypeParameterReference) subComp).getTypeParameterReference();
								if (paramRef != null) {
									FormalTypeParameter[] params = ((FormalTypeParameterContainer) definition).getFormalTypeParameters();
									for (FormalTypeParameter param :params) {
										if (param.getName().equals(paramRef)) {
											defRef = param.getDefinitionReference();
											def = adlLoaderItf.load(defRef.getName(), context);
										}
									}
								}
							}

							//subComp is an inlined anonymous component
							if ((subComp instanceof AnonymousDefinitionContainer)) {
								//The following has been copy pasted from anonymousDefinitionExtractorImpl
								//anonymousDefinitionExtractor could not be used as it also modify anonymous definitions
								//and causes conflict in the anonymous loading phase latter
								def = ((AnonymousDefinitionContainer) subComp).getAnonymousDefinition(); 
								// get a name for this definition
							    Map<String, Integer> counters = contextualCounters.get(context);
							    if (counters == null) {
							      counters = new HashMap<String, Integer>();
							      contextualCounters.set(context, counters);
							    }
							    final String topLevelName = definition.getName();
							    Integer counter = counters.get(topLevelName);
							    if (counter == null) {
							      counter = 0;
							    }
							    counters.put(topLevelName, counter + 1);
							    final String defName = topLevelName + "$" + counter;
							    def.setName(defName);
							    //End of copy paste from anonymousDefinitionExtractorImpl
							}
						} else {
							def = adlLoaderItf.load(defRef.getName(), context);
						}

						if (def != null) {
							dotLines.add(dotName(def) + "[URL=\"" + getSource(def) + "\"shape=box,label=<" + dotLabel(def) + ">];");
							dotLines.add(dotName(def) + "->" + dotName(definition)+"[arrowhead=diamond];");

							//adlLoaderItf.load(defRef.getName(), context);
							printDefinitionDeps(def);
						}
					} catch (ADLException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				} 
			}
		} 
	}



	private String dotName(Definition definition){
		return definition.getName().replace('.', '_').replace("$", "_anon_");
	}

	private String dotLabel(Definition definition){
		return definition.getName().replace("$", "_anon_");
	}

	private String dotLabel(DefinitionReference definitionReference){
		return definitionReference.getName().replace("$", "_anon_");
	}

	private String dotName(DefinitionReference definitionReference){
		return definitionReference.getName().replace('.', '_').replace("$", "_anon_");
	}

	private String getSource(Definition definition){
		String defSource = definition.astGetSource();
		//removing line information. (using lastIndexOf instead of split[0] as ":" is a valid path character)
		if (defSource != null) // Do  not test os if the source is null 
		{
			if (System.getProperty("os.name").toLowerCase().indexOf("win") >= 0) {
				defSource = defSource.substring(1,defSource.lastIndexOf(":"));
			} else {
				//Somehow windows paths come here with an extra "/" in front of the Drive letter.
				defSource = defSource.substring(0,defSource.lastIndexOf(":"));
			}
		}
		return defSource; 
	}
}

