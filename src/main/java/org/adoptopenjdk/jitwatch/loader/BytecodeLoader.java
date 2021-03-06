/*
 * Copyright (c) 2013, 2014 Chris Newland.
 * Licensed under https://github.com/AdoptOpenJDK/jitwatch/blob/master/LICENSE-BSD
 * Instructions: https://github.com/AdoptOpenJDK/jitwatch/wiki
 */
package org.adoptopenjdk.jitwatch.loader;

import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.C_COLON;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.C_HASH;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.C_NEWLINE;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.C_OPEN_ANGLE;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.C_SEMICOLON;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.DEBUG_LOGGING_BYTECODE;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.S_BYTECODE_CODE;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.S_BYTECODE_CONSTANT_POOL;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.S_BYTECODE_EXCEPTIONS;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.S_BYTECODE_INNERCLASSES;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.S_BYTECODE_LINENUMBERTABLE;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.S_BYTECODE_LOCALVARIABLETABLE;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.S_BYTECODE_MAJOR_VERSION;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.S_BYTECODE_MINOR_VERSION;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.S_BYTECODE_RUNTIMEVISIBLEANNOTATIONS;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.S_BYTECODE_SIGNATURE;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.S_BYTECODE_STACKMAPTABLE;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.S_BYTECODE_STATIC_INITIALISER_SIGNATURE;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.S_CLOSE_BRACE;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.S_COLON;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.S_COMMA;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.S_DEFAULT;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.S_DOT;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.S_DOUBLE_SLASH;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.S_HASH;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.S_NEWLINE;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.S_SEMICOLON;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.S_SLASH;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.S_SPACE;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.adoptopenjdk.jitwatch.model.MemberSignatureParts;
import org.adoptopenjdk.jitwatch.model.MetaClass;
import org.adoptopenjdk.jitwatch.model.bytecode.BCParamConstant;
import org.adoptopenjdk.jitwatch.model.bytecode.BCParamNumeric;
import org.adoptopenjdk.jitwatch.model.bytecode.BCParamString;
import org.adoptopenjdk.jitwatch.model.bytecode.BCParamSwitch;
import org.adoptopenjdk.jitwatch.model.bytecode.BytecodeInstruction;
import org.adoptopenjdk.jitwatch.model.bytecode.ClassBC;
import org.adoptopenjdk.jitwatch.model.bytecode.IBytecodeParam;
import org.adoptopenjdk.jitwatch.model.bytecode.LineTableEntry;
import org.adoptopenjdk.jitwatch.model.bytecode.MemberBytecode;
import org.adoptopenjdk.jitwatch.model.bytecode.Opcode;
import org.adoptopenjdk.jitwatch.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.tools.javap.JavapTask;
import com.sun.tools.javap.JavapTask.BadArgs;

public final class BytecodeLoader
{
	private static final Logger logger = LoggerFactory.getLogger(BytecodeLoader.class);

	private static final Pattern PATTERN_BYTECODE_INSTRUCTION = Pattern
			.compile("^([0-9]+):\\s([0-9a-z_]+)\\s?([#0-9a-z,\\- ]+)?\\s?\\{?\\s?(//.*)?");

	enum BytecodeSection
	{
		NONE, CONSTANT_POOL, CODE, EXCEPTIONS, LINETABLE, RUNTIMEVISIBLEANNOTATIONS, LOCALVARIABLETABLE, STACKMAPTABLE, INNERCLASSES
	}

	private static final Map<String, BytecodeSection> sectionLabelMap = new HashMap<>();

	static
	{
		sectionLabelMap.put(S_BYTECODE_CONSTANT_POOL, BytecodeSection.CONSTANT_POOL);
		sectionLabelMap.put(S_BYTECODE_CODE, BytecodeSection.CODE);
		sectionLabelMap.put(S_BYTECODE_LINENUMBERTABLE, BytecodeSection.LINETABLE);
		sectionLabelMap.put(S_BYTECODE_LOCALVARIABLETABLE, BytecodeSection.LOCALVARIABLETABLE);
		sectionLabelMap.put(S_BYTECODE_RUNTIMEVISIBLEANNOTATIONS, BytecodeSection.RUNTIMEVISIBLEANNOTATIONS);
		sectionLabelMap.put(S_BYTECODE_EXCEPTIONS, BytecodeSection.EXCEPTIONS);
		sectionLabelMap.put(S_BYTECODE_STACKMAPTABLE, BytecodeSection.STACKMAPTABLE);
		sectionLabelMap.put(S_BYTECODE_INNERCLASSES, BytecodeSection.INNERCLASSES);
	}

