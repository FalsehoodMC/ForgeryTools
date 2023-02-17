/**
 * Cursed Software License
 * 
 * Copyright (c) 2020-2022 Una Thompson (unascribed)
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * You shall not credit the original author(s), except as is required for the
 * inclusion of this copyright notice.
 * 
 * You understand that this Software is stricken with a terrible curse and what it
 * does is not known by the copyright holder(s) and they accept no responsibility
 * for its effects.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * 
 */
package com.unascribed.forgery;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import org.cadixdev.atlas.AtlasWithNewASM;
import org.cadixdev.atlas.util.NIOHelper;
import org.cadixdev.bombe.analysis.InheritanceProvider;
import org.cadixdev.bombe.asm.analysis.ClassProviderInheritanceProvider;
import org.cadixdev.bombe.asm.jar.JarFileClassProvider;
import org.cadixdev.bombe.jar.JarClassEntry;
import org.cadixdev.bombe.jar.JarEntryTransformer;
import org.cadixdev.bombe.jar.JarManifestEntry;
import org.cadixdev.bombe.jar.JarResourceEntry;
import org.cadixdev.bombe.jar.asm.JarEntryRemappingTransformer;
import org.cadixdev.bombe.type.ArrayType;
import org.cadixdev.bombe.type.FieldType;
import org.cadixdev.bombe.type.MethodDescriptor;
import org.cadixdev.bombe.type.MethodDescriptorReader;
import org.cadixdev.bombe.type.ObjectType;
import org.cadixdev.bombe.type.signature.FieldSignature;
import org.cadixdev.bombe.type.signature.MethodSignature;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.merge.FieldMergeStrategy;
import org.cadixdev.lorenz.merge.MappingSetMerger;
import org.cadixdev.lorenz.merge.MergeConfig;
import org.cadixdev.lorenz.merge.MethodMergeStrategy;
import org.cadixdev.lorenz.model.ClassMapping;
import org.cadixdev.lorenz.model.FieldMapping;
import org.cadixdev.lorenz.model.InnerClassMapping;
import org.cadixdev.lorenz.model.MethodMapping;
import org.cadixdev.lorenz.model.TopLevelClassMapping;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.grack.nanojson.JsonWriter;

import org.cadixdev.lorenz.asm.LorenzRemapper;
import org.cadixdev.lorenz.io.proguard.ProGuardReader;
import net.fabricmc.lorenztiny.TinyMappingsReader;

@SuppressWarnings("deprecation")
public class Forgery {

