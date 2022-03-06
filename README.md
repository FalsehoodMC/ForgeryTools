# ForgeryTools

**Warning**: This code is a horrible mess and there is *no* error handling. I
wrote this as a quick hack to port Fabrication to Forge as I had a sudden idea
due to the fact Forge supports Mixin now.

ForgeryTools is a cursed, hacky remapper built on CadixDev tools to remap Fabric
mods to be able to run on Forge. Its primary features are the ability to convert
a fabric.mod.json into a mods.toml (very minimally, does not remap dependencies
or anything else) and the ability to convert Mixin refmaps from Intermediary to
SRG.

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