	private BytecodeLoader()
	{
	}

	/*
	 * Builds a meta class from bytecode where JITDataModel.buildAndGetMetaClass
	 * fails due to NoClassDefFoundError
	 */
	public static MetaClass buildMetaClassFromClass(String fqClassName)
	{
		return null;
	}

	//TODO return list? or combine? (latter)
	public static ClassBC fetchBytecodeForClass(List<String> classLocations, String fqClassName)
	{
		if (DEBUG_LOGGING_BYTECODE)
		{
			logger.debug("fetchBytecodeForClass: {}", fqClassName);

			logger.info("Class locations: {}", StringUtil.listToString(classLocations));
		}

		String[] args = buildClassPathFromClassLocations(classLocations, fqClassName);

		if (DEBUG_LOGGING_BYTECODE)
		{
			for (String arg : args)
			{
				logger.debug("arg: {}", arg);
			}
		}

		String byteCodeString = createJavapTaskFromArguments(fqClassName, args);

		ClassBC classBytecode = parsedByteCodeFrom(fqClassName, byteCodeString);

		return classBytecode;
	}

	private static ClassBC parsedByteCodeFrom(String fqClassName, String byteCodeString)
	{
		ClassBC result = null;

		if (byteCodeString != null)
		{
			try
			{
				result = parse(fqClassName, byteCodeString);
			}
			catch (Exception ex)
			{
				logger.error("Exception parsing bytecode", ex);
			}
		}

		return result;
	}

	private static String createJavapTaskFromArguments(String fqClassName, String[] args)
	{
		String byteCodeString = null;

		try (ByteArrayOutputStream baos = new ByteArrayOutputStream(65536))
		{
			JavapTask task = new JavapTask();
			task.setLog(baos);
			task.handleOptions(args);
			task.call();

			byteCodeString = baos.toString();
		}
		catch (BadArgs ba)
		{
			logger.error("Could not obtain bytecode for class: {}", fqClassName, ba);
		}
		catch (IOException ioe)
		{
			logger.error("", ioe);
		}

		return byteCodeString;
	}

	private static String[] buildClassPathFromClassLocations(Collection<String> classLocations, String fqClassName)
	{
		String[] args;

		if (classLocations == null || classLocations.size() == 0)
		{
			args = new String[] { "-c", "-p", "-v", fqClassName };
		}
		else
		{
			StringBuilder classPathBuilder = new StringBuilder();

			for (String cp : classLocations)
			{
				classPathBuilder.append(cp).append(File.pathSeparatorChar);
			}

			classPathBuilder.deleteCharAt(classPathBuilder.length() - 1);

			args = new String[] { "-c", "-p", "-v", "-classpath", classPathBuilder.toString(), fqClassName };
		}

		return args;
	}