	public static void main(String[] args) throws IOException, JsonParserException {
		System.err.println("Forgery v0.2.0");
		System.err.println("NOTICE: Forgery is NOT a silver bullet. It is not a magical Fabric-to-Forge converter. For a mod to successfully convert with Forgery, it must have changes made to it to work on both loaders. Forgery simply facilitates remapping.");
		if (args.length != 7 && args.length != 9) {
			System.err.println("Forgery requires seven (nine for 1.18) arguments. Input Fabric mod, output Forge mod, Intermediary tiny mappings, MCP mcp_mappings.tsrg, Forgery runtime JAR, Intermediary remapped Minecraft JAR, package name, official client mappings (1.18 only), and official server mappings (1.18 only).");
			System.err.println("You can find the Intermediary mappings in ~/.gradle/caches/fabric-loom/mappings/intermediary-1.16.4-v2.tiny");
			System.err.println("You can find the MCP joined.tsrg in ~/.gradle/caches/forge_gradle/minecraft_repo/versions/1.16.4/mcp_mappings.tsrg");
			System.err.println("You can find the Intermediary remapped Minecraft jar in ~/.gradle/caches/fabric-loom/minecraft-1.16.4-intermediary-net.fabricmc.yarn-1.16.4+build.6-v2.jar");
			System.err.println("Building Fabrication and the Forgery runtime should be enough to make all these files appear.");
			System.exit(1);
			return;
		}
		String pkg = args[6].replace('/', '.');
		String pkgBin = pkg.replace('.', '/');
		ZipFile in = new ZipFile(args[0]);
		if (in.getEntry("fabric.mod.json") == null) {
			System.err.println(args[0]+" doesn't look like a Fabric mod.");
			return;
		}
		JsonObject fabricMod = JsonParser.object().from(in.getInputStream(in.getEntry("fabric.mod.json")));
		JsonObject mixins = null;
		String refmapFile;
		String refmapStr;
		if (fabricMod.has("mixins") && !fabricMod.getArray("mixins").isEmpty()) {
			mixins = JsonParser.object().from(in.getInputStream(in.getEntry(fabricMod.getArray("mixins").getString(0))));
			refmapFile = mixins.getString("refmap");
			if (refmapFile != null) {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				try (InputStream is = in.getInputStream(in.getEntry(refmapFile))) {
					is.transferTo(baos);
				}
				refmapStr = new String(baos.toByteArray());
			} else {
				refmapStr = null;
			}
		} else {
			refmapFile = null;
			refmapStr = null;
		}
		String fabAbsRefMapStr = null;
		String fabRelRefMapStr = null;
		{
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			try (InputStream is = in.getInputStream(in.getEntry("fabAbsRefMap.txt"))) {
				is.transferTo(baos);
			}
			fabAbsRefMapStr = baos.toString();
			baos = new ByteArrayOutputStream();
			try (InputStream is = in.getInputStream(in.getEntry("fabRelRefMap.txt"))) {
				is.transferTo(baos);
			}
			fabRelRefMapStr = baos.toString();
		}
		in.close();
		System.out.println("Building mappings...");
		MemoryMappingTree mappingTree = new MemoryMappingTree();
		MappingReader.read(new BufferedReader(new FileReader(args[2])), mappingTree);
		MappingSet offToInt = new TinyMappingsReader(mappingTree, "official", "intermediary").read();
		MappingSet intToOff = offToInt.reverse();
		MappingSet offToSrg = new TSrg2Reader(new FileReader(args[3])).read();
		if (args.length == 9) {
			MappingSet offToMojClient = new ProGuardReader(new FileReader(args[7])).read().reverse();
			MappingSet offToMojServer = new ProGuardReader(new FileReader(args[8])).read().reverse();
			MappingSet offToSrgWithMojClasses = offToSrg.copy();
			for (TopLevelClassMapping cm : offToInt.getTopLevelClassMappings()) {
				mojifyRecursively(offToSrg, offToMojClient, offToMojServer, offToSrgWithMojClasses, cm);
			}
			offToSrg = offToSrgWithMojClasses;
		}
		MappingSet intToSrg = MappingSetMerger.create(intToOff, offToSrg, MergeConfig.builder()
				.withMethodMergeStrategy(MethodMergeStrategy.LOOSE)
				.withFieldMergeStrategy(FieldMergeStrategy.LOOSE)
				.build()).merge();
		Map<String, String> yarnToInt = new HashMap<>();
//		new TSrgWriter(new FileWriter("merged.tsrg")).write(intToSrg);
		MappingSet srgToInt = intToSrg.reverse();
		
		intToSrg.createTopLevelClassMapping("net/fabricmc/api/Environment", "net/minecraftforge/api/distmarker/OnlyIn");
		TopLevelClassMapping envType = intToSrg.createTopLevelClassMapping("net/fabricmc/api/EnvType", "net/minecraftforge/api/distmarker/Dist");
		envType.createFieldMapping("SERVER", "DEDICATED_SERVER");
		intToSrg.createTopLevelClassMapping("io.github.prospector.modmenu.api.ModMenuApi", pkg+".ModMenuAdapter");
		intToSrg.createTopLevelClassMapping("io.github.prospector.modmenu.api.ConfigScreenFactory", pkg+".ConfigScreenFactory");
		intToSrg.createTopLevelClassMapping("com.terraformersmc.modmenu.api.ModMenuApi", pkg+".ModMenuAdapter");
		intToSrg.createTopLevelClassMapping("com.terraformersmc.modmenu.api.ConfigScreenFactory", pkg+".ConfigScreenFactory");
		System.out.println("Remapping...");
		AtlasWithNewASM a = new AtlasWithNewASM();
		a.getClasspath().add(new File(args[5]).toPath());
		
		JsonObject refmap;
		if (refmapStr != null) {
			InheritanceProvider inh = new ClassProviderInheritanceProvider(Opcodes.ASM9, new JarFileClassProvider(new JarFile(new File(args[5]))));
			refmap = JsonParser.object().from(refmapStr);
			JsonObject nwMappings = new JsonObject();
			JsonObject mappings = refmap.getObject("mappings");
			for (Map.Entry<String, Object> en : mappings.entrySet()) {
				JsonObject nw = new JsonObject();
				nwMappings.put(en.getKey(), nw);
				JsonObject obj = (JsonObject)en.getValue();
				for (Map.Entry<String, Object> en2 : obj.entrySet()) {
					String mapping = (String)en2.getValue();
					String remapped = remap(mapping, en2.getKey(), intToSrg, yarnToInt, inh);
					if (remapped == null) continue;
					nw.put(en2.getKey(), remapped);
				}
			}
			refmap.put("mappings", nwMappings);
			refmap.getObject("data").put("named:srg", nwMappings.clone());
		} else {
			refmap = null;
		}
		String fabRelRefMap;
		String fabAbsRefMap;
		{
			InheritanceProvider inh = new ClassProviderInheritanceProvider(Opcodes.ASM9, new JarFileClassProvider(new JarFile(new File(args[5]))));
			Map<String, String> discardMap = new HashMap<>();
			if (fabAbsRefMapStr != null) {
				BufferedReader read = new BufferedReader(new StringReader(fabAbsRefMapStr));
				StringBuilder write = new StringBuilder();
				String l = read.readLine();
				while (l != null) {
					int i = l.indexOf(' ');
					if (i == -1) {
						write.append(l);
					} else {
						String remapped = remap(l.substring(i+1), "", intToSrg, discardMap, inh);
						if (remapped != null) {
							write.append(l, 0, i).append(' ').append(remapped);
						} else {
							write.append(l);
						}
					}
					l = read.readLine();
					if (l != null) write.append('\n');
				}
				String fabRefMap = write.toString();
				fabAbsRefMap = fabRefMap.isBlank() ? null : fabRefMap;
			} else {
				fabAbsRefMap = null;
			}
			if (fabRelRefMapStr != null) {
				BufferedReader read = new BufferedReader(new StringReader(fabRelRefMapStr));
				StringBuilder write = new StringBuilder();
				String k = read.readLine();
				if (k != null) write.append(k).append('\n');
				String l = k == null ? null : read.readLine();
				while (l != null) {
					String[] split = l.split("\t");
					for (int x=0; x<split.length; x++) {
						int i = split[x].indexOf(' ');
						if (i != -1) {
							String remapped = remap(split[x].substring(i+1), "", intToSrg, discardMap, inh);
							if (remapped != null) {
								split[x] = split[x].substring(0, i)+" "+remapped;
							}
						}
					}
					write.append(String.join("\t", split));
					k = read.readLine();
					if (k == null) break;
					else write.append('\n').append(k);
					l = read.readLine();
					if (l != null) write.append('\n');
				}
				String fabRefMap = write.toString();
				fabRelRefMap = fabRefMap.isBlank() ? null : fabRefMap;
			} else {
				fabRelRefMap = null;
			}
		}
		
		a.install(ctx -> {
			for (TopLevelClassMapping tlcm : srgToInt.getTopLevelClassMappings()) {
				completeRecursively(tlcm, ctx.inheritanceProvider());
			}
			return new JarEntryTransformer() {};
		});
		a.install(ctx -> new JarEntryRemappingTransformer(new LorenzRemapper(intToSrg, ctx.inheritanceProvider())) {
			@Override
			public JarManifestEntry transform(JarManifestEntry entry) {
				// avoid NPE due to missing Main-Class
				return entry;
			}
		});
		a.install(ctx -> new JarEntryTransformer() {
			@Override
			public JarResourceEntry transform(JarResourceEntry entry) {
				if (entry.getName().equals("fabric.mod.json")) {
					StringBuilder toml = new StringBuilder("modLoader=\"javafml\"\n");
					toml.append("loaderVersion=\"[32,)\"\n");
					toml.append("license=");
					toml.append(JsonWriter.string(fabricMod.get("license")));
					toml.append("\n[[mods]]\n");
					String[] keys = {
							"id", "modId",
							"version", "version",
							"name", "displayName",
							"authors", "authors",
							"description", "description"
					};
					for (int i = 0; i < keys.length; i += 2) {
						String fab = keys[i];
						String frg = keys[i+1];
						if (!fabricMod.has(fab)) continue;
						toml.append(frg);
						toml.append("=");
						toml.append(JsonWriter.string(fabricMod.get(fab)));
						toml.append("\n");
					}
					return new JarResourceEntry("META-INF/mods.toml", entry.getTime(), toml.toString().getBytes());
				} else if (entry.getName().equals(refmapFile)) {
					return new JarResourceEntry(entry.getName(), entry.getTime(), JsonWriter.indent("\t").string().value(refmap).done().getBytes());
				} else if (entry.getName().equals("fabRelRefMap.txt")) {
					if (fabRelRefMap != null) {
						return new JarResourceEntry(entry.getName(), entry.getTime(), fabRelRefMap.getBytes());
					}
				} else if (entry.getName().equals("fabAbsRefMap.txt")) {
					if (fabAbsRefMap != null) {
						return new JarResourceEntry(entry.getName(), entry.getTime(), fabAbsRefMap.getBytes());
					}
				} else if (entry.getName().endsWith(".accesswidener")) {
					try {
						BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(entry.getContents())));
						StringBuilder bldr = new StringBuilder();
						br.readLine();
						while (true) {
							String line = br.readLine();
							if (line == null) break;
							String[] split = line.split("\t");
							String access = split[0];
							String type = split[1];
							String mod;
							if ("accessible".equals(access)) {
								mod = "public ";
							} else {
								mod = "public-f ";
							}
							ClassMapping<?, ?> cm = intToSrg.getClassMapping(split[2]).orElse(null);
							if (cm != null) cm.complete(ctx.inheritanceProvider());
							bldr.append("# ");
							bldr.append(line);
							bldr.append("\n");
							if ("class".equals(type) || "extendable".equals(access)) {
								bldr.append(mod);
								bldr.append((cm == null ? split[2] : cm.getFullDeobfuscatedName()).replace('/', '.'));
								bldr.append("\n");
							}
							if ("method".equals(type)) {
								bldr.append(mod);
								bldr.append((cm == null ? split[2] : cm.getFullDeobfuscatedName()).replace('/', '.'));
								MethodMapping mm = cm == null ? null : cm.getMethodMapping(split[3], split[4]).orElse(null);
								bldr.append(" ");
								bldr.append(mm == null ? split[3] : mm.getDeobfuscatedName());
								bldr.append(mm == null ? split[4] : mm.getDeobfuscatedDescriptor());
								bldr.append("\n");
							} else if ("field".equals(type)) {
								bldr.append(mod);
								bldr.append((cm == null ? split[2] : cm.getFullDeobfuscatedName()).replace('/', '.'));
								FieldMapping fm = cm == null ? null : cm.getFieldMapping(split[3]).orElse(null);
								bldr.append(" ");
								bldr.append(fm == null ? split[3] : fm.getDeobfuscatedName());
								bldr.append("\n");
							}
						}
						return new JarResourceEntry("META-INF/accesstransformer.cfg", entry.getTime(), bldr.toString().getBytes());
					} catch (IOException e) {
						throw new UncheckedIOException(e);
					}
				}
				return entry;
			}
			
			@Override
			public JarManifestEntry transform(JarManifestEntry entry) {
				Attributes attr = entry.getManifest().getMainAttributes();
				attr.putValue("Specification-Title", fabricMod.getString("id"));
				if (fabricMod.has("authors")) attr.putValue("Specification-Vendor", fabricMod.getArray("authors").getString(0));
				attr.putValue("Specification-Version", "1");
				attr.putValue("Implementation-Title", fabricMod.getString("name"));
				if (fabricMod.has("authors")) attr.putValue("Implementation-Vendor", fabricMod.getArray("authors").getString(0));
				attr.putValue("Implementation-Version", fabricMod.getString("version"));
				attr.putValue("Implementation-Timestamp", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(new Date()));
				if (fabricMod.has("mixins")) {
					attr.putValue("MixinConfigs", fabricMod.getArray("mixins").getString(0));
				}
				return entry;
			}
			
			@Override
			public JarClassEntry transform(JarClassEntry entry) {
				ClassReader cr = new ClassReader(entry.getContents());
				ClassNode node = new ClassNode();
				final AtomicBoolean changed = new AtomicBoolean(false);
				cr.accept(node, 0);
				if (node.interfaces != null) {
					if (node.interfaces.contains("net/fabricmc/api/ModInitializer")) {
						node.interfaces.remove("net/fabricmc/api/ModInitializer");
						if (node.visibleAnnotations == null) node.visibleAnnotations = new ArrayList<>();
						AnnotationNode atMod = new AnnotationNode("Lnet/minecraftforge/fml/common/Mod;");
						atMod.values = new ArrayList<>();
						atMod.values.add("value");
						atMod.values.add(fabricMod.getString("id"));
						node.visibleAnnotations.add(atMod);
						String originalSuper = node.superName;
						node.superName = pkgBin+"/ConvertedModInitializer";
						for (MethodNode mn : node.methods) {
							if (mn.name.equals("<init>")) {
								// correct super() calls
								for (AbstractInsnNode insn : mn.instructions) {
									if (insn.getOpcode() == Opcodes.INVOKESPECIAL) {
										MethodInsnNode min = (MethodInsnNode)insn;
										if (min.name.equals("<init>") && min.owner.equals(originalSuper)) {
											min.owner = node.superName;
										}
									}
								}
							}
						}
						changed.set(true);
					}
				}
				node.visibleAnnotations = hoist(node.invisibleAnnotations, node.visibleAnnotations, () -> changed.set(true));
				for (MethodNode mn : node.methods) {
					mn.visibleAnnotations = hoist(mn.invisibleAnnotations, mn.visibleAnnotations, () -> changed.set(true));
				}
				for (FieldNode fn : node.fields) {
					fn.visibleAnnotations = hoist(fn.invisibleAnnotations, fn.visibleAnnotations, () -> changed.set(true));
				}
				if (node.invisibleAnnotations != null) {
					for (AnnotationNode ann : node.invisibleAnnotations) {
						if (ann.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) {
							for (int i = 0; i < ann.values.size(); i += 2) {
								String k = (String)ann.values.get(i);
								if ("value".equals(k) || "targets".equals(k)) {
									List<String> targets;
									if ("value".equals(k)) {
										targets = new ArrayList<>();
										for (Type t : (List<Type>)ann.values.get(i+1)) {
											targets.add(t.getClassName());
										}
									} else {
										targets = (List<String>)ann.values.get(i+1);
									}
									for (String tgt : targets) {
										ClassMapping<?, ?> cm;
										if (yarnToInt.containsKey(tgt)) {
											cm = intToSrg.getClassMapping(yarnToInt.get(tgt)).orElse(null);
											if (cm == null) {
												continue;
											}
										} else {
											ClassMapping<?, ?> cm2 = srgToInt.getClassMapping(tgt).orElse(null);
											if (cm2 == null) {
												continue;
											}
											cm = intToSrg.getClassMapping(cm2.getFullDeobfuscatedName()).get();
										}
										cm.complete(ctx.inheritanceProvider());
										if (cm != null) {
											Map<String, String> renamedMethods = new HashMap<>();
											Map<String, String> renamedFields = new HashMap<>();
											for (MethodNode mn : node.methods) {
												for (MethodMapping mm : cm.getMethodMappings()) {
													if (mm.getObfuscatedName().equals(mn.name)) {
														renamedMethods.put(mn.name+mn.desc, mm.getDeobfuscatedName()+"\0"+mm.getDeobfuscatedDescriptor());
														mn.name = mm.getDeobfuscatedName();
														mn.desc = mm.getDeobfuscatedDescriptor();
														changed.set(true);
														break;
													}
												}
											}
											for (FieldNode fn : node.fields) {
												FieldMapping fm = cm.getFieldMapping(fn.name).orElse(null);
												if (fm != null) {
													renamedFields.put(fn.name, fm.getDeobfuscatedName());
													fn.name = fm.getDeobfuscatedName();
													fn.desc = fm.getDeobfuscatedSignature().getType().get().toString();
													changed.set(true);
												}
											}
											for (MethodNode mn : node.methods) {
												for (AbstractInsnNode insn : mn.instructions) {
													if (insn instanceof FieldInsnNode) {
														FieldInsnNode fin = (FieldInsnNode)insn;
														if (renamedFields.containsKey(fin.name)) {
															fin.name = renamedFields.get(fin.name);
														}
														if (fin.name.startsWith("field_") && fin.name.lastIndexOf('_') == 5) {
															ClassMapping<?, ?> mcm2 = srgToInt.getClassMapping(fin.owner).orElse(null);
															if (mcm2 != null) {
																ClassMapping<?, ?> mcm = intToSrg.getClassMapping(mcm2.getDeobfuscatedName()).orElse(null);
																if (mcm != null) {
																	FieldMapping fm = mcm.getFieldMapping(fin.name).orElse(null);
																	if (fm != null) {
																		fin.name = fm.getDeobfuscatedName();
																		fin.desc = fm.getDeobfuscatedSignature().getType().get().toString();
																		changed.set(true);
																	}
																}
															}
														}
													} else if (insn instanceof MethodInsnNode) {
														MethodInsnNode min = (MethodInsnNode)insn;
														if (renamedMethods.containsKey(min.name+min.desc)) {
															String s = renamedMethods.get(min.name+min.desc);
															min.name = s.substring(0, s.indexOf('\0'));
															min.desc = s.substring(s.indexOf('\0')+1);
														}
														if (min.name.startsWith("method_")) {
															ClassMapping<?, ?> mcm2 = srgToInt.getClassMapping(min.owner).orElse(null);
															if (mcm2 != null) {
																ClassMapping<?, ?> mcm = intToSrg.getClassMapping(mcm2.getDeobfuscatedName()).orElse(null);
																if (mcm != null) {
																	MethodMapping mm = mcm.getMethodMapping(min.name, min.desc).orElse(null);
																	if (mm != null) {
																		min.name = mm.getDeobfuscatedName();
																		min.desc = mm.getDeobfuscatedDescriptor();
																		changed.set(true);
																	}
																}
															}
														}
													}
												}
											}
										}
									}
								}
							}
						}
					}
				}
				if (changed.get()) {
					ClassWriter cw = new ClassWriter(0);
					node.accept(cw);
					return new JarClassEntry(entry.getName(), entry.getTime(), cw.toByteArray());
				}
				return entry;
			}

			private List<AnnotationNode> hoist(List<AnnotationNode> invisible, List<AnnotationNode> visible, Runnable changed) {
				if (invisible == null) return visible;
				List<AnnotationNode> toHoist = new ArrayList<>();
				for (AnnotationNode ann : invisible) {
					if (ann.desc.equals("Lnet/minecraftforge/api/distmarker/OnlyIn;")) {
						toHoist.add(ann);
					}
				}
				if (!toHoist.isEmpty()) {
					if (visible == null) visible = new ArrayList<>();
					for (AnnotationNode ann : toHoist) {
						invisible.remove(ann);
						visible.add(ann);
					}
					changed.run();
				}
				return visible;
			}
			
		});
		a.run(Paths.get(args[0]), Paths.get(args[1]));
		System.out.println("Adding runtime...");
		FileSystem out = NIOHelper.openZip(Paths.get(args[1]), false);
		ZipFile runtime = new ZipFile(args[4]);
		for (ZipEntry ze : (Iterable<ZipEntry>)(Iterable)runtime.entries()::asIterator) {
			if (ze.getName().startsWith("META-INF")) continue;
			if (ze.isDirectory()) {
				Files.createDirectories(out.getPath(ze.getName()));
				continue;
			}
			try (OutputStream os = Files.newOutputStream(out.getPath(ze.getName()))) {
				runtime.getInputStream(ze).transferTo(os);
			}
		}
		runtime.close();
		out.close();
		System.out.println("Done!");
	}

	private static void mojifyRecursively(MappingSet offToSrg, MappingSet offToMojClient, MappingSet offToMojServer, MappingSet offToSrgWithMojClasses, ClassMapping<?, ?> cm) {
		Optional<? extends ClassMapping<?, ?>> mojClass = offToMojClient.getClassMapping(cm.getFullObfuscatedName());
		String suffix = "";
		if (!mojClass.isPresent()) {
			mojClass = offToMojServer.getClassMapping(cm.getFullObfuscatedName());
			if (!mojClass.isPresent()) {
				System.out.println("Can't find mapping for "+cm.getFullDeobfuscatedName());
			} else {
				suffix = " [server]";
			}
		} else {
			suffix = " [client]";
		}
		if (mojClass.isPresent()) {
			Optional<? extends ClassMapping<?, ?>> srg = offToSrg.getClassMapping(cm.getFullObfuscatedName());
			System.out.println("obf "+cm.getFullObfuscatedName()+
					" -> int "+cm.getFullDeobfuscatedName()+
					" -> srg "+srg.map(ClassMapping::getFullDeobfuscatedName).orElse("?")+
					" -> moj "+mojClass.get().getFullDeobfuscatedName()+suffix);
			offToSrgWithMojClasses.getClassMapping(cm.getFullObfuscatedName()).get().setDeobfuscatedName(mojClass.get().getFullDeobfuscatedName());
		}
		for (ClassMapping<?, ?> child : cm.getInnerClassMappings()) {
			mojifyRecursively(offToSrg, offToMojClient, offToMojServer, offToSrgWithMojClasses, child);
		}
	}
	
	private static <T extends org.cadixdev.bombe.type.Type> T remap(T ty, InheritanceProvider inh, MappingSet mappings) {
		if (ty instanceof ObjectType) {
			ObjectType ot = (ObjectType)ty;
			Optional<? extends ClassMapping<?, ?>> cm = mappings.getClassMapping(ot.getClassName());
			if (cm.isPresent()) {
				cm.get().complete(inh);
				return (T)new ObjectType(cm.get().getFullDeobfuscatedName());
			}
		}
		if (ty instanceof ArrayType) {
			return (T)new ArrayType(((ArrayType) ty).getDimCount(), remap(((ArrayType) ty).getComponent(), inh, mappings));
		}
		return ty;
	}

	private static void completeRecursively(ClassMapping<?, ?> cm, InheritanceProvider inh) {
		cm.complete(inh);
		for (ClassMapping<?, ?> child : cm.getInnerClassMappings()) {
			child.complete(inh);
		}
	}

	private static String remap(String mapping, String mappingClass, MappingSet intToSrg, Map<String, String> yarnToInt, InheritanceProvider inh) {
		if (mapping.equals("<init>") || mapping.equals("<clinit>")) return null;
		String remapped;
		int semi = mapping.indexOf(';');
		String clazz;
		if (mapping.startsWith("L")) {
			clazz = mapping.substring(1, semi);
		} else {
			clazz = null;
			semi = -1;
		}
		int paren = mapping.indexOf('(');
		if (paren == -1) {
			int colon = mapping.indexOf(':');
			if (colon == -1) {
				clazz = mapping;
				ClassMapping<?, ?> cm = intToSrg.getClassMapping(clazz).get();
				cm.complete(inh);
				System.out.println(mappingClass + " = " + clazz);
				yarnToInt.put(mappingClass, clazz);
				clazz = cm.getFullDeobfuscatedName();
				remapped = clazz;
			} else {
				String name = mapping.substring(semi+1, colon);
				String type = mapping.substring(colon+1);
				if (clazz != null) {
					ClassMapping<?, ?> cm = intToSrg.getClassMapping(clazz).get();
					cm.complete(inh);
					FieldMapping fm = cm.getFieldMapping(FieldSignature.of(name, type)).orElse(null);
					if (fm != null) {
						name = fm.getDeobfuscatedName();
						type = fm.getDeobfuscatedSignature().getType().get().toString();
					}
					clazz = cm.getFullDeobfuscatedName();
				} else {
					FieldSignature sig = FieldSignature.of(name, type);
					for (TopLevelClassMapping cm : intToSrg.getTopLevelClassMappings()) {
						cm.complete(inh);
						FieldMapping fm = getTopOrInnerMapping(inh, cm, m->m.getFieldMapping(sig));
						if (fm != null) {
							name = fm.getDeobfuscatedName();
							type = fm.getDeobfuscatedSignature().getType().get().toString();
							break;
						}
					}
				}
				remapped = (clazz == null ? "" : "L"+clazz+";")+name+":"+type;
			}
		} else {
			String name = mapping.substring(semi+1, paren);
			String desc = mapping.substring(paren);
			if (name.equals("<init>") || name.equals("<clinit>")) {
				MethodDescriptor parsed = new MethodDescriptorReader(desc).read();
				List<FieldType> newTypes = new ArrayList<>();
				org.cadixdev.bombe.type.Type newReturnType = remap(parsed.getReturnType(), inh, intToSrg);
				for (FieldType ft : parsed.getParamTypes()) {
					newTypes.add(remap(ft, inh, intToSrg));
				}
				desc = new MethodDescriptor(newTypes, newReturnType).toString();
				if (clazz != null) {
					ClassMapping<?, ?> cm = intToSrg.getClassMapping(clazz).get();
					cm.complete(inh);
					clazz = cm.getFullDeobfuscatedName();
				}
			} else if (clazz != null) {
				ClassMapping<?, ?> cm = intToSrg.getClassMapping(clazz).orElse(null);
				if (cm != null) {
					cm.complete(inh);
					MethodMapping mm = cm.getMethodMapping(name, desc).orElse(null);
					if (mm != null) {
						name = mm.getDeobfuscatedName();
						desc = mm.getDeobfuscatedDescriptor();
					}
					clazz = cm.getFullDeobfuscatedName();
				} else {
					System.err.println("Class mapping for "+clazz+" not found!");
				}
			} else {
				MethodSignature sig = MethodSignature.of(name, desc);
				for (TopLevelClassMapping cm : intToSrg.getTopLevelClassMappings()) {
					MethodMapping mm = getTopOrInnerMapping(inh, cm, m->m.getMethodMapping(sig));
					if (mm != null) {
						name = mm.getDeobfuscatedName();
						desc = mm.getDeobfuscatedDescriptor();
						break;
					}
				}
			}
			remapped = (clazz == null ? "" : "L"+clazz+";")+name+desc;
		}
		if (remapped.contains("class_") || remapped.contains("method_")) {
			System.out.println(mapping+" became "+remapped+", which still contains obvious Intermediary names!");
		}
		return remapped;
	}
	public static<T> T getTopOrInnerMapping(InheritanceProvider inh, TopLevelClassMapping cm, Function<ClassMapping<?, ?>, Optional<T>> get) {
		cm.complete(inh);
		Optional<T> mm = get.apply(cm);
		if (!mm.isEmpty()) return mm.get();
		Set<InnerClassMapping> dejavu = new HashSet<>();
		Collection<InnerClassMapping> icms1 = cm.getInnerClassMappings();
		List<InnerClassMapping> icms2;
		while (!icms1.isEmpty()) {
			icms2 = new ArrayList<>();
			for (InnerClassMapping icm : icms1) {
				if (dejavu.add(icm)) {
					icm.complete(inh);
					mm = get.apply(icm);
					if (!mm.isEmpty()) return mm.get();
					icms2.addAll(icm.getInnerClassMappings());
				}
			}
			icms1 = icms2;
		}
		return null;
	}
	
}
