package com.unascribed.forgery;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.cadixdev.atlas.Atlas;
import org.cadixdev.atlas.util.NIOHelper;
import org.cadixdev.bombe.jar.JarClassEntry;
import org.cadixdev.bombe.jar.JarEntryTransformer;
import org.cadixdev.bombe.jar.JarManifestEntry;
import org.cadixdev.bombe.jar.JarResourceEntry;
import org.cadixdev.bombe.jar.asm.JarEntryRemappingTransformer;
import org.cadixdev.bombe.type.signature.FieldSignature;
import org.cadixdev.bombe.type.signature.MethodSignature;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.srg.tsrg.TSrgReader;
import org.cadixdev.lorenz.io.srg.tsrg.TSrgWriter;
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
import org.objectweb.asm.tree.TypeInsnNode;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.grack.nanojson.JsonWriter;

import org.cadixdev.lorenz.asm.LorenzRemapper;
import net.fabricmc.lorenztiny.TinyMappingsReader;
import net.fabricmc.mapping.tree.TinyMappingFactory;

@SuppressWarnings("deprecation")
public class Forgery {

	public static void main(String[] args) throws IOException, JsonParserException {
		System.err.println("Forgery v0.0.1");
		System.err.println("NOTICE: Forgery is NOT a silver bullet. It is not a magical Fabric-to-Forge converter. For a mod to successfully convert with Forgery, it must have changes made to it to work on both loaders. Forgery simply facilitates remapping and has a few runtime helpers.");
		if (args.length != 6) {
			System.err.println("Forgery requires four arguments. Input Fabric mod, output Forge mod, Intermediary tiny mappings, MCP mcp_mappings.tsrg, Forgery runtime JAR, Intermediary remapped Minecraft JAR.");
			System.err.println("You can find the Intermediary mappings in ~/.gradle/caches/fabric-loom/mappings/intermediary-1.16.4-v2.tiny");
			System.err.println("You can find the MCP joined.tsrg in ~/.gradle/caches/forge_gradle/minecraft_repo/versions/1.16.4/mcp_mappings.tsrg");
			System.err.println("You can find the Intermediary remapped Minecraft jar in ~/.gradle/caches/fabric-loom/minecraft-1.16.4-intermediary-net.fabricmc.yarn-1.16.4+build.6-v2.jar");
			System.exit(1);
			return;
		}
		ZipFile in = new ZipFile(args[0]);
		if (in.getEntry("fabric.mod.json") == null) {
			System.err.println(args[0]+" doesn't look like a Fabric mod.");
			return;
		}
		JsonObject fabricMod = JsonParser.object().from(in.getInputStream(in.getEntry("fabric.mod.json")));
		JsonObject mixins = null;
		String refmapFile;
		if (fabricMod.has("mixins") && !fabricMod.getArray("mixins").isEmpty()) {
			mixins = JsonParser.object().from(in.getInputStream(in.getEntry(fabricMod.getArray("mixins").getString(0))));
			refmapFile = mixins.getString("refmap");
		} else {
			refmapFile = null;
		}
		in.close();
		System.out.println("Building mappings...");
		MappingSet intToOff = new TinyMappingsReader(TinyMappingFactory.loadWithDetection(new BufferedReader(new FileReader(args[2]))), "official", "intermediary").read().reverse();
		MappingSet offToSrg = new TSrgReader(new FileReader(args[3])).read();
		MappingSet intToSrg = MappingSetMerger.create(intToOff, offToSrg, MergeConfig.builder()
				.withMethodMergeStrategy(MethodMergeStrategy.LOOSE)
				.withFieldMergeStrategy(FieldMergeStrategy.LOOSE)
				.build()).merge();
		new TSrgWriter(new FileWriter("merged.tsrg")).write(intToSrg);
		MappingSet srgToInt = intToSrg.reverse();
		intToSrg.createTopLevelClassMapping("net/fabricmc/api/Environment", "net/minecraftforge/api/distmarker/OnlyIn");
		TopLevelClassMapping envType = intToSrg.createTopLevelClassMapping("net/fabricmc/api/EnvType", "net/minecraftforge/api/distmarker/Dist");
		envType.createFieldMapping("SERVER", "DEDICATED_SERVER");
		System.out.println("Remapping...");
		Atlas a = new Atlas();
		a.getClasspath().add(new File(args[5]).toPath());
		a.install(ctx -> {
			for (TopLevelClassMapping tlcm : srgToInt.getTopLevelClassMappings()) {
				tlcm.complete(ctx.inheritanceProvider());
				// *shrug*
				for (InnerClassMapping icm : tlcm.getInnerClassMappings()) {
					icm.complete(ctx.inheritanceProvider());
					for (InnerClassMapping iicm : icm.getInnerClassMappings()) {
						iicm.complete(ctx.inheritanceProvider());
						for (InnerClassMapping iiicm : iicm.getInnerClassMappings()) {
							iiicm.complete(ctx.inheritanceProvider());
							for (InnerClassMapping iiiicm : iiicm.getInnerClassMappings()) {
								iiiicm.complete(ctx.inheritanceProvider());
							}
						}
					}
				}
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
						toml.append(frg);
						toml.append("=");
						toml.append(JsonWriter.string(fabricMod.get(fab)));
						toml.append("\n");
					}
					return new JarResourceEntry("META-INF/mods.toml", entry.getTime(), toml.toString().getBytes());
				} else if (entry.getName().equals(refmapFile)) {
					try {
						JsonObject refmap = JsonParser.object().from(new String(entry.getContents()));
						JsonObject nwMappings = new JsonObject();
						JsonObject mappings = refmap.getObject("mappings");
						for (Map.Entry<String, Object> en : mappings.entrySet()) {
							JsonObject nw = new JsonObject();
							nwMappings.put(en.getKey(), nw);
							JsonObject obj = (JsonObject)en.getValue();
							for (Map.Entry<String, Object> en2 : obj.entrySet()) {
								String mapping = (String)en2.getValue();
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
									String name = mapping.substring(semi+1, colon);
									String type = mapping.substring(colon+1);
									if (clazz != null) {
										ClassMapping<?, ?> cm = intToSrg.getClassMapping(clazz).get();
										cm.complete(ctx.inheritanceProvider());
										FieldMapping fm = cm.getFieldMapping(FieldSignature.of(name, type)).orElse(null);
										if (fm != null) {
											name = fm.getDeobfuscatedName();
											type = fm.getDeobfuscatedSignature().getType().get().toString();
										}
										clazz = cm.getFullDeobfuscatedName();
									} else {
										FieldSignature sig = FieldSignature.of(name, type);
										for (TopLevelClassMapping cm : intToSrg.getTopLevelClassMappings()) {
											cm.complete(ctx.inheritanceProvider());
											if (cm.hasFieldMapping(sig)) {
												FieldMapping fm = cm.getFieldMapping(sig).get();
												name = fm.getDeobfuscatedName();
												type = fm.getDeobfuscatedSignature().getType().get().toString();
												break;
											}
										}
									}
									remapped = (clazz == null ? "" : "L"+clazz+";")+name+":"+type;
								} else {
									String name = mapping.substring(semi+1, paren);
									String desc = mapping.substring(paren);
									if (clazz != null) {
										ClassMapping<?, ?> cm = intToSrg.getClassMapping(clazz).get();
										cm.complete(ctx.inheritanceProvider());
										MethodMapping mm = cm.getMethodMapping(name, desc).orElse(null);
										if (mm != null) {
											name = mm.getDeobfuscatedName();
											desc = mm.getDeobfuscatedDescriptor();
										}
										clazz = cm.getFullDeobfuscatedName();
									} else {
										MethodSignature sig = MethodSignature.of(name, desc);
										for (TopLevelClassMapping cm : intToSrg.getTopLevelClassMappings()) {
											cm.complete(ctx.inheritanceProvider());
											if (cm.hasMethodMapping(sig)) {
												MethodMapping mm = cm.getMethodMapping(sig).get();
												name = mm.getDeobfuscatedName();
												desc = mm.getDeobfuscatedDescriptor();
												break;
											}
										}
									}
									remapped = (clazz == null ? "" : "L"+clazz+";")+name+desc;
								}
								if (remapped.contains("class_") || remapped.contains("field_") || remapped.contains("method_")) {
									System.out.println(mapping+" became "+remapped+", which still contains Intermediary names!");
								}
								nw.put(en2.getKey(), remapped);
							}
						}
						refmap.put("mappings", nwMappings);
						refmap.getObject("data").put("named:srg", nwMappings.clone());
						return new JarResourceEntry(entry.getName(), entry.getTime(), JsonWriter.indent("\t").string().value(refmap).done().getBytes());
					} catch (JsonParserException e) {
						throw new IllegalArgumentException(e);
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
				attr.putValue("Specification-Vendor", fabricMod.getArray("authors").getString(0));
				attr.putValue("Specification-Version", "1");
				attr.putValue("Implementation-Title", fabricMod.getString("name"));
				attr.putValue("Implementation-Vendor", fabricMod.getArray("authors").getString(0));
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
				boolean changed = false;
				cr.accept(node, 0);
				if (node.interfaces != null && node.interfaces.contains("net/fabricmc/api/ModInitializer")) {
					node.interfaces.remove("net/fabricmc/api/ModInitializer");
					if (node.visibleAnnotations == null) node.visibleAnnotations = new ArrayList<>();
					AnnotationNode atMod = new AnnotationNode("Lnet/minecraftforge/fml/common/Mod;");
					atMod.values = new ArrayList<>();
					atMod.values.add("value");
					atMod.values.add(fabricMod.getString("id"));
					node.visibleAnnotations.add(atMod);
					String originalSuper = node.superName;
					node.superName = "com/unascribed/forgery/ConvertedModInitializer";
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
					changed = true;
				} else if (node.name.equals("com/unascribed/fabrication/Agnos")) {
					for (MethodNode mn : node.methods) {
						if (mn.name.equals("<clinit>")) {
							for (AbstractInsnNode insn : mn.instructions) {
								if (insn.getOpcode() == Opcodes.NEW) {
									TypeInsnNode tin = (TypeInsnNode)insn;
									tin.desc = tin.desc.replace("fabrication/FabricAgnos", "forgery/ForgeAgnos");
								} else if (insn.getOpcode() == Opcodes.INVOKESPECIAL) {
									MethodInsnNode min = (MethodInsnNode)insn;
									min.owner = min.owner.replace("fabrication/FabricAgnos", "forgery/ForgeAgnos");
								}
							}
						}
					}
					changed = true;
				}
				if (node.invisibleAnnotations != null) {
					for (AnnotationNode ann : node.invisibleAnnotations) {
						if (ann.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) {
							for (int i = 0; i < ann.values.size(); i += 2) {
								String k = (String)ann.values.get(i);
								if ("value".equals(k)) {
									List<Type> types = (List<Type>)ann.values.get(i+1);
									for (Type t : types) {
										ClassMapping<?, ?> cm2 = srgToInt.getTopLevelClassMapping(t.getClassName()).orElse(null);
										if (cm2 != null) {
											ClassMapping<?, ?> cm = intToSrg.getTopLevelClassMapping(cm2.getFullDeobfuscatedName()).orElse(null);
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
															changed = true;
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
														changed = true;
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
																	ClassMapping<?, ?> mcm = intToSrg.getClassMapping(cm2.getDeobfuscatedName()).orElse(null);
																	if (mcm != null) {
																		FieldMapping fm = mcm.getFieldMapping(fin.name).orElse(null);
																		if (fm != null) {
																			fin.name = fm.getDeobfuscatedName();
																			fin.desc = fm.getDeobfuscatedSignature().getType().get().toString();
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
																	ClassMapping<?, ?> mcm = intToSrg.getClassMapping(cm2.getDeobfuscatedName()).orElse(null);
																	if (mcm != null) {
																		MethodMapping mm = mcm.getMethodMapping(min.name, min.desc).orElse(null);
																		if (mm != null) {
																			min.name = mm.getDeobfuscatedName();
																			min.desc = mm.getDeobfuscatedDescriptor();
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
				}
				if (changed) {
					ClassWriter cw = new ClassWriter(0);
					node.accept(cw);
					return new JarClassEntry(entry.getName(), entry.getTime(), cw.toByteArray());
				}
				return entry;
			}
			
		});
		a.run(Paths.get(args[0]), Paths.get(args[1]));
		System.out.println("Adding runtime...");
		FileSystem out = NIOHelper.openZip(Paths.get(args[1]), false);
		ZipFile runtime = new ZipFile(args[4]);
		for (ZipEntry ze : (Iterable<ZipEntry>)(Iterable)runtime.entries()::asIterator) {
			if (ze.getName().startsWith("META-INF")) continue;
			if (ze.getName().endsWith("fabrication/Agnos.class")) continue;
			if (ze.isDirectory()) {
				Files.createDirectories(out.getPath(ze.getName()));
				continue;
			}
			try (OutputStream os = Files.newOutputStream(out.getPath(ze.getName()))) {
				runtime.getInputStream(ze).transferTo(os);
			}
		}
		Path p = out.getPath("com/unascribed/fabrication/FabricAgnos.class");
		if (Files.exists(p)) {
			Files.delete(p);
		}
		runtime.close();
		out.close();
		System.out.println("Done!");
	}
	
}