	// TODO refactor this class - better stateful than all statics
	public static ClassBC parse(String fqClassName, String bytecode)
	{
		ClassBC classBytecode = new ClassBC();

		String[] lines = bytecode.split(S_NEWLINE);

		int pos = 0;

		StringBuilder builder = new StringBuilder();

		BytecodeSection section = BytecodeSection.NONE;

		MemberSignatureParts msp = null;

		MemberBytecode memberBytecode = null;

		while (pos < lines.length)
		{
			String line = lines[pos].trim();

			if (DEBUG_LOGGING_BYTECODE)
			{
				logger.debug("Line: '{}'", line);
			}

			BytecodeSection nextSection = getNextSection(line);

			if (nextSection != null)
			{
				sectionFinished(fqClassName, section, msp, builder, memberBytecode, classBytecode);

				section = changeSection(nextSection);
				pos++;

				if (pos < lines.length)
				{
					line = lines[pos].trim();
				}
			}

			if (DEBUG_LOGGING_BYTECODE)
			{
				logger.debug("{} Line: {}", section, line);
			}

			switch (section)
			{
			case NONE:
				if (couldBeMemberSignature(line))
				{
					msp = MemberSignatureParts.fromBytecodeSignature(fqClassName, line);

					msp.setClassBC(classBytecode);

					if (DEBUG_LOGGING_BYTECODE)
					{
						logger.debug("New signature: {}", msp);
					}

					memberBytecode = new MemberBytecode(msp);

					if (DEBUG_LOGGING_BYTECODE)
					{
						logger.debug("Initialised new MemberBytecode");
					}
				}
				else if (line.startsWith(S_BYTECODE_SIGNATURE))
				{
					buildClassGenerics(line, classBytecode);
				}
				else if (line.startsWith(S_BYTECODE_MINOR_VERSION))
				{
					int minorVersion = getVersionPart(line);

					if (minorVersion != -1)
					{
						classBytecode.setMinorVersion(minorVersion);
					}
				}
				else if (line.startsWith(S_BYTECODE_MAJOR_VERSION))
				{
					int majorVersion = getVersionPart(line);

					if (majorVersion != -1)
					{
						classBytecode.setMajorVersion(majorVersion);
					}
				}
				break;
			case INNERCLASSES:
				String innerClassName = getInnerClassNameOrNull(line);

				if (innerClassName != null)
				{
					classBytecode.addInnerClassName(innerClassName);
				}
				else
				{
					section = changeSection(BytecodeSection.NONE);
					pos--;
				}
				break;
			case CODE:
				section = performCODE(fqClassName, classBytecode, builder, section, msp, memberBytecode, line);
				break;
			case CONSTANT_POOL:
				section = performConstantPool(fqClassName, classBytecode, builder, section, msp, memberBytecode, line);
				break;
			case LINETABLE:
				section = performLINETABLE(fqClassName, classBytecode, builder, section, msp, memberBytecode, line);
				break;
			case RUNTIMEVISIBLEANNOTATIONS:
				if (!isRunTimeVisibleAnnotation(line))
				{
					section = changeSection(BytecodeSection.NONE);
					pos--;
				}
				break;
			case LOCALVARIABLETABLE:
				if (!isLocalVariableLine(line))
				{
					section = changeSection(BytecodeSection.NONE);
					pos--;
				}
				break;
			case STACKMAPTABLE:
				if (!isStackMapTable(line))
				{
					section = changeSection(BytecodeSection.NONE);
					pos--;
				}
				break;
			case EXCEPTIONS:
				break;
			}

			pos++;
		}

		return classBytecode;
	}

	public static String getInnerClassNameOrNull(String innerClassLine)
	{
		String result = null;

		if (innerClassLine != null)
		{
			String tempResult = StringUtil.getSubstringBetween(innerClassLine, S_DOUBLE_SLASH, " of class");

			if (tempResult != null)
			{
				int startIndex = tempResult.indexOf(S_SPACE);

				if (startIndex != -1)
				{
					result = tempResult.substring(startIndex + 1);
				}

			}
		}

		return result;
	}

	public static void buildClassGenerics(String line, ClassBC classBytecode)
	{
		StringBuilder keyBuilder = new StringBuilder();
		StringBuilder valBuilder = new StringBuilder();

		boolean inKey = false;
		boolean inVal = false;

		for (int i = 0; i < line.length(); i++)
		{
			char c = line.charAt(i);

			if (c == C_OPEN_ANGLE)
			{
				inKey = true;
				inVal = false;
			}
			else if (c == C_COLON)
			{
				if (inKey && !inVal)
				{
					inKey = false;
					inVal = true;
				}
			}
			else if (c == C_SEMICOLON)
			{
				if (!inKey && inVal)
				{
					String key = keyBuilder.toString();
					String val = valBuilder.toString();

					if (val.length() > 0)
					{
						val = val.substring(1); // string leading 'L'
						val = val.replace(S_SLASH, S_DOT);
					}

					classBytecode.addGenericsMapping(key, val);

					keyBuilder.setLength(0);
					valBuilder.setLength(0);

					inKey = true;
					inVal = false;
				}
			}
			else if (inKey)
			{
				keyBuilder.append(c);
			}
			else if (inVal)
			{
				valBuilder.append(c);
			}
		}

		if (!inKey && inVal)
		{
			String key = keyBuilder.toString();
			String val = valBuilder.toString();

			if (val.length() > 0)
			{
				val = val.substring(1); // string leading 'L'
				val = val.replace(S_SLASH, S_DOT);
			}

			classBytecode.addGenericsMapping(key, val);
			keyBuilder.setLength(0);
			valBuilder.setLength(0);

			inKey = false;
			inVal = false;
		}
	}

	private static BytecodeSection performLINETABLE(String fqClassName, ClassBC classBytecode, StringBuilder builder,
			BytecodeSection section, MemberSignatureParts msp, MemberBytecode memberBytecode, String line)
	{
		if (line.startsWith("line "))
		{
			builder.append(line).append(C_NEWLINE);
		}
		else
		{
			sectionFinished(fqClassName, BytecodeSection.LINETABLE, msp, builder, memberBytecode, classBytecode);

			section = changeSection(BytecodeSection.NONE);
		}
		return section;
	}

