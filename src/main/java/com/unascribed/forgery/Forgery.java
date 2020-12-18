package com.unascribed.forgery;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import net.fabricmc.mapping.tree.TinyMappingFactory;
import net.fabricmc.mapping.tree.TinyTree;

public class Forgery {

	public static void main(String[] args) throws IOException {
		System.err.println("Forgery v0.0.1");
		System.err.println("NOTICE: Forgery is NOT a silver bullet. It is not a magical Fabric-to-Forge converter. For a mod to successfully convert with Forgery, it must have changes made to it to work on both loaders. Forgery simply facilitates remapping and has a few runtime helpers.");
		if (args.length != 4) {
			System.err.println("Forgery requires four arguments. Input Fabric mod, output Forge mod, Intermediary tiny mappings, MCP joined.tsrg.");
			System.err.println("You can obtain the Intermediary mappings here: https://github.com/FabricMC/intermediary/tree/master/mappings");
			System.err.println("You can obtain the MCP joined.tsrg here: http://files.minecraftforge.net/maven/de/oceanlabs/mcp/mcp_config/");
			System.exit(1);
			return;
		}
		ZipFile in = new ZipFile(args[0]);
		if (in.getEntry("fabric.mod.json") == null) {
			System.err.println(args[0]+" doesn't look like a Fabric mod.");
			return;
		}
		ZipOutputStream out = new ZipOutputStream(new FileOutputStream(args[1]));
		BufferedReader inter = new BufferedReader(new FileReader(args[2]));
		TinyTree tree = TinyMappingFactory.load(inter, true);
		
	}
	
}
