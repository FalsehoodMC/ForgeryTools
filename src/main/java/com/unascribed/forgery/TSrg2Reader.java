package com.unascribed.forgery;

import java.io.Reader;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.TextMappingsReader;
import org.cadixdev.lorenz.io.srg.SrgConstants;
import org.cadixdev.lorenz.io.srg.tsrg.TSrgReader;
import org.cadixdev.lorenz.model.ClassMapping;
import org.cadixdev.lorenz.model.ExtensionKey;
import org.cadixdev.lorenz.model.MethodMapping;

public class TSrg2Reader extends TextMappingsReader {
	
	public static final ExtensionKey<String> ID = new ExtensionKey<>(String.class, "tsrg2:id");
	
	/**
	 * Creates a new TSRG mappings reader, for the given {@link Reader}.
	 *
	 * @param reader The reader
	 */
	public TSrg2Reader(final Reader reader) {
		super(reader, TSrg2Reader.Processor::new);
	}

	/**
	 * The mappings processor for the TSRG format.
	 */
	public static class Processor extends TextMappingsReader.Processor {

		private static final int HEADER_ELEMENT_COUNT = 4;
		private static final int CLASS_MAPPING_ELEMENT_COUNT = 3;
		private static final int FIELD_MAPPING_ELEMENT_COUNT = 3;
		private static final int METHOD_MAPPING_ELEMENT_COUNT = 4;
		private static final int PARAMETER_MAPPING_ELEMENT_COUNT = 4;

		private ClassMapping currentClass;
		private MethodMapping currentMethod;
		
		private int lineNum = 0;
		private TSrgReader.Processor v1 = null;
		
		/**
		 * Creates a mappings parser for the TSRG format, with the provided {@link MappingSet}.
		 *
		 * @param mappings The mappings set
		 */
		public Processor(final MappingSet mappings) {
			super(mappings);
		}

		/**
		 * Creates a mappings parser for the TSRG format.
		 */
		public Processor() {
			this(MappingSet.create());
		}

		@Override
		public void accept(final String rawLine) {
			lineNum++;
			try {
				if (v1 != null) {
					v1.accept(rawLine);
					return;
				}
				final String line = SrgConstants.removeComments(rawLine);
				if (line.isEmpty()) return;
	
				if (line.length() < 3) {
					throw new IllegalArgumentException("Faulty TSRG mapping encountered: `" + line + "`!");
				}
	
				// Split up the line, for further processing
				final String[] split = SPACE.split(line);
				final int len = split.length;
				
				if (lineNum == 1) {
					if (split.length == 4 && "tsrg2".equals(split[0]) && "obf".equals(split[1]) && "srg".equals(split[2]) && "id".equals(split[3])) {
						// it's a tsrg2 we know how to read
					} else {
						v1 = new TSrgReader.Processor(mappings);
						v1.accept(rawLine);
					}
					return;
				}
	
				// Process class mappings
				if (!split[0].startsWith("\t") && len == CLASS_MAPPING_ELEMENT_COUNT) {
					final String obfuscatedName = split[0];
					final String deobfuscatedName = split[1];
	
					// Get mapping, and set de-obfuscated name
					this.currentMethod = null;
					this.currentClass = this.mappings.getOrCreateClassMapping(obfuscatedName);
					this.currentClass.setDeobfuscatedName(deobfuscatedName);
					this.currentClass.set(ID, split[2]);
				}
				else if (split[0].startsWith("\t\t") && this.currentMethod != null) {
					if (split.length == 1) {
						// modifier; ignore
					} else if (len == PARAMETER_MAPPING_ELEMENT_COUNT) {
						int param = Integer.parseInt(split[0].replace("\t", ""));
						String obfuscatedName = split[1]; // always "o" due to snowmen, but Lorenz doesn't want it anyway
						String deobfuscatedName = split[2];
						this.currentMethod.createParameterMapping(param, deobfuscatedName)
							.set(ID, split[3]);
					}
				}
				else if (split[0].startsWith("\t") && this.currentClass != null) {
					final String obfuscatedName = split[0].replace("\t", "");
	
					// Process field mapping
					if (len == FIELD_MAPPING_ELEMENT_COUNT) {
						final String deobfuscatedName = split[1];
	
						// Get mapping, and set de-obfuscated name
						this.currentMethod = null;
						this.currentClass
							.getOrCreateFieldMapping(obfuscatedName)
							.setDeobfuscatedName(deobfuscatedName)
							.set(ID, split[2]);
					}
					// Process method mapping
					else if (len == METHOD_MAPPING_ELEMENT_COUNT) {
						final String obfuscatedSignature = split[1];
						final String deobfuscatedName = split[2];
	
						// Get mapping, and set de-obfuscated name
						this.currentMethod = this.currentClass
							.getOrCreateMethodMapping(obfuscatedName, obfuscatedSignature)
							.setDeobfuscatedName(deobfuscatedName);
						this.currentMethod.set(ID, split[3]);
					}
				} else {
					throw new IllegalArgumentException("Failed to process line: `" + line + "`!");
				}
			} catch (Throwable t) {
				throw new IllegalArgumentException("Parse error at line "+lineNum+"\n"+rawLine, t);
			}
		}

	}
}
