<img src="https://git.sleeping.town/repo-avatars/19-3b9704897144fd61629c1b107c970a7f" align="right" width="180px"/>

# ForgeryTools

*Doesn't matter even if I stay here — [here is there](https://auvicmusic.bandcamp.com/track/here-is-there)*

**Warning**: This code is a horrible mess and there is *no* error handling. I
wrote this as a quick hack to port Fabrication to Forge as I had a sudden idea
due to the fact Forge supports Mixin now.

ForgeryTools is a cursed, hacky remapper built on CadixDev tools to remap Fabric
mods to be able to run on Forge. Its primary features are the ability to convert
a fabric.mod.json into a mods.toml (very minimally, does not remap dependencies
or anything else) and the ability to convert Mixin refmaps from Intermediary to
SRG.

***That is all it does***. It is *not* a silver bullet mystical magical Fabric
to Forge converter. To convert a mod successfully, it needs to have a special
custom "Forgery Runtime" made for it and the mod needs to be designed with
Forgery in mind.

## Contributing
Please don't.

## Building
Please don't.

`./gradlew clean shadow`

## Usage
Please don't.

For 1.16:
`java -jar ForgeryTools.jar <path to fabric mod> <path to forge mod output> <intermediary mappings> <mcp mappings> <forgery runtime> <intermediary minecraft jar> <package name>`

For 1.18:
`java -jar ForgeryTools.jar <path to fabric mod> <path to forge mod output> <intermediary mappings> <mcp mappings> <forgery runtime> <intermediary minecraft jar> <package name> <client mojmap mappings> <server mojmap mappings>`

See the 1.16 version of Fabrication for a [sample build script](https://github.com/unascribed/Fabrication/blob/2.0/1.16/build.sh) and a
[runtime project](https://github.com/unascribed/Fabrication/tree/2.0/1.16/forgery).
You can check [SkyChunk](https://github.com/LemmaEOF/Skychunk) for a more minimal example.
