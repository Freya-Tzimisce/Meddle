# Meddle

A tweak-class mod loader for Minecraft.  Should be compatible with any version of Minecraft capable of working under Launchwrapper.

Meddle acts as a pass-through to allow mods which implement net.minecraft.launchwrapper.ITweaker to run without the modder having to deal with manually installing them.

Mods must include a Tweak-Class directive in the jar manifest to point to the appropriate class to load.

Mod jars go into the .minecraft/meddle directory.

If desired, mods may include the MeddleMod annotation on their tweak class to designate an ID, name, author, version, and mod dependencies.

See the [DynamicMappings](https://github.com/Freya-Tzimisce/DynamicMappings) and [MeddleMods](https://github.com/FyberOptic/MeddleMods) projects for examples of Meddle's use.

For higher-level development, there's also [MeddleAPI](https://github.com/Freya-Tzimisce/MeddleAPI).
