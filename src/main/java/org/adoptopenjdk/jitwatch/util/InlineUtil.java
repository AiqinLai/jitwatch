/*
 * Copyright (c) 2013, 2014 Chris Newland.
 * Licensed under https://github.com/AdoptOpenJDK/jitwatch/blob/master/LICENSE-BSD
 * Instructions: https://github.com/AdoptOpenJDK/jitwatch/wiki
 */
package org.adoptopenjdk.jitwatch.util;

import java.util.Map;

import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.*;

public final class InlineUtil
{
	private InlineUtil()
	{
	}

	public static String buildInlineAnnotationText(boolean inlined, String reason, Map<String, String> callAttrs,
			Map<String, String> methodAttrs)
	{
		StringBuilder builder = new StringBuilder();

		builder.append("Inlined: ");

		if (inlined)
		{
			builder.append("Yes, ");
		}
		else
		{
			builder.append("No, ");
		}

		builder.append(reason);

		if (callAttrs.containsKey(ATTR_COUNT))
		{
			builder.append("\nCount: ").append(callAttrs.get(ATTR_COUNT));
		}
		if (methodAttrs.containsKey(ATTR_IICOUNT))
		{
			builder.append("\niicount: ").append(methodAttrs.get(ATTR_IICOUNT));
		}
		if (methodAttrs.containsKey(ATTR_BYTES))
		{
			builder.append("\nBytes: ").append(methodAttrs.get(ATTR_BYTES));
		}
		if (callAttrs.containsKey(ATTR_PROF_FACTOR))
		{
			builder.append("\nProf factor: ").append(callAttrs.get(ATTR_PROF_FACTOR));
		}

		return builder.toString();
	}
}