	private static BytecodeSection performConstantPool(String fqClassName, ClassBC classBytecode, StringBuilder builder,
			BytecodeSection section, MemberSignatureParts msp, MemberBytecode memberBytecode, String line)
	{
		if (!line.startsWith(S_HASH))
		{
			sectionFinished(fqClassName, BytecodeSection.CONSTANT_POOL, msp, builder, memberBytecode, classBytecode);

			section = changeSection(BytecodeSection.NONE);
		}
		return section;
	}

	private static BytecodeSection performCODE(String fqClassName, ClassBC classBytecode, StringBuilder builder,
			BytecodeSection section, MemberSignatureParts msp, MemberBytecode memberBytecode, final String line)
	{
		int firstColonIndex = line.indexOf(C_COLON);

		if (firstColonIndex != -1)
		{
			String beforeColon = line.substring(0, firstColonIndex);

			try
			{
				// line number ?
				Integer.parseInt(beforeColon);

				builder.append(line).append(C_NEWLINE);
			}
			catch (NumberFormatException nfe)
			{
				if (S_DEFAULT.equals(beforeColon))
				{
					// possibly inside a tableswitch or lookupswitch
					builder.append(line).append(C_NEWLINE);
				}
				else
				{
					sectionFinished(fqClassName, BytecodeSection.CODE, msp, builder, memberBytecode, classBytecode);
					section = changeSection(BytecodeSection.NONE);
				}
			}
		}
		else if (S_CLOSE_BRACE.equals(line.trim()))
		{
			// end of a tableswitch or lookupswitch
			builder.append(line).append(C_NEWLINE);
		}
		return section;
	}

	private static boolean isRunTimeVisibleAnnotation(final String line)
	{
		return line.contains(": #");
	}

	private static boolean isLocalVariableLine(final String line)
	{
		return line.startsWith("Start") || (line.length() > 0 && Character.isDigit(line.charAt(0)));
	}

	private static boolean isStackMapTable(final String line)
	{
		String trimmedLine = line.trim();
		return trimmedLine.startsWith("frame_type") || trimmedLine.startsWith("offset_delta") || trimmedLine.startsWith("locals")
				|| trimmedLine.startsWith("stack");
	}

	private static boolean couldBeMemberSignature(String line)
	{
		return line.endsWith(");") || line.contains(" throws ") && line.endsWith(S_SEMICOLON)
				|| line.startsWith(S_BYTECODE_STATIC_INITIALISER_SIGNATURE);
	}

	private static void sectionFinished(String fqClassName, BytecodeSection lastSection, MemberSignatureParts msp,
			StringBuilder builder, MemberBytecode memberBytecode, ClassBC classBytecode)
	{
		if (DEBUG_LOGGING_BYTECODE)
		{
			logger.debug("sectionFinished: {}", lastSection);
		}

		if (lastSection == BytecodeSection.CODE)
		{
			List<BytecodeInstruction> instructions = parseInstructions(builder.toString());

			if (memberBytecode != null)
			{
				memberBytecode.setInstructions(instructions);

				classBytecode.addMemberBytecode(memberBytecode);

				if (DEBUG_LOGGING_BYTECODE)
				{
					logger.debug("stored bytecode for:\n{}", msp);
				}
			}
			else
			{
				logger.error("No member for these instructions");

				for (BytecodeInstruction instr : instructions)
				{
					logger.error("{}", instr);
				}
			}
		}
		else if (lastSection == BytecodeSection.LINETABLE)
		{
			storeLineNumberTable(fqClassName, memberBytecode, builder.toString(), msp);

			if (DEBUG_LOGGING_BYTECODE)
			{
				logger.debug("stored line number table for : {}", msp);
			}
		}

		builder.delete(0, builder.length());
	}

	private static BytecodeSection changeSection(BytecodeSection nextSection)
	{
		if (DEBUG_LOGGING_BYTECODE)
		{
			logger.debug("Changing section to: {}", nextSection);
		}

		return nextSection;
	}

	private static BytecodeSection getNextSection(final String line)
	{
		BytecodeSection nextSection = null;

		if (line != null)
		{
			if (line.length() == 0)
			{
				nextSection = BytecodeSection.NONE;
			}

			for (Map.Entry<String, BytecodeSection> entry : sectionLabelMap.entrySet())
			{
				if (line.trim().startsWith(entry.getKey()))
				{
					nextSection = entry.getValue();
					break;
				}
			}
		}

		return nextSection;
	}

