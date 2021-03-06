/*
 * Copyright (c) 2013, 2014 Chris Newland.
 * Licensed under https://github.com/AdoptOpenJDK/jitwatch/blob/master/LICENSE-BSD
 * Instructions: https://github.com/AdoptOpenJDK/jitwatch/wiki
 */
package org.adoptopenjdk.jitwatch.ui.triview;

import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.ATTR_BYTES;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.ATTR_COMPILE_MILLIS;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.ATTR_NMSIZE;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.DEFAULT_FREQ_INLINE_SIZE;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.DEFAULT_MAX_INLINE_SIZE;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import org.adoptopenjdk.jitwatch.model.IMetaMember;

public class MemberInfo extends HBox
{
	private Label lblBytecodeSize;
	private Label lblBytecodeSizeVal;

	private Label lblAssemblySize;
	private Label lblAssemblySizeVal;

	private Label lblCompileTime;
	private Label lblCompileTimeVal;

	private static final String STYLE_VALUE = "-fx-border-color: black; -fx-border-width: 1px; text-align: center; ";

	public MemberInfo()
	{
		double valueWidth = 125;

		setSpacing(20);

		VBox vBoxBytecode = new VBox();
		VBox vBoxAssembly = new VBox();
		VBox vBoxCompileTime = new VBox();

		vBoxBytecode.setMaxWidth(valueWidth);
		vBoxAssembly.setMaxWidth(valueWidth);
		vBoxCompileTime.setMaxWidth(valueWidth);

		vBoxBytecode.setSpacing(2);
		vBoxAssembly.setSpacing(2);
		vBoxCompileTime.setSpacing(2);

		lblBytecodeSize = new Label();
		lblBytecodeSizeVal = new Label();
		lblBytecodeSizeVal.setStyle(STYLE_VALUE);
		lblBytecodeSizeVal.setMinWidth(valueWidth);

		lblAssemblySize = new Label();
		lblAssemblySizeVal = new Label();
		lblAssemblySizeVal.setStyle(STYLE_VALUE);
		lblAssemblySizeVal.setMinWidth(valueWidth);

		lblCompileTime = new Label();
		lblCompileTimeVal = new Label();
		lblCompileTimeVal.setStyle(STYLE_VALUE);
		lblCompileTimeVal.setMinWidth(valueWidth);

		lblBytecodeSize.setText("Bytecode size");
		lblAssemblySize.setText("Native size");
		lblCompileTime.setText("Compile time (ms)");

		setMember(null);

		vBoxBytecode.getChildren().add(lblBytecodeSize);
		vBoxBytecode.getChildren().add(lblBytecodeSizeVal);

		vBoxAssembly.getChildren().add(lblAssemblySize);
		vBoxAssembly.getChildren().add(lblAssemblySizeVal);

		vBoxCompileTime.getChildren().add(lblCompileTime);
		vBoxCompileTime.getChildren().add(lblCompileTimeVal);

		getChildren().add(vBoxBytecode);
		getChildren().add(vBoxAssembly);
		getChildren().add(vBoxCompileTime);
	}

	public void setMember(IMetaMember member)
	{
		lblBytecodeSizeVal.setText(getAttrOrNA(member, false, ATTR_BYTES));

		lblAssemblySizeVal.setText(getAttrOrNA(member, true, ATTR_NMSIZE));

		lblCompileTimeVal.setText(getAttrOrNA(member, true, ATTR_COMPILE_MILLIS));

		if (member != null)
		{
			try
			{
				int bytecodeSize = Integer.parseInt(lblBytecodeSizeVal.getText());

				if (bytecodeSize < DEFAULT_FREQ_INLINE_SIZE)
				{
					lblBytecodeSizeVal.setStyle(STYLE_VALUE + " -fx-background-color: #00ff00");
					lblBytecodeSizeVal.setTooltip(new Tooltip("Will be inlined"));
				}
				else if (bytecodeSize < DEFAULT_MAX_INLINE_SIZE)
				{
					lblBytecodeSizeVal.setStyle(STYLE_VALUE + " -fx-background-color: #ffff00");
					lblBytecodeSizeVal.setTooltip(new Tooltip("Will be inlined if hot"));
				}
				else
				{
					lblBytecodeSizeVal.setStyle(STYLE_VALUE + " -fx-background-color: #ff0000");
					lblBytecodeSizeVal.setTooltip(new Tooltip("Will not be inlined"));
				}
			}
			catch (NumberFormatException nfe)
			{
				lblBytecodeSizeVal.setStyle(STYLE_VALUE);
				Tooltip.uninstall(lblBytecodeSizeVal, lblBytecodeSizeVal.getTooltip());
			}
		}
	}

	private String getAttrOrNA(IMetaMember member, boolean compiled, String attribute)
	{
		String result = null;

		if (member != null)
		{
			if (compiled)
			{
				result = member.getCompiledAttribute(attribute);
			}
			else
			{
				result = member.getQueuedAttribute(attribute);
			}
		}

		if (result == null)
		{
			result = "n/a";
		}

		return result;
	}
}