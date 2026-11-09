package com.alchmatepro;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Local RuneLite developer-mode launcher for AlchMate Pro.
 * Run with "--developer-mode --debug" to launch the official RuneLite client with AlchMate Pro loaded.
 */
public class AlchMateProPluginTest
{
	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(AlchMateProPlugin.class);
		RuneLite.main(args);
	}
}