	private static int getVersionPart(final String line)
	{
		int version = -1;

		int colonPos = line.indexOf(C_COLON);

		if (colonPos != -1 && colonPos != line.length() - 1)
		{
			String versionPart = line.substring(colonPos + 1).trim();

			try
			{
				version = Integer.parseInt(versionPart);
			}
			catch (NumberFormatException nfe)
			{
			}
		}

		return version;
	}

	public static List<BytecodeInstruction> parseInstructions(final String bytecode)
	{
		List<BytecodeInstruction> bytecodeInstructions = new ArrayList<>();

		if (DEBUG_LOGGING_BYTECODE)
		{
			logger.debug("Raw bytecode: '{}'", bytecode);
		}

		String[] lines = bytecode.split(S_NEWLINE);

		boolean inSwitch = false;
		BCParamSwitch table = new BCParamSwitch();
		BytecodeInstruction instruction = null;

		for (String line : lines)
		{
			line = line.trim();

			if (DEBUG_LOGGING_BYTECODE)
			{
				logger.debug("parsing bytecode line: '{}' inSwitch: {}", line, inSwitch);
			}

			if (inSwitch)
			{
				if (S_CLOSE_BRACE.equals(line))
				{
					instruction.addParameter(table);

					bytecodeInstructions.add(instruction);
					inSwitch = false;

					if (DEBUG_LOGGING_BYTECODE)
					{
						logger.debug("finished switch");
					}
				}
				else
				{
					String[] parts = line.split(S_COLON);

					if (parts.length == 2)
					{
						table.put(parts[0].trim(), parts[1].trim());
					}
					else
					{
						logger.error("Unexpected tableswitch entry: " + line);
					}
				}
			}
			else
			{
				try
				{
					Matcher matcher = PATTERN_BYTECODE_INSTRUCTION.matcher(line);

					if (matcher.find())
					{
						instruction = new BytecodeInstruction();

						String offset = matcher.group(1);
						String mnemonic = matcher.group(2);
						String paramString = matcher.group(3);
						String comment = matcher.group(4);

						instruction.setOffset(Integer.parseInt(offset));
						instruction.setOpcode(Opcode.getOpcodeForMnemonic(mnemonic));

						if (comment != null && comment.trim().length() > 0)
						{
							instruction.setComment(comment.trim());
						}

						if (instruction.getOpcode() == Opcode.TABLESWITCH || instruction.getOpcode() == Opcode.LOOKUPSWITCH)
						{
							if (DEBUG_LOGGING_BYTECODE)
							{
								logger.debug("Found a table or lookup switch");
							}

							inSwitch = true;
						}
						else
						{
							if (paramString != null && paramString.trim().length() > 0)
							{
								processParameters(paramString.trim(), instruction);
							}

							bytecodeInstructions.add(instruction);
						}
					}
					else
					{
						logger.error("could not parse bytecode: '" + line + "'");
					}
				}
				catch (Exception e)
				{
					logger.error("Error parsing bytecode line: '" + line + "'", e);
				}
			}
		}

		return bytecodeInstructions;
	}

	private static void storeLineNumberTable(String fqClassName, MemberBytecode memberBytecode, String tableLines,
			MemberSignatureParts msp)
	{
		String[] lines = tableLines.split(S_NEWLINE);

		for (String line : lines)
		{
			// strip off 'line '
			line = line.trim().substring(5);

			String[] parts = line.split(S_COLON);

			if (parts.length == 2)
			{
				String source = parts[0].trim();
				String offset = parts[1].trim();

				try
				{
					LineTableEntry entry = new LineTableEntry(Integer.parseInt(source), Integer.parseInt(offset));
					memberBytecode.addLineTableEntry(entry);
				}
				catch (NumberFormatException nfe)
				{
					logger.error("Could not parse line number {}", line, nfe);
				}
			}
			else
			{
				logger.error("Could not split line: {}", line);
			}
		}
	}

	private static void processParameters(String paramString, BytecodeInstruction instruction)
	{
		String[] parts = paramString.split(S_COMMA);

		for (String part : parts)
		{
			IBytecodeParam parameter;

			part = part.trim();

			if (part.charAt(0) == C_HASH)
			{
				parameter = new BCParamConstant(part);
			}
			else
			{
				try
				{
					int value = Integer.parseInt(part);
					parameter = new BCParamNumeric(value);
				}
				catch (NumberFormatException nfe)
				{
					parameter = new BCParamString(part);
				}
			}

			instruction.addParameter(parameter);
		}
	}
}
