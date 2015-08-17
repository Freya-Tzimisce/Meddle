package net.fybertech.dynamicmappings;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

import net.fybertech.meddle.MeddleUtil;


public class DynamicMappings
{
	public static final Logger LOGGER = LogManager.getLogger("Meddle");

	// The deobfuscated -> obfuscated name.
	public static final Map<String, String> classMappings = new HashMap<String, String>();
	public static final Map<String, String> reverseClassMappings = new HashMap<String, String>();

	public static final Map<String, String> fieldMappings = new HashMap<String, String>();
	public static final Map<String, String> reverseFieldMappings = new HashMap<String, String>();

	public static final Map<String, String> methodMappings = new HashMap<String, String>();
	public static final Map<String, String> reverseMethodMappings = new HashMap<String, String>();

	// Used by getClassNode to avoid reloading the classes over and over
	private static Map<String, ClassNode> cachedClassNodes = new HashMap<String, ClassNode>();


	// Load a ClassNode from class name.  This is for loading the original
	// obfuscated classes.
	//
	// Note: *Do not* edit classes you get from this.  They're cached and used by
	// anyone doing analysis of vanilla class files.
	public static ClassNode getClassNode(String className)
	{
		if (className == null) return null;

		className = className.replace(".", "/");
		if (cachedClassNodes.containsKey(className)) return cachedClassNodes.get(className);

		//InputStream stream = Launch.classLoader.getResourceAsStream(className + ".class");
		InputStream stream = DynamicMappings.class.getClassLoader().getResourceAsStream(className + ".class");
		if (stream == null) return null;

		ClassReader reader = null;
		try {
			reader = new ClassReader(stream);
		} catch (IOException e) { return null; }

		ClassNode cn = new ClassNode();
		reader.accept(cn, 0);

		cachedClassNodes.put(className, cn);

		return cn;
	}


	// Get constant pool string that an LDC is loading
	public static String getLdcString(AbstractInsnNode node)
	{
		if (!(node instanceof LdcInsnNode)) return null;
		LdcInsnNode ldc = (LdcInsnNode)node;
		if (!(ldc.cst instanceof String)) return null;
		return new String((String)ldc.cst);
	}


	// Get constant pool class that an LDC is referencing
	public static String getLdcClass(AbstractInsnNode node)
	{
		if (!(node instanceof LdcInsnNode)) return null;
		LdcInsnNode ldc = (LdcInsnNode)node;
		if (!(ldc.cst instanceof Type)) return null;
		return ((Type)ldc.cst).getClassName();
	}


	// Check if LDC is loading the specified string
	public static boolean isLdcWithString(AbstractInsnNode node, String string)
	{
		String s = getLdcString(node);
		return (s != null && string.equals(s));
	}


	// Check if LDC is loading specified int
	public static boolean isLdcWithInteger(AbstractInsnNode node, int val)
	{
		if (!(node instanceof LdcInsnNode)) return false;
		LdcInsnNode ldc = (LdcInsnNode)node;
		if (!(ldc.cst instanceof Integer)) return false;
		return ((Integer)ldc.cst) == val;
	}


	// Get the description of the specified field from the class
	public static String getFieldDesc(ClassNode cn, String fieldName)
	{
		for (FieldNode field : (List<FieldNode>)cn.fields)
		{
			if (field.name.equals(fieldName)) return field.desc;
		}
		return null;
	}


	// Get the specified field node from the class
	public static FieldNode getFieldByName(ClassNode cn, String fieldName)
	{
		for (FieldNode field : (List<FieldNode>)cn.fields)
		{
			if (field.name.equals(fieldName)) return field;
		}
		return null;
	}


	// Search a class's constant pool for the list of strings.
	// NOTE: Strings are trimmed!  Take into account when matching.
	public static boolean searchConstantPoolForStrings(String className, String... matchStrings)
	{
		if (className == null) return false;
		className = className.replace(".", "/");
		//InputStream stream = Launch.classLoader.getResourceAsStream(className + ".class");
		InputStream stream = DynamicMappings.class.getClassLoader().getResourceAsStream(className + ".class");
		if (stream == null) return false;

		ClassReader reader = null;
		try {
			reader = new ClassReader(stream);
		} catch (IOException e) { return false; }

		int itemCount = reader.getItemCount();
		char[] buffer = new char[reader.getMaxStringLength()];

		int matches = 0;

		for (int n = 1; n < itemCount; n++)	{
			int pos = reader.getItem(n);
			if (pos == 0 || reader.b[pos - 1] != 8) continue;

			Arrays.fill(buffer, (char)0);
			reader.readUTF8(pos,  buffer);
			String string = (new String(buffer)).trim();

			for (int n2 = 0; n2 < matchStrings.length; n2++) {
				if (string.equals(matchStrings[n2])) { matches++; break; }
			}
		}

		return (matches == matchStrings.length);
	}

	public static boolean searchConstantPoolForClasses(String className, String... matchStrings)
	{
		className = className.replace(".", "/");
		InputStream stream = DynamicMappings.class.getClassLoader().getResourceAsStream(className + ".class");
		if (stream == null) return false;

		ClassReader reader = null;
		try {
			reader = new ClassReader(stream);
		} catch (IOException e) { return false; }

		int itemCount = reader.getItemCount();
		char[] buffer = new char[reader.getMaxStringLength()];

		int matches = 0;

		for (int n = 1; n < itemCount; n++)	{
			int pos = reader.getItem(n);
			if (pos == 0 || reader.b[pos - 1] != 7) continue;

			Arrays.fill(buffer, (char)0);
			reader.readUTF8(pos,  buffer);
			String string = (new String(buffer)).trim();

			for (int n2 = 0; n2 < matchStrings.length; n2++) {
				if (string.equals(matchStrings[n2].replace(".", "/"))) { matches++; break; }
			}
		}

		return (matches == matchStrings.length);
	}


	public static List<String> getConstantPoolStrings(String className)
	{
		List<String> strings = new ArrayList<String>();

		className = className.replace(".", "/");
		//InputStream stream = Launch.classLoader.getResourceAsStream(className + ".class");
		InputStream stream = DynamicMappings.class.getClassLoader().getResourceAsStream(className + ".class");
		if (stream == null) return null;

		ClassReader reader = null;
		try {
			reader = new ClassReader(stream);
		} catch (IOException e) { return null; }

		int itemCount = reader.getItemCount();
		char[] buffer = new char[reader.getMaxStringLength()];

		for (int n = 1; n < itemCount; n++)	{
			int pos = reader.getItem(n);
			if (pos == 0 || reader.b[pos - 1] != 8) continue;

			Arrays.fill(buffer, (char)0);
			reader.readUTF8(pos,  buffer);
			String string = (new String(buffer)).trim();

			strings.add(string);
		}

		return strings;
	}


	// Confirm the parameter types of a method.
	// Uses org.objectweb.asm.Type for values.
	public static boolean checkMethodParameters(MethodNode method, int ... types)
	{
		Type t = Type.getMethodType(method.desc);
		Type[] args = t.getArgumentTypes();
		if (args.length != types.length) return false;

		int len = args.length;
		for (int n = 0; n < len; n++) {
			if (args[n].getSort() != types[n]) return false;
		}

		return true;
	}


	// Finds all methods matching the specified name and/or description.
	// Both are optional.
	public static List<MethodNode> getMatchingMethods(ClassNode cn, String name, String desc)
	{
		List<MethodNode> output = new ArrayList<MethodNode>();

		for (MethodNode method : (List<MethodNode>)cn.methods) {
			if ((name == null || (name != null && method.name.equals(name))) &&
					(desc == null || (desc != null && method.desc.equals(desc)))) output.add(method);
		}

		return output;
	}


	public static List<FieldNode> getMatchingFields(ClassNode cn, String name, String desc)
	{
		List<FieldNode> output = new ArrayList<FieldNode>();

		for (FieldNode field : (List<FieldNode>)cn.fields) {
			if ((name == null || (name != null && field.name.equals(name))) &&
					(desc == null || (desc != null && field.desc.equals(desc)))) output.add(field);
		}

		return output;
	}


	public static ClassNode getClassNodeFromMapping(String string)
	{
		return getClassNode(getClassMapping(string));
	}






	// The starting point
	@Mapping(provides="net/minecraft/server/MinecraftServer")
	public static boolean getMinecraftServerClass()
	{
		ClassNode cn = getClassNode("net/minecraft/server/MinecraftServer");
		if (cn == null) return false;
		addClassMapping("net/minecraft/server/MinecraftServer", cn);
		return true;
	}


	@Mapping(provides="net/minecraft/world/World", depends="net/minecraft/server/MinecraftServer")
	public static boolean getWorldClass()
	{
		ClassNode server = getClassNode(getClassMapping("net/minecraft/server/MinecraftServer"));
		if (server == null) return false;

		List<String> potentialClasses = new ArrayList<String>();

		// Fetch all obfuscated classes used inside MinecraftServer's interfaces
		for (String interfaceClass : (List<String>)server.interfaces) {
			if (interfaceClass.contains("/")) continue;

			ClassNode node = getClassNode(interfaceClass);
			if (node == null) continue; // TODO - Error message

			for (MethodNode method : node.methods) {
				Type returnType = Type.getReturnType(method.desc);

				if (returnType.getSort() == Type.OBJECT) {
					if (!returnType.getClassName().contains(".")) potentialClasses.add(returnType.getClassName());
				}
			}
		}

		String[] matchStrings = new String[] { "Getting biome", "chunkCheck", "Level name", "Chunk stats" };

		// Search constant pools of potential classes for strings matching the list above
		for (String className : potentialClasses)
		{
			if (searchConstantPoolForStrings(className, matchStrings))
			{
				ClassNode worldClass = getClassNode(className);
				addClassMapping("net/minecraft/world/World", worldClass);
				return true;
			}
		}

		return false;
	}


	@Mapping(provides="net/minecraft/init/Blocks", depends="net/minecraft/world/World")
	public static boolean getBlocksClass()
	{
		ClassNode worldClass = getClassNode(getClassMapping("net/minecraft/world/World"));
		if (worldClass == null) return false;

		Set<String> potentialClasses = new HashSet<String>();

		// Discover and filter all static fields accessed in World's methods
		for (MethodNode method : (List<MethodNode>)worldClass.methods) {
			for (AbstractInsnNode node = method.instructions.getFirst(); node != null; node = node.getNext()) {
				if (node.getOpcode() != Opcodes.GETSTATIC) continue;
				FieldInsnNode fieldNode = (FieldInsnNode)node;

				if (!fieldNode.desc.startsWith("L") || fieldNode.desc.contains("/")) continue;
				String descClass = Type.getType(fieldNode.desc).getClassName();
				if (fieldNode.owner.equals(descClass)) continue;

				potentialClasses.add(fieldNode.owner);
			}
		}

		String[] matchStrings = new String[] { "Accessed Blocks before Bootstrap!", "air", "stone" };

		// Find net.minecraft.init.Blocks from what's left
		for (String className : potentialClasses) {
			if (searchConstantPoolForStrings(className, matchStrings))	{
				ClassNode blocksClass = getClassNode(className);
				addClassMapping("net/minecraft/init/Blocks", blocksClass);
				return true;
			}
		}

		return false;
	}


	@Mapping(provides="net/minecraft/block/Block", depends="net/minecraft/init/Blocks")
	public static boolean getBlockClass()
	{
		ClassNode blocksClass = getClassNode(getClassMapping("net/minecraft/init/Blocks"));
		if (blocksClass == null) return false;

		Map<String, Integer> classes = new HashMap<String, Integer>();

		// Make a tally of how many times a class was used in the field descriptors
		for (FieldNode field : (List<FieldNode>)blocksClass.fields) {
			String descClass = Type.getType(field.desc).getClassName();
			int val = (classes.containsKey(descClass) ? classes.get(descClass) : 0);
			val++;
			classes.put(descClass,  val);
		}

		String mostClass = null;
		int mostCount = 0;

		// Find the one with the highest count
		for (String key : classes.keySet()) {
			if (classes.get(key) > mostCount) { mostClass = key; mostCount = classes.get(key); }
		}

		// The standard Block class is used far more than any other, so we've found it
		if (mostCount > 100) {
			ClassNode blockClass = getClassNode(mostClass);
			addClassMapping("net/minecraft/block/Block", blockClass);
			return true;
		}

		return false;

	}


	@Mapping(provides="net/minecraft/block/state/IBlockState", depends="net/minecraft/block/Block")
	public static boolean getIBlockStateClass()
	{
		ClassNode block = getClassNode(getClassMapping("net/minecraft/block/Block"));
		if (block == null) return false;

		Map<String, Integer> counts = new HashMap<String, Integer>();

		// Count the times a class is used as the third parameter of a method
		for (MethodNode method : (List<MethodNode>)block.methods) {
			Type t = Type.getMethodType(method.desc);
			Type[] args = t.getArgumentTypes();
			if (args.length < 3) continue;

			String name = args[2].getClassName();
			int count = (counts.containsKey(name) ? counts.get(name) : 0);
			count++;
			counts.put(name,  count);
		}

		int max = 0;
		String maxClass = null;

		// Find the one with the most
		for (String key : counts.keySet()) {
			int count = counts.get(key);
			if (count > max) { max = count; maxClass = key; }
		}

		// It should be over 15
		if (max < 10) return false;

		ClassNode classBlockState = getClassNode(maxClass);
		addClassMapping("net/minecraft/block/state/IBlockState",  classBlockState);

		return true;
	}


	@Mapping(provides="net/minecraft/world/IBlockAccess", depends="net/minecraft/world/World")
	public static boolean getIBlockAccessClass()
	{
		ClassNode world = getClassNode(getClassMapping("net/minecraft/world/World"));
		if (world == null) return false;

		// We won't know for sure if they added more interfaces
		if (world.interfaces.size() != 1) return false;

		ClassNode classBlockAccess = getClassNode(world.interfaces.get(0));
		addClassMapping("net/minecraft/world/IBlockAccess",  classBlockAccess);
		return true;
	}


	@Mapping(provides="net/minecraft/util/BlockPos", depends="net/minecraft/block/Block")
	public static boolean getBlockPosClass()
	{
		ClassNode block = getClassNode(getClassMapping("net/minecraft/block/Block"));
		if (block == null) return false;

		Map<String, Integer> counts = new HashMap<String, Integer>();

		// Count the times a class is used as the second parameter of a method
		for (MethodNode method : (List<MethodNode>)block.methods) {
			Type t = Type.getMethodType(method.desc);
			Type[] args = t.getArgumentTypes();
			if (args.length < 2) continue;

			String name = args[1].getClassName();
			int count = (counts.containsKey(name) ? counts.get(name) : 0);
			count++;
			counts.put(name,  count);
		}

		int max = 0;
		String maxClass = null;

		// Find the one with the most
		for (String key : counts.keySet()) {
			int count = counts.get(key);
			if (count > max) { max = count; maxClass = key; }
		}

		// It should be over 30
		if (max < 10) return false;

		ClassNode blockPos = getClassNode(maxClass);
		addClassMapping("net/minecraft/util/BlockPos", blockPos);

		return true;
	}


	@Mapping(provides={
			"net/minecraft/entity/item/EntityItem",
	"net/minecraft/item/ItemStack"},
	providesMethods={
			"net/minecraft/block/Block spawnAsEntity (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/item/ItemStack;)V"
	},
	depends={
			"net/minecraft/world/World",
			"net/minecraft/util/BlockPos",
	"net/minecraft/block/Block"})
	public static boolean getEntityItemClass()
	{
		ClassNode worldClass = getClassNode(getClassMapping("net/minecraft/world/World"));
		ClassNode blockPosClass = getClassNode(getClassMapping("net/minecraft/util/BlockPos"));
		ClassNode blockClass = getClassNode(getClassMapping("net/minecraft/block/Block"));
		if (!MeddleUtil.notNull(worldClass, blockPosClass, blockClass)) return false;

		// search for Block.spawnAsEntity(World worldIn, BlockPos pos, ItemStack stack)
		for (MethodNode method : (List<MethodNode>)blockClass.methods)
		{
			// We're looking for a static method
			if ((method.access & Opcodes.ACC_STATIC) != Opcodes.ACC_STATIC) continue;

			// Look for methods with three arguments
			Type methodType = Type.getMethodType(method.desc);
			Type[] arguments = methodType.getArgumentTypes();
			if (arguments.length != 3) continue;

			// Make sure all arguments are objects
			if (arguments[0].getSort() != Type.OBJECT || arguments[1].getSort() != Type.OBJECT || arguments[2].getSort() != Type.OBJECT ) continue;
			// Make sure arg0 is World and arg1 is BlockPos
			if (!arguments[0].getClassName().equals(worldClass.name) || !arguments[1].getClassName().equals(blockPosClass.name)) continue;

			boolean foundString = false;
			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext())
			{
				if (!foundString && isLdcWithString(insn, "doTileDrops")) { foundString = true; continue; }
				if (foundString && insn.getOpcode() == Opcodes.NEW)
				{
					TypeInsnNode newNode = (TypeInsnNode)insn;
					addClassMapping("net/minecraft/entity/item/EntityItem", getClassNode(newNode.desc));

					// This also means that the last arg for spawnAsEntity is ItemStack
					addClassMapping("net/minecraft/item/ItemStack", getClassNode(arguments[2].getClassName()));

					addMethodMapping("net/minecraft/block/Block spawnAsEntity (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/item/ItemStack;)V",
							blockClass.name + " " + method.name + " " + method.desc);

					return true;
				}
			}

		}

		return false;
	}


	@Mapping(provides={
			"net/minecraft/block/BlockFire",
			"net/minecraft/block/BlockLeaves",
	"net/minecraft/block/BlockChest"},
	depends={
			"net/minecraft/init/Blocks",
	"net/minecraft/block/Block"})
	public static boolean discoverBlocksFields()
	{
		Map<String, String> blocksClassFields = new HashMap<String, String>();

		ClassNode blockClass = getClassNode(getClassMapping("net/minecraft/block/Block"));
		ClassNode blocksClass = getClassNode(getClassMapping("net/minecraft/init/Blocks"));
		if (blockClass == null || blocksClass == null) return false;

		// Generate list
		for (MethodNode method : (List<MethodNode>)blocksClass.methods)
		{
			if (!method.name.equals("<clinit>")) continue;

			String lastString = null;
			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext())
			{
				String s = getLdcString(insn);
				if (s != null) lastString = s;
				// Avoid any strings that definitely aren't block names
				if (lastString == null || lastString.contains(" ")) continue;

				if (insn.getOpcode() != Opcodes.PUTSTATIC) continue;
				FieldInsnNode fieldNode = (FieldInsnNode)insn;
				// Filter out non-objects and packaged classes, just in case
				if (!fieldNode.desc.startsWith("L") || fieldNode.desc.contains("/")) continue;
				// Filter out generic ones extending just the block class
				if (fieldNode.desc.equals("L" + blockClass.name + ";")) continue;

				blocksClassFields.put(lastString, fieldNode.desc.substring(1, fieldNode.desc.length() - 1));
			}
		}

		String className;

		/*
			grass, flowing_water, water, flowing_lava, lava, sand, leaves2, sticky_piston, tallgrass,
			deadbush, piston, piston_head, piston_extension, yellow_flower, red_flower, brown_mushroom,
			red_mushroom, double_stone_slab, stone_slab, redstone_wire, cactus, reeds,
			portal, unpowered_repeater, powered_repeater, mycelium, cauldron, double_wooden_slab, wooden_slab,
			tripwire_hook, beacon, skull, unpowered_comparator, powered_comparator, daylight_detector,
			daylight_detector_inverted, hopper, double_plant, stained_glass, stained_glass_pane,
			double_stone_slab2, stone_slab2, purpur_double_slab, purpur_slab
		 */

		className = blocksClassFields.get("fire");
		if (className != null && searchConstantPoolForStrings(className, "doFireTick")) {
			addClassMapping("net/minecraft/block/BlockFire", getClassNode(className));
		}

		className = blocksClassFields.get("leaves");
		if (className != null && searchConstantPoolForStrings(className, "decayable")) {
			addClassMapping("net/minecraft/block/BlockLeaves", getClassNode(className));
		}

		className = blocksClassFields.get("chest");
		if (className != null && searchConstantPoolForStrings(className, "container.chestDouble")) {
			addClassMapping("net/minecraft/block/BlockChest", getClassNode(className));
		}


		return true;
	}


	@Mapping(provides="net/minecraft/block/BlockLeavesBase",
			depends={"net/minecraft/block/Block", "net/minecraft/block/BlockLeaves"})
	public static boolean getBlockLeavesBaseClass()
	{
		ClassNode blockLeaves = getClassNode(getClassMapping("net/minecraft/block/BlockLeaves"));
		ClassNode blockClass = getClassNode(getClassMapping("net/minecraft/block/Block"));
		if (blockClass == null || blockLeaves == null || blockLeaves.superName == null) return false;

		// BlockLeaves extends BlockLeavesBase
		ClassNode blockLeavesBase = getClassNode(blockLeaves.superName);
		if (blockLeavesBase == null || blockLeavesBase.superName == null) return false;

		// BlockLeavesBase should then extend Block, otherwise class hierarchy is unknown
		if (!blockClass.name.equals(blockLeavesBase.superName)) return false;

		addClassMapping("net/minecraft/block/BlockLeavesBase", blockLeavesBase);
		return true;
	}


	@Mapping(provides="net/minecraft/item/Item",
			depends={"net/minecraft/block/Block", "net/minecraft/item/ItemStack"})
	public static boolean getItemClass()
	{
		ClassNode blockClass = getClassNode(getClassMapping("net/minecraft/block/Block"));
		ClassNode itemStackClass = getClassNode(getClassMapping("net/minecraft/item/ItemStack"));
		if (blockClass == null || itemStackClass == null) return false;

		List<String> possibleClasses = new ArrayList<String>();

		for (MethodNode method : (List<MethodNode>)itemStackClass.methods)
		{
			if (!method.name.equals("<init>")) continue;
			Type t = Type.getMethodType(method.desc);
			Type[] args = t.getArgumentTypes();
			if (args.length != 1 || args[0].getSort() != Type.OBJECT) continue;

			// Get the class of the first method parameter
			String className = args[0].getClassName();
			// One of them will be the block class, ignore it
			if (className.equals(blockClass.name)) continue;
			possibleClasses.add(className);
		}

		if (possibleClasses.size() == 1) {
			String className = possibleClasses.get(0);
			if (searchConstantPoolForStrings(className, "item.", "arrow")) {
				addClassMapping("net/minecraft/item/Item", getClassNode(possibleClasses.get(0)));
				return true;
			}
		}

		return false;
	}



	@Mapping(provides={"net/minecraft/block/BlockContainer", "net/minecraft/block/ITileEntityProvider"},
			depends= {"net/minecraft/block/Block", "net/minecraft/block/BlockChest"})
	public static boolean getBlockContainerClass()
	{
		ClassNode blockClass = getClassNode(getClassMapping("net/minecraft/block/Block"));
		ClassNode blockChestClass = getClassNode(getClassMapping("net/minecraft/block/BlockChest"));
		if (blockClass == null || blockChestClass == null || blockChestClass.superName == null) return false;

		ClassNode containerClass = getClassNode(blockChestClass.superName);
		if (!containerClass.superName.equals(blockClass.name)) return false;

		if (containerClass.interfaces.size() != 1) return false;

		addClassMapping("net/minecraft/block/BlockContainer", containerClass);
		addClassMapping("net/minecraft/block/ITileEntityProvider", getClassNode(containerClass.interfaces.get(0)));

		return true;
	}


	@Mapping(provides="net/minecraft/tileentity/TileEntity", depends="net/minecraft/block/ITileEntityProvider")
	public static boolean getTileEntityClass()
	{
		ClassNode teProviderClass = getClassNode(getClassMapping("net/minecraft/block/ITileEntityProvider"));
		if (teProviderClass == null) return false;

		for (MethodNode method : teProviderClass.methods)
		{
			Type t = Type.getMethodType(method.desc);
			Type returnType = t.getReturnType();
			if (returnType.getSort() != Type.OBJECT) return false;

			String teClassName = returnType.getClassName();
			if (searchConstantPoolForStrings(teClassName, "Furnace", "MobSpawner")) {
				addClassMapping("net/minecraft/tileentity/TileEntity", getClassNode(teClassName));
				return true;
			}
		}

		return false;
	}


	@Mapping(provides="net/minecraft/entity/player/EntityPlayer",
			depends={
			"net/minecraft/server/MinecraftServer",
			"net/minecraft/world/World",
	"net/minecraft/util/BlockPos"})
	public static boolean getEntityPlayerClass()
	{
		ClassNode serverClass = getClassNode(getClassMapping("net/minecraft/server/MinecraftServer"));
		ClassNode worldClass = getClassNode(getClassMapping("net/minecraft/world/World"));
		ClassNode blockPosClass = getClassNode(getClassMapping("net/minecraft/util/BlockPos"));
		if (serverClass == null || worldClass == null || blockPosClass == null) return false;

		List<String> potentialClasses = new ArrayList<String>();

		// Find isBlockProtected(World, BlockPos, EntityPlayer)Z
		for (MethodNode method : (List<MethodNode>)serverClass.methods)
		{
			Type t = Type.getMethodType(method.desc);
			if (t.getReturnType().getSort() != Type.BOOLEAN) continue;

			if (!checkMethodParameters(method, Type.OBJECT, Type.OBJECT, Type.OBJECT)) continue;
			Type[] args = t.getArgumentTypes();

			if (!args[0].getClassName().equals(worldClass.name) || !args[1].getClassName().equals(blockPosClass.name)) continue;
			potentialClasses.add(args[2].getClassName());
		}

		if (potentialClasses.size() != 1) return false;
		String className = potentialClasses.get(0);
		if (!searchConstantPoolForStrings(className, "Inventory", "Notch")) return false;

		addClassMapping("net/minecraft/entity/player/EntityPlayer", getClassNode(className));
		return true;
	}


	@Mapping(provides="net/minecraft/entity/EntityLivingBase", depends="net/minecraft/entity/player/EntityPlayer")
	public static boolean getEntityLivingBaseClass()
	{
		ClassNode entityPlayerClass = getClassNode(getClassMapping("net/minecraft/entity/player/EntityPlayer"));
		if (entityPlayerClass == null || entityPlayerClass.superName == null) return false;

		if (!searchConstantPoolForStrings(entityPlayerClass.superName, "Health", "doMobLoot", "ai")) return false;

		addClassMapping("net/minecraft/entity/EntityLivingBase", getClassNode(entityPlayerClass.superName));
		return true;
	}


	@Mapping(provides="net/minecraft/entity/Entity", depends="net/minecraft/entity/EntityLivingBase")
	public static boolean getEntityClass()
	{
		ClassNode entityLivingBase = getClassNode(getClassMapping("net/minecraft/entity/EntityLivingBase"));
		if (entityLivingBase == null || entityLivingBase.superName == null) return false;

		ClassNode entity = getClassNode(entityLivingBase.superName);
		if (!entity.superName.equals("java/lang/Object")) return false;

		addClassMapping("net/minecraft/entity/Entity", entity);
		return true;
	}


	@Mapping(provides="net/minecraft/entity/EntityList", depends="net/minecraft/entity/Entity")
	public static boolean getEntityListClass()
	{
		ClassNode entity = getClassNode(getClassMapping("net/minecraft/entity/Entity"));
		if (entity == null) return false;

		String className = null;

		// Try to find: public void travelToDimension(int)
		// NOTE: Mojang changed to public Entity travelToDimension(int, BlockPos)
		for (MethodNode method : (List<MethodNode>)entity.methods)
		{
			if (!method.desc.startsWith("(I")) continue;

			boolean foundFirst = false;
			boolean foundSecond = false;

			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (!foundFirst && !isLdcWithString(insn, "changeDimension")) continue;
				else foundFirst = true;

				if (!foundSecond && !isLdcWithString(insn, "reloading")) continue;
				else foundSecond = true;

				if (insn.getOpcode() == Opcodes.INVOKESTATIC) {
					MethodInsnNode mn = (MethodInsnNode)insn;
					if (className == null) { className = mn.owner; continue; }
					else if (!className.equals(mn.owner)) return false;
				}

				if (className != null) break;
			}
			if (className != null) break;
		}

		if (!searchConstantPoolForStrings(className, "ThrownPotion", "EnderDragon")) return false;

		addClassMapping("net/minecraft/entity/EntityList", getClassNode(className));
		return true;
	}



	// Parses net.minecraft.entity.EntityList to match entity names to
	// their associated classes.
	// Note: Doesn't handle minecarts, as those aren't registered directly with names.
	@Mapping(provides={
			"net/minecraft/entity/monster/EntityZombie",
			"net/minecraft/entity/passive/EntityVillager"
	},
	depends="net/minecraft/entity/EntityList")
	public static boolean parseEntityList()
	{
		Map<String, String> entityListClasses = new HashMap<String, String>();

		ClassNode entityList = getClassNode(getClassMapping("net/minecraft/entity/EntityList"));
		if (entityList == null) return false;

		List<MethodNode> methods = getMatchingMethods(entityList, "<clinit>", "()V");
		if (methods.size() != 1) return false;

		String entityClass = null;
		String entityName = null;

		// Create entity list
		for (AbstractInsnNode insn = methods.get(0).instructions.getFirst(); insn != null; insn = insn.getNext())
		{
			entityClass = getLdcClass(insn);
			if (entityClass == null) continue;

			insn = insn.getNext();
			if (insn == null) break;

			entityName = getLdcString(insn);
			if (entityName == null) continue;

			entityListClasses.put(entityName, entityClass);
		}

		// Get EntityZombie
		String zombieClass = entityListClasses.get("Zombie");
		if (zombieClass != null) {
			if (searchConstantPoolForStrings(zombieClass,  "zombie.spawnReinforcements", "IsBaby")) {
				addClassMapping("net/minecraft/entity/monster/EntityZombie", getClassNode(zombieClass));
			}
		}

		String villagerClass = entityListClasses.get("Villager");
		if (villagerClass != null) {
			if (searchConstantPoolForStrings(villagerClass, "Profession", "entity.Villager.")) {
				addClassMapping("net/minecraft/entity/passive/EntityVillager", getClassNode(villagerClass));
			}
		}

		return true;

	}


	@Mapping(provides={
			"net/minecraft/item/ItemSword",
			"net/minecraft/item/ItemSoup",
	"net/minecraft/item/ItemBanner"},
	providesMethods={
			"net/minecraft/item/Item registerItems ()V"
	},
	depends="net/minecraft/item/Item")
	public static boolean discoverItems()
	{
		ClassNode itemClass = getClassNodeFromMapping("net/minecraft/item/Item");
		if (itemClass == null) return false;

		// Find: public static void registerItems()
		List<MethodNode> methods = removeMethodsWithoutFlags(getMatchingMethods(itemClass, null,  "()V"), Opcodes.ACC_STATIC);
		for (Iterator<MethodNode> it = methods.iterator(); it.hasNext();) {
			if (it.next().name.contains("<")) it.remove();
		}
		if (methods.size() != 1) return false;
		MethodNode registerItemsMethod = methods.get(0);

		addMethodMapping("net/minecraft/item/Item registerItems ()V", itemClass.name + " " + registerItemsMethod.name + " " + registerItemsMethod.desc);

		Map<String, String> itemClassMap = new HashMap<String, String>();

		// Extract a list of classes from item initializations.
		for (AbstractInsnNode insn = registerItemsMethod.instructions.getFirst(); insn != null; insn = insn.getNext())
		{
			String name = getLdcString(insn);
			if (name == null || insn.getNext().getOpcode() != Opcodes.NEW) continue;
			insn = insn.getNext();
			TypeInsnNode newNode = (TypeInsnNode)insn;
			if (!newNode.desc.equals(itemClass.name)) itemClassMap.put(name, newNode.desc);
		}

		String className;

		className = itemClassMap.get("iron_sword");
		if (className != null && searchConstantPoolForStrings(className, "Weapon modifier")) {
			addClassMapping("net/minecraft/item/ItemSword", className);
		}

		className = itemClassMap.get("mushroom_stew");
		if (className != null && className.equals(itemClassMap.get("rabbit_stew"))) {
			addClassMapping("net/minecraft/item/ItemSoup", className);
		}

		className = itemClassMap.get("banner");
		if (className != null && searchConstantPoolForStrings(className, "item.banner.")) {
			addClassMapping("net/minecraft/item/ItemBanner", className);
		}


		return true;
	}


	@Mapping(providesMethods={
			"net/minecraft/item/Item setMaxDamage (I)Lnet/minecraft/item/Item;",
			"net/minecraft/item/Item getMaxDamage ()I"
	},
	providesFields={
			"net/minecraft/item/Item maxDamage I"
	},
	depends={
			"net/minecraft/item/Item",
			"net/minecraft/item/ItemBanner"
	})
	public static boolean getMaxDamageStuff()
	{
		ClassNode item = getClassNodeFromMapping("net/minecraft/item/Item");
		ClassNode banner = getClassNodeFromMapping("net/minecraft/item/ItemBanner");
		if (item == null || banner == null) return false;

		List<MethodNode> methods = getMatchingMethods(banner, "<init>", "()V");
		if (methods.size() != 1) return false;

		int count = 0;
		String setMaxDamageName = null;
		String setMaxDamageDesc = "(I)L" + item.name + ";";

		// Locate Item.setMaxDamage used in ItemBanner's constructor
		for (AbstractInsnNode insn = methods.get(0).instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
			MethodInsnNode mn = (MethodInsnNode)insn;
			if (!mn.desc.equals(setMaxDamageDesc)) continue;
			setMaxDamageName = mn.name;
			count++;
		}

		if (count != 1) return false;
		addMethodMapping("net/minecraft/item/Item setMaxDamage (I)Lnet/minecraft/item/Item;",
				item.name + " " + setMaxDamageName + " " + setMaxDamageDesc);


		String maxDamageField = null;

		methods = getMatchingMethods(item, setMaxDamageName, setMaxDamageDesc);
		if (methods.size() != 1) return false;

		// Get Item.maxDamage field from Item.setMaxDamage(I)
		for (AbstractInsnNode insn = methods.get(0).instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn.getOpcode() < 0) continue;
			if (insn.getOpcode() != Opcodes.ALOAD) return false;
			insn = insn.getNext();
			if (insn.getOpcode() != Opcodes.ILOAD) return false;
			insn = insn.getNext();
			if (insn.getOpcode() != Opcodes.PUTFIELD) return false;

			FieldInsnNode fn = (FieldInsnNode)insn;
			if (!fn.desc.equals("I")) return false;
			maxDamageField = fn.name;
			break;
		}
		if (maxDamageField == null) return false;

		addFieldMapping("net/minecraft/item/Item maxDamage I", item.name + " " + maxDamageField + " I");


		// Find Item.getMaxDamage()
		methods = getMatchingMethods(item, null, "()I");
		for (MethodNode method : methods) {
			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (insn.getOpcode() < 0) continue;
				if (insn.getOpcode() != Opcodes.ALOAD) return false;
				insn = insn.getNext();
				if (insn.getOpcode() != Opcodes.GETFIELD) return false;

				FieldInsnNode fn = (FieldInsnNode)insn;
				if (fn.name.equals(maxDamageField)) {
					addMethodMapping("net/minecraft/item/Item getMaxDamage ()I", item.name + " " + method.name + " " + method.desc);
					return true;
				}

				break;
			}
		}


		return false;
	}



	public static String assembleDescriptor(Object... objects)
	{
		String output = "";

		for (Object o : objects) {
			if (o instanceof String) output += (String)o;
			else if (o instanceof ClassNode) output += "L" + ((ClassNode)o).name + ";";
		}

		return output;
	}



	@Mapping(provides={},
			providesMethods={
			"net/minecraft/block/Block onBlockActivated (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/util/MainOrOffHand;Lnet/minecraft/item/ItemStack;Lnet/minecraft/util/EnumFacing;FFF)Z"
	},
	depends={
			"net/minecraft/block/Block",
			"net/minecraft/item/Item",
			"net/minecraft/item/ItemStack",
			"net/minecraft/world/World",
			"net/minecraft/util/BlockPos",
			"net/minecraft/block/state/IBlockState",
			"net/minecraft/entity/player/EntityPlayer",
			"net/minecraft/util/MainOrOffHand",
			"net/minecraft/util/EnumFacing"
	})
	public static boolean processBlockClass()
	{
		ClassNode block = getClassNodeFromMapping("net/minecraft/block/Block");
		ClassNode item = getClassNodeFromMapping("net/minecraft/item/Item");
		ClassNode itemStack = getClassNodeFromMapping("net/minecraft/item/ItemStack");
		ClassNode world = getClassNodeFromMapping("net/minecraft/world/World");
		ClassNode blockPos = getClassNodeFromMapping("net/minecraft/util/BlockPos");
		ClassNode blockState = getClassNodeFromMapping("net/minecraft/block/state/IBlockState");
		ClassNode entityPlayer = getClassNodeFromMapping("net/minecraft/entity/player/EntityPlayer");
		ClassNode mainOrOffHand = getClassNodeFromMapping("net/minecraft/util/MainOrOffHand");
		ClassNode enumFacing = getClassNodeFromMapping("net/minecraft/util/EnumFacing");

		if (!MeddleUtil.notNull(block, item, itemStack, blockPos, world, blockState, entityPlayer, mainOrOffHand, enumFacing)) return false;

		//private static void registerBlock(int, ResourceLocation, Block)
		for (MethodNode method : block.methods) {
			if ((method.access & Opcodes.ACC_STATIC) == 0) continue;
			//System.out.println(method);
		}


		List<MethodNode> methods;


		// public boolean onBlockActivated(World, BlockPos, IBlockState, EntityPlayer, MainOrOffHand, ItemStack, EnumFacing, float, float, float)
		String descriptor = assembleDescriptor("(", world, blockPos, blockState, entityPlayer, mainOrOffHand, itemStack, enumFacing, "FFF)Z");
		methods = getMatchingMethods(block,  null,  descriptor);
		if (methods.size() == 1) {
			MethodNode method = methods.get(0);
			addMethodMapping("net/minecraft/block/Block onBlockActivated (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/util/MainOrOffHand;Lnet/minecraft/item/ItemStack;Lnet/minecraft/util/EnumFacing;FFF)Z",
					block.name + " " + method.name + " " + method.desc);
		}

		return true;
	}



	@Mapping(providesFields={
			"net/minecraft/creativetab/CreativeTabs tabBlock Lnet/minecraft/creativetab/CreativeTabs;",
			"net/minecraft/creativetab/CreativeTabs tabDecorations Lnet/minecraft/creativetab/CreativeTabs;",
			"net/minecraft/creativetab/CreativeTabs tabRedstone Lnet/minecraft/creativetab/CreativeTabs;",
			"net/minecraft/creativetab/CreativeTabs tabTransport Lnet/minecraft/creativetab/CreativeTabs;",
			"net/minecraft/creativetab/CreativeTabs tabMisc Lnet/minecraft/creativetab/CreativeTabs;",
			"net/minecraft/creativetab/CreativeTabs tabAllSearch Lnet/minecraft/creativetab/CreativeTabs;",
			"net/minecraft/creativetab/CreativeTabs tabFood Lnet/minecraft/creativetab/CreativeTabs;",
			"net/minecraft/creativetab/CreativeTabs tabTools Lnet/minecraft/creativetab/CreativeTabs;",
			"net/minecraft/creativetab/CreativeTabs tabCombat Lnet/minecraft/creativetab/CreativeTabs;",
			"net/minecraft/creativetab/CreativeTabs tabBrewing Lnet/minecraft/creativetab/CreativeTabs;",
			"net/minecraft/creativetab/CreativeTabs tabMaterials Lnet/minecraft/creativetab/CreativeTabs;",
			"net/minecraft/creativetab/CreativeTabs tabInventory Lnet/minecraft/creativetab/CreativeTabs;"
	},
	depends="net/minecraft/creativetab/CreativeTabs")
	public static boolean getCreativeTabs()
	{
		ClassNode creativeTabs = getClassNodeFromMapping("net/minecraft/creativetab/CreativeTabs");
		if (creativeTabs == null) return false;

		List<MethodNode> methods = getMatchingMethods(creativeTabs, "<clinit>", "()V");
		if (methods.size() != 1) return false;

		Map<String, String> tabMap = new HashMap<String, String>();

		String name = null;
		for (AbstractInsnNode insn = methods.get(0).instructions.getFirst(); insn != null; insn = insn.getNext())
		{
			if (name == null) { name = getLdcString(insn); continue; }
			if (insn.getOpcode() != Opcodes.PUTSTATIC) continue;
			FieldInsnNode fn = (FieldInsnNode)insn;
			if (fn.desc.equals("L" + creativeTabs.name + ";")) {
				tabMap.put(name,  fn.name);
				name = null;
			}
		}

		Map<String, String> tabToFieldMap = new HashMap<String, String>() {{
			put("buildingBlocks", "tabBlock");
			put("decorations", "tabDecorations");
			put("redstone", "tabRedstone");
			put("transportation", "tabTransport");
			put("misc", "tabMisc");
			put("search", "tabAllSearch");
			put("food", "tabFood");
			put("tools", "tabTools");
			put("combat", "tabCombat");
			put("brewing", "tabBrewing");
			put("materials", "tabMaterials");
			put("inventory", "tabInventory");
		}};

		for (String key : tabMap.keySet()) {
			if (tabToFieldMap.containsKey(key)) {
				String mappedField = tabToFieldMap.get(key);
				String unmappedField = tabMap.get(key);
				addFieldMapping("net/minecraft/creativetab/CreativeTabs " + mappedField + " Lnet/minecraft/creativetab/CreativeTabs;",
						creativeTabs.name + " " + unmappedField + " L" + creativeTabs.name + ";");
			}
		}


		return true;
	}



	@Mapping(provides={
			"net/minecraft/util/EnumFacing",
			"net/minecraft/util/ItemUseResult",  		// NEW
			"net/minecraft/util/ObjectActionHolder", 	// NEW
			"net/minecraft/util/MainOrOffHand", 		// NEW
			"net/minecraft/creativetab/CreativeTabs",
			"net/minecraft/util/RegistryNamespaced",
			"net/minecraft/item/state/IItemState",		// NEW
			"net/minecraft/util/MovingObjectPosition",
	"net/minecraft/util/ResourceLocation"},
	providesMethods={
			"net/minecraft/item/Item getMaxStackSize ()I",
			"net/minecraft/item/Item setMaxStackSize (I)Lnet/minecraft/item/Item;",
			"net/minecraft/item/Item setCreativeTab (Lnet/minecraft/creativetab/CreativeTabs;)Lnet/minecraft/item/Item;",
			"net/minecraft/item/Item registerItem (ILjava/lang/String;Lnet/minecraft/item/Item;)V",
			"net/minecraft/item/Item onItemRightClick (Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/util/MainOrOffHand;)Lnet/minecraft/util/ObjectActionHolder;",
			"net/minecraft/item/Item onItemUse (Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/MainOrOffHand;Lnet/minecraft/util/EnumFacing;FFF)Lnet/minecraft/util/ItemUseResult;"
	},
	providesFields={
			"net/minecraft/item/Item maxStackSize I"
	},
	depends={
			"net/minecraft/item/Item",
			"net/minecraft/item/ItemStack",
			"net/minecraft/world/World",
			"net/minecraft/util/BlockPos",
			"net/minecraft/entity/player/EntityPlayer",
	"net/minecraft/entity/EntityLivingBase"})
	public static boolean processItemClass()
	{
		ClassNode item = getClassNodeFromMapping("net/minecraft/item/Item");
		ClassNode itemStack = getClassNodeFromMapping("net/minecraft/item/ItemStack");
		ClassNode world = getClassNodeFromMapping("net/minecraft/world/World");
		ClassNode blockPos = getClassNodeFromMapping("net/minecraft/util/BlockPos");
		ClassNode entityPlayer = getClassNodeFromMapping("net/minecraft/entity/player/EntityPlayer");
		ClassNode entityLivingBase = getClassNodeFromMapping("net/minecraft/entity/EntityLivingBase");

		String mainOrOffHand = null;
		String enumFacing = null;
		String itemUseResult = null;

		ClassNode objectActionHolder = null;
		String itemState = null;
		String resourceLocation = null;

		//public ItemUseResult onItemUse(ItemStack, EntityPlayer, World, BlockPos, MainOrOffHand, EnumFacing, float, float, float)
		for (MethodNode method : item.methods) {
			if (!checkMethodParameters(method, Type.OBJECT, Type.OBJECT, Type.OBJECT, Type.OBJECT, Type.OBJECT, Type.OBJECT, Type.FLOAT, Type.FLOAT, Type.FLOAT)) continue;
			String test = "(L" + itemStack.name + ";L" + entityPlayer.name + ";L" + world.name + ";L" + blockPos.name + ";L";
			if (!method.desc.startsWith(test)) continue;

			Type t = Type.getMethodType(method.desc);
			Type[] args = t.getArgumentTypes();

			addClassMapping("net/minecraft/util/ItemUseResult", itemUseResult = t.getReturnType().getClassName());
			addClassMapping("net/minecraft/util/MainOrOffHand", mainOrOffHand = args[4].getClassName());
			addClassMapping("net/minecraft/util/EnumFacing", enumFacing = args[5].getClassName());
			addMethodMapping("net/minecraft/item/Item onItemUse (Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/MainOrOffHand;Lnet/minecraft/util/EnumFacing;FFF)Lnet/minecraft/util/ItemUseResult;",
					item.name + " " + method.name + " " + method.desc);
		}

		// public ObjectActionHolderThing onItemRightClick(ItemStack, World, EntityPlayer, MainOrOffHand)
		for (MethodNode method : item.methods) {
			String test = "(L" + itemStack.name + ";L" + world.name + ";L" + entityPlayer.name + ";L" + mainOrOffHand + ";)";
			if (!method.desc.startsWith(test)) continue;
			Type t = Type.getMethodType(method.desc).getReturnType();

			objectActionHolder = getClassNode(t.getClassName());
			addClassMapping("net/minecraft/util/ObjectActionHolder", objectActionHolder);
			addMethodMapping("net/minecraft/item/Item onItemRightClick (Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/util/MainOrOffHand;)Lnet/minecraft/util/ObjectActionHolder;",
					item.name + " " + method.name + " " + method.desc);
		}

		// private static void registerItem(int, ResourceLocation, Item)
		for (MethodNode method : item.methods) {
			if ((method.access & Opcodes.ACC_STATIC) == 0) continue;
			if (!checkMethodParameters(method, Type.INT, Type.OBJECT, Type.OBJECT)) continue;
			Type t = Type.getMethodType(method.desc);
			Type[] args = t.getArgumentTypes();
			if (!args[2].getClassName().equals(item.name)) continue;
			String className = args[1].getClassName();
			if (className.equals("java.lang.String")) continue;
			if (searchConstantPoolForStrings(className, "minecraft")) {
				resourceLocation = className;
				addClassMapping("net/minecraft/util/ResourceLocation", className);
			}
		}


		for (FieldNode field : item.fields) {
			if ((field.access & (Opcodes.ACC_FINAL | Opcodes.ACC_STATIC)) != (Opcodes.ACC_FINAL + Opcodes.ACC_STATIC)) continue;
			String className = Type.getType(field.desc).getClassName();
			if (className.contains(".")) continue;

			// Item.itemRegistry
			if (searchConstantPoolForClasses(className, "com.google.common.collect.BiMap", "com.google.common.collect.HashBiMap")) {
				addClassMapping("net/minecraft/util/RegistryNamespaced", className);
				continue;
			}
		}


		// public final void addItemState(ResourceLocation, IItemState)
		for (MethodNode method : item.methods) {
			Type t = Type.getMethodType(method.desc);
			Type[] args = t.getArgumentTypes();
			if (args.length != 2) continue;
			if (!args[0].getClassName().equals(resourceLocation)) continue;

			String className = args[1].getClassName();
			ClassNode cn = getClassNode(className);
			if (cn == null) continue;
			if ((cn.access & Opcodes.ACC_INTERFACE) == 0) continue;

			addClassMapping("net/minecraft/item/state/IItemState", className);
			break;
		}


		//protected MovingObjectPosition getMovingObjectPositionFromPlayer(World, EntityPlayer, boolean)
		for (MethodNode method : item.methods) {
			if (!method.desc.startsWith("(L" + world.name + ";L" + entityPlayer.name + ";Z)")) continue;
			Type t = Type.getMethodType(method.desc).getReturnType();
			if (t.getSort() != Type.OBJECT) continue;
			if (searchConstantPoolForStrings(t.getClassName(), "HitResult{type=")) {
				addClassMapping("net/minecraft/util/MovingObjectPosition", t.getClassName());
				break;
			}
		}



		ClassNode creativeTab = null;

		// Get net.minecraft.creativetab.CreativeTabs
		for (FieldNode field : item.fields) {
			Type t = Type.getType(field.desc);
			if (t.getSort() != Type.OBJECT) continue;
			String className = t.getClassName();
			if (className.contains(".")) continue;
			if (reverseClassMappings.containsKey(className)) continue;
			if (searchConstantPoolForStrings(className, "buildingBlocks", "decorations", "redstone")) {
				addClassMapping("net/minecraft/creativetab/CreativeTabs", className);
				creativeTab = getClassNode(className);
			}
		}


		// Get Item.setCreativeTab()
		if (creativeTab != null)
		{
			String setCreativeTabDesc = "(L" + creativeTab.name + ";)L" + item.name + ";";
			List<MethodNode> methods = getMatchingMethods(item, null, setCreativeTabDesc);
			if (methods.size() == 1) {
				addMethodMapping("net/minecraft/item/Item setCreativeTab (Lnet/minecraft/creativetab/CreativeTabs;)Lnet/minecraft/item/Item;",
						item.name + " " + methods.get(0).name + " " + methods.get(0).desc);
			}
		}


		String maxStackSizeField = null;

		// Get maxStackSize field
		List<MethodNode> initMethod = getMatchingMethods(item, "<init>", "()V");
		if (initMethod.size() == 1) {
			for (AbstractInsnNode insn = initMethod.get(0).instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (!(insn instanceof IntInsnNode)) continue;
				IntInsnNode bipush = (IntInsnNode)insn;
				if (bipush.operand != 64) continue;
				if (insn.getNext().getOpcode() != Opcodes.PUTFIELD) continue;
				FieldInsnNode fn = (FieldInsnNode)insn.getNext();
				if (!fn.desc.equals("I")) continue;
				maxStackSizeField = fn.name;
				addFieldMapping("net/minecraft/item/Item maxStackSize I", item.name + " " + maxStackSizeField + " I");
				break;
			}
		}


		// get getMaxStackSize() and setMaxStackSize(I) methods
		if (maxStackSizeField != null)
		{
			boolean foundGetter = false;

			List<MethodNode> intGetters = getMatchingMethods(item, null, "()I");
			for (MethodNode method : intGetters) {
				for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
					if (insn.getOpcode() < 0) continue;
					// First real instruction should be ALOAD_0
					if (insn.getOpcode() != Opcodes.ALOAD) break;
					insn = insn.getNext();
					// Next should be GETFIELD
					if (insn.getOpcode() != Opcodes.GETFIELD) break;

					FieldInsnNode fn = (FieldInsnNode)insn;
					if (fn.name.equals(maxStackSizeField)) {
						addMethodMapping("net/minecraft/item/Item getMaxStackSize ()I", item.name + " " + method.name + " " + method.desc);
						foundGetter = true;
						break;
					}
				}
				if (foundGetter) break;
			}

			boolean foundSetter = false;

			List<MethodNode> intSetters = getMatchingMethods(item, null, "(I)L" + item.name + ";");
			for (MethodNode method : intSetters) {
				for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
					if (insn.getOpcode() < 0) continue;
					if (insn.getOpcode() != Opcodes.ALOAD) break;
					insn = insn.getNext();
					if (insn.getOpcode() != Opcodes.ILOAD) break;
					insn = insn.getNext();
					if (insn.getOpcode() != Opcodes.PUTFIELD) break;

					FieldInsnNode fn = (FieldInsnNode)insn;
					if (fn.name.equals(maxStackSizeField)) {
						addMethodMapping("net/minecraft/item/Item setMaxStackSize (I)Lnet/minecraft/item/Item;", item.name + " " + method.name + " " + method.desc);
						foundSetter = true;
						break;
					}
				}
				if (foundSetter) break;
			}
		}


		// private static void registerItem(int id, String textualID, Item itemIn)
		List<MethodNode> methods = getMatchingMethods(item, null, "(ILjava/lang/String;L" + item.name + ";)V");
		methods = removeMethodsWithoutFlags(methods, Opcodes.ACC_STATIC);
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/item/Item registerItem (ILjava/lang/String;Lnet/minecraft/item/Item;)V",
					item.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}



		return true;
	}


	@Mapping(providesFields={
			"net/minecraft/world/World isRemote Z"
	},
	depends={
			"net/minecraft/world/World"
	})
	public static boolean processWorldClass()
	{
		ClassNode world = getClassNodeFromMapping("net/minecraft/world/World");
		if (world == null) return false;

		int count = 0;

		String isRemote = null;
		for (FieldNode fn : world.fields) {
			if ((fn.access & Opcodes.ACC_FINAL) == 0 || !fn.desc.equals("Z")) continue;
			isRemote = fn.name;
			count++;
		}
		if (count == 1) addFieldMapping("net/minecraft/world/World isRemote Z", world.name + " " + isRemote + " Z");
		else isRemote = null;


		return true;
	}


	public static MethodNode getMethodNodeFromMapping(ClassNode cn, String deobfMapping)
	{
		String mapping = getMethodMapping(deobfMapping);
		if (cn == null || mapping == null) return null;

		String[] split = mapping.split(" ");
		if (split.length < 3) return null;

		for (MethodNode method : cn.methods) {
			if (method.name.equals(split[1]) && method.desc.equals(split[2])) {
				return method;
			}
		}

		return null;
	}



	@Mapping(provides={
			"net/minecraft/inventory/IInventory"
	},
	providesMethods={
			"net/minecraft/entity/player/EntityPlayer displayGUIChest (Lnet/minecraft/inventory/IInventory;)V"
	},
	dependsMethods={
			"net/minecraft/block/Block onBlockActivated (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/util/MainOrOffHand;Lnet/minecraft/item/ItemStack;Lnet/minecraft/util/EnumFacing;FFF)Z"
	},
	depends={
			"net/minecraft/block/BlockChest",
			"net/minecraft/entity/player/EntityPlayer"
	})
	public static boolean processBlockChestClass()
	{
		ClassNode blockChest = getClassNodeFromMapping("net/minecraft/block/BlockChest");
		ClassNode entityPlayer = getClassNodeFromMapping("net/minecraft/entity/player/EntityPlayer");
		if (blockChest == null || entityPlayer == null) return false;

		MethodNode onBlockActivated = getMethodNodeFromMapping(blockChest, "net/minecraft/block/Block onBlockActivated (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/util/MainOrOffHand;Lnet/minecraft/item/ItemStack;Lnet/minecraft/util/EnumFacing;FFF)Z");
		if (onBlockActivated == null) return false;

		for (AbstractInsnNode insn = onBlockActivated.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (!(insn instanceof MethodInsnNode)) continue;
			MethodInsnNode mn = (MethodInsnNode)insn;
			if (!mn.owner.equals(entityPlayer.name)) continue;
			Type t = Type.getMethodType(mn.desc);
			Type[] args = t.getArgumentTypes();
			if (args.length < 1 || args[0].getSort() != Type.OBJECT) continue;

			String className = args[0].getClassName();
			ClassNode cn = getClassNode(className);
			if ((cn.access & Opcodes.ACC_INTERFACE) == 0) continue;

			addClassMapping("net/minecraft/inventory/IInventory", className);
			addMethodMapping("net/minecraft/entity/player/EntityPlayer displayGUIChest (Lnet/minecraft/inventory/IInventory;)V",
					entityPlayer.name + " " + mn.name + " " + mn.desc);
			break;
		}


		return true;
	}

	@Mapping(provides={
			"net/minecraft/world/IWorldNameable",
			"net/minecraft/util/IChatComponent"
	},
	providesMethods={
			"net/minecraft/world/IWorldNameable hasCustomName ()Z",
			"net/minecraft/world/IWorldNameable getName ()Ljava/lang/String;",
			"net/minecraft/world/IWorldNameable getDisplayName ()Lnet/minecraft/util/IChatComponent;"
	},
	depends={
			"net/minecraft/inventory/IInventory"
	})
	public static boolean processIWorldNameable()
	{
		ClassNode inventory = getClassNodeFromMapping("net/minecraft/inventory/IInventory");
		if (inventory == null) return false;

		if (inventory.interfaces.size() != 1) return false;
		String className = inventory.interfaces.get(0);

		addClassMapping("net/minecraft/world/IWorldNameable", className);

		ClassNode worldNameable = getClassNode(className);
		if (worldNameable == null) return false;

		if (worldNameable.methods.size() != 3) return false;
		for (MethodNode method : worldNameable.methods) {
			Type t = Type.getMethodType(method.desc);
			Type returnType = t.getReturnType();

			if (returnType.getSort() == Type.BOOLEAN) {
				addMethodMapping("net/minecraft/world/IWorldNameable hasCustomName ()Z", worldNameable.name + " " + method.name + " ()Z");
				continue;
			}

			if (returnType.getSort() == Type.OBJECT) {
				if (returnType.getClassName().equals("java.lang.String")) {
					addMethodMapping("net/minecraft/world/IWorldNameable getName ()Ljava/lang/String;", worldNameable.name + " " + method.name + " " + method.desc);
					continue;
				}

				addClassMapping("net/minecraft/util/IChatComponent", returnType.getClassName());
				addMethodMapping("net/minecraft/world/IWorldNameable getDisplayName ()Lnet/minecraft/util/IChatComponent;", worldNameable.name + " " + method.name + " " + method.desc);
			}
		}


		return true;
	}


	@Mapping(provides="net/minecraft/util/ChatComponentText", depends="net/minecraft/server/MinecraftServer")
	public static boolean getChatComponentTextClass()
	{
		ClassNode server = getClassNode("net/minecraft/server/MinecraftServer");
		if (server == null) return false;

		List<MethodNode> methods = getMatchingMethods(server, "run", "()V");
		if (methods.size() != 1) return false;

		for (AbstractInsnNode insn = methods.get(0).instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn.getOpcode() != Opcodes.NEW) continue;
			TypeInsnNode tn = (TypeInsnNode)insn;
			if (searchConstantPoolForStrings(tn.desc, "TextComponent{text=\'")) {
				addClassMapping("net/minecraft/util/ChatComponentText", tn.desc);
				return true;
			}
		}

		return false;
	}


	@Mapping(provides={
			"net/minecraft/inventory/InventoryBasic"
	},
	depends={
			"net/minecraft/entity/passive/EntityVillager",
			"net/minecraft/inventory/IInventory"
	})
	public static boolean processEntityVillagerClass()
	{
		ClassNode villager = getClassNodeFromMapping("net/minecraft/entity/passive/EntityVillager");
		ClassNode inventory = getClassNodeFromMapping("net/minecraft/inventory/IInventory");
		if (villager == null || inventory == null) return false;

		for (MethodNode method : villager.methods) {
			if (!method.name.equals("<init>")) continue;

			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (insn.getOpcode() != Opcodes.NEW) continue;
				TypeInsnNode tn = (TypeInsnNode)insn;

				ClassNode node = getClassNode(tn.desc);
				if (node == null) continue;

				boolean isInventory = false;
				for (String iface : node.interfaces) {
					if (inventory.name.equals(iface)) isInventory = true;
				}

				if (!isInventory) continue;

				// TODO - Find more accurate way of detecting this class
				addClassMapping("net/minecraft/inventory/InventoryBasic", node.name);
				return true;
			}

		}


		return false;
	}



	@Mapping(provides={
	},
	depends={
			"net/minecraft/entity/player/EntityPlayer"
	})
	public static boolean processEntityPlayerClass()
	{
		ClassNode entityPlayer = getClassNodeFromMapping("net/minecraft/entity/player/EntityPlayer");
		if (entityPlayer == null) return false;

		for (MethodNode method : entityPlayer.methods) {
			Type t = Type.getMethodType(method.desc);
			Type[] args = t.getArgumentTypes();
			if (args.length != 1 || args[0].getSort() != Type.OBJECT || t.getReturnType().getSort() != Type.VOID) continue;
			//System.out.println(method.name + " " + method.desc);
		}

		// TODO - Get displayChestGui


		return true;
	}



	public static AbstractInsnNode getNextRealOpcode(AbstractInsnNode insn)
	{
		while (insn != null && insn.getOpcode() < 0) insn = insn.getNext();
		return insn;
	}



	@Mapping(provides={
			"net/minecraft/nbt/NBTTagCompound",
			"net/minecraft/nbt/NBTBase",
			"net/minecraft/nbt/NBTTagEnd",
			"net/minecraft/nbt/NBTTagByte",
			"net/minecraft/nbt/NBTTagShort",
			"net/minecraft/nbt/NBTTagInt",
			"net/minecraft/nbt/NBTTagLong",
			"net/minecraft/nbt/NBTTagFloat",
			"net/minecraft/nbt/NBTTagDouble",
			"net/minecraft/nbt/NBTTagByteArray",
			"net/minecraft/nbt/NBTTagString",
			"net/minecraft/nbt/NBTTagList",
			"net/minecraft/nbt/NBTTagIntArray"
	},
	providesMethods={
			"net/minecraft/item/ItemStack writeToNBT (Lnet/minecraft/nbt/NBTTagCompound;)Lnet/minecraft/nbt/NBTTagCompound;",
			"net/minecraft/nbt/NBTBase createNewByType (B)Lnet/minecraft/nbt/NBTBase;"
	},
	depends={
			"net/minecraft/item/ItemStack"
	})
	public static boolean getNBTClasses()
	{
		ClassNode itemStack = getClassNodeFromMapping("net/minecraft/item/ItemStack");
		if (itemStack == null) return false;

		String tagCompoundName = null;

		// Find: public NBTTagCompound writeToNBT(NBTTagCompound)
		for (MethodNode method : itemStack.methods) {
			Type t = Type.getMethodType(method.desc);
			Type[] args = t.getArgumentTypes();
			Type returnType = t.getReturnType();

			if (args.length != 1) continue;
			if (args[0].getSort() != Type.OBJECT || returnType.getSort() != Type.OBJECT) continue;
			if (!args[0].getClassName().equals(returnType.getClassName())) continue;

			tagCompoundName = returnType.getClassName();

			if (searchConstantPoolForStrings(tagCompoundName, "Tried to read NBT tag with too high complexity, depth > 512")) {
				addClassMapping("net/minecraft/nbt/NBTTagCompound", tagCompoundName);
				addMethodMapping("net/minecraft/item/ItemStack writeToNBT (Lnet/minecraft/nbt/NBTTagCompound;)Lnet/minecraft/nbt/NBTTagCompound;",
						itemStack.name + " " + method.name + " " + method.desc);
				break;
			}
			tagCompoundName = null;
		}
		if (tagCompoundName == null) return false;

		ClassNode tagCompound = getClassNode(tagCompoundName);
		if (tagCompound == null) return false;
		ClassNode tagBase = getClassNode(tagCompound.superName);
		if (tagBase == null) return false;

		if (!searchConstantPoolForStrings(tagBase.name, "END", "BYTE", "SHORT")) return false;

		addClassMapping("net/minecraft/nbt/NBTBase", tagBase.name);

		Map<Integer, String> nbtClassesMap = new HashMap<Integer, String>();

		// We want: protected static NBTBase createNewByType(byte id)
		for (MethodNode method : tagBase.methods) {
			Type t = Type.getMethodType(method.desc);
			Type[] args = t.getArgumentTypes();
			Type returnType = t.getReturnType();

			if (args.length != 1) continue;
			if (args[0].getSort() != Type.BYTE) continue;
			if (!returnType.getClassName().equals(tagBase.name)) continue;

			addMethodMapping("net/minecraft/nbt/NBTBase createNewByType (B)Lnet/minecraft/nbt/NBTBase;", tagBase.name + " " + method.name + " " + method.desc);

			// Find the tableswitch to parse
			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (insn.getOpcode() != Opcodes.TABLESWITCH) continue;
				TableSwitchInsnNode table = (TableSwitchInsnNode)insn;

				// There's 12 NBT types, otherwise this table isn't reliable
				if (table.labels.size() != 12) continue;

				for (int n = 0; n < 12; n++) {
					AbstractInsnNode destInsn = table.labels.get(n);
					AbstractInsnNode nextReal = getNextRealOpcode(destInsn);

					// Every jump target should be creating a new instance of an NBT type
					if (nextReal.getOpcode() != Opcodes.NEW) return false;
					nbtClassesMap.put(n, ((TypeInsnNode)nextReal).desc);
				}
			}
		}

		for (Integer key : nbtClassesMap.keySet()) {
			String className = nbtClassesMap.get(key);
			if (className == null) continue;

			switch (key) {
			case 0: addClassMapping("net/minecraft/nbt/NBTTagEnd", className);
			continue;
			case 1: addClassMapping("net/minecraft/nbt/NBTTagByte", className);
			continue;
			case 2: addClassMapping("net/minecraft/nbt/NBTTagShort", className);
			continue;
			case 3: addClassMapping("net/minecraft/nbt/NBTTagInt", className);
			continue;
			case 4: addClassMapping("net/minecraft/nbt/NBTTagLong", className);
			continue;
			case 5: addClassMapping("net/minecraft/nbt/NBTTagFloat", className);
			continue;
			case 6: addClassMapping("net/minecraft/nbt/NBTTagDouble", className);
			continue;
			case 7: addClassMapping("net/minecraft/nbt/NBTTagByteArray", className);
			continue;
			case 8: addClassMapping("net/minecraft/nbt/NBTTagString", className);
			continue;
			case 9: addClassMapping("net/minecraft/nbt/NBTTagList", className);
			continue;
			case 10: // Already added this earlier
				continue;
			case 11: addClassMapping("net/minecraft/nbt/NBTTagIntArray", className);
			continue;
			}
		}



		return true;
	}


	@Mapping(provides={
			"net/minecraft/nbt/NBTBase$NBTPrimitive"
	},
	providesMethods={
			"net/minecraft/nbt/NBTTagCompound getTagList (Ljava/lang/String;I)Lnet/minecraft/nbt/NBTTagList;",
			"net/minecraft/nbt/NBTBase hashCode ()I",
			"net/minecraft/nbt/NBTTagCompound tagCount ()I",
			"net/minecraft/nbt/NBTTagList tagCount ()I",
			"net/minecraft/nbt/NBTTagList getTagType ()I",
			"net/minecraft/nbt/NBTTagList getCompoundTagAt (I)Lnet/minecraft/nbt/NBTTagCompound;",
			"net/minecraft/nbt/NBTTagCompound getByte (Ljava/lang/String;)B",
			"net/minecraft/nbt/NBTTagCompound setByte (Ljava/lang/String;B)V",
			"net/minecraft/nbt/NBTTagCompound hasKey (Ljava/lang/String;I)Z",
			"net/minecraft/nbt/NBTTagCompound getTagType (Ljava/lang/String;)B",
			"net/minecraft/nbt/NBTTagList appendTag (Lnet/minecraft/nbt/NBTBase;)V",
			"net/minecraft/nbt/NBTTagCompound setTag (Ljava/lang/String;Lnet/minecraft/nbt/NBTBase;)V"
	},
	providesFields={
			"net/minecraft/nbt/NBTTagList tagType B"
	},
	depends={
			"net/minecraft/nbt/NBTTagCompound",
			"net/minecraft/nbt/NBTTagList",
			"net/minecraft/nbt/NBTBase"
	})
	public static boolean processNBTTagCompound()
	{
		ClassNode tagCompound = getClassNodeFromMapping("net/minecraft/nbt/NBTTagCompound");
		ClassNode tagList = getClassNodeFromMapping("net/minecraft/nbt/NBTTagList");
		ClassNode tagBase = getClassNodeFromMapping("net/minecraft/nbt/NBTBase");
		if (tagCompound == null || tagList == null || tagBase == null) return false;

		// public NBTTagList getTagList(String, int)
		List<MethodNode> methods = getMatchingMethods(tagCompound, null, "(Ljava/lang/String;I)L" + tagList.name + ";");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/nbt/NBTTagCompound getTagList (Ljava/lang/String;I)Lnet/minecraft/nbt/NBTTagList;",
					tagCompound.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}

		// public int hashCode()
		methods = getMatchingMethods(tagBase, null, "()I");
		if (methods.size() != 1) return false;
		String getHashName = methods.get(0).name;
		addMethodMapping("net/minecraft/nbt/NBTBase hashCode ()I", tagBase.name + " " + getHashName + " ()I");


		// public NBTTagCompound.tagCount()I
		methods = getMatchingMethods(tagCompound, null, "()I");
		for (Iterator<MethodNode> iterator = methods.iterator(); iterator.hasNext();) {
			if (iterator.next().name.equals(getHashName)) iterator.remove();
		}
		if (methods.size() != 1) return false;
		String tagCountName_compound = methods.get(0).name;
		addMethodMapping("net/minecraft/nbt/NBTTagCompound tagCount ()I", tagCompound.name + " " + tagCountName_compound + " ()I");


		// public NBTTagList.getTagType()I
		methods = getMatchingMethods(tagList, null, "()I");
		for (Iterator<MethodNode> iterator = methods.iterator(); iterator.hasNext();) {
			MethodNode method = iterator.next();
			if (method.name.equals(getHashName)) { iterator.remove(); continue; }

			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (insn.getOpcode() != Opcodes.GETFIELD) continue;
				FieldInsnNode fn = (FieldInsnNode)insn;
				if (fn.owner.equals(tagList.name) && fn.desc.equals("B")) {
					addFieldMapping("net/minecraft/nbt/NBTTagList tagType B", tagList.name + " " + fn.name + " B");
					addMethodMapping("net/minecraft/nbt/NBTTagList getTagType ()I", tagList.name + " " + method.name + " ()I");
					iterator.remove();
					break;
				}
			}
		}

		// public NBTTagList.tagCount()I
		if (methods.size() != 1) return false;
		String tagCountName_list = methods.get(0).name;
		addMethodMapping("net/minecraft/nbt/NBTTagList tagCount ()I", tagList.name + " " + tagCountName_list + " ()I");


		// public NBTTagList.getCompoundTagAt(I)Lnet/minecraft/nbt/NBTTagCompound;
		methods = getMatchingMethods(tagList,  null,  "(I)L" + tagCompound.name + ";");
		if (methods.size() != 1) return false;
		addMethodMapping("net/minecraft/nbt/NBTTagList getCompoundTagAt (I)Lnet/minecraft/nbt/NBTTagCompound;",
				tagList.name + " " + methods.get(0).name + " " + methods.get(0).desc);


		// public boolean hasKey(String key, int type)
		methods = getMatchingMethods(tagCompound, null, "(Ljava/lang/String;I)Z");
		if (methods.size() != 1) return false;
		String hasKeyName = methods.get(0).name;
		addMethodMapping("net/minecraft/nbt/NBTTagCompound hasKey (Ljava/lang/String;I)Z",
				tagCompound.name + " " + hasKeyName + " (Ljava/lang/String;I)Z");




		methods = getMatchingMethods(tagCompound, null, "(Ljava/lang/String;)B");
		if (methods.size() != 2) return false;
		for (MethodNode method : methods) {
			boolean hasBase = false;
			boolean hasPrimitive = false;
			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (insn.getOpcode() != Opcodes.CHECKCAST) continue;
				TypeInsnNode tn = (TypeInsnNode)insn;
				if (tn.desc.equals(tagBase.name)) hasBase = true;
				else if (tn.desc.startsWith(tagBase.name + "$")) {
					hasPrimitive = true;
					addClassMapping("net/minecraft/nbt/NBTBase$NBTPrimitive", tn.desc);
				}
			}

			// public byte getByte(String key)
			if (hasPrimitive && !hasBase) {
				addMethodMapping("net/minecraft/nbt/NBTTagCompound getByte (Ljava/lang/String;)B",
						tagCompound.name + " " + method.name + " " + method.desc);
			}
			// public byte getTagType(String key)
			else if (!hasPrimitive && hasBase) {
				addMethodMapping("net/minecraft/nbt/NBTTagCompound getTagType (Ljava/lang/String;)B",
						tagCompound.name + " " + method.name + " " + method.desc);
			}
		}


		// public void setByte(String key, byte value)
		methods = getMatchingMethods(tagCompound, null, "(Ljava/lang/String;B)V");
		if (methods.size() != 1) return false;
		addMethodMapping("net/minecraft/nbt/NBTTagCompound setByte (Ljava/lang/String;B)V",
				tagCompound.name + " " + methods.get(0).name + " (Ljava/lang/String;B)V");


		// public void appendTag(NBTBase nbt)
		methods = getMatchingMethods(tagList, null, "(L" + tagBase.name + ";)V");
		if (methods.size() != 1) return false;
		addMethodMapping("net/minecraft/nbt/NBTTagList appendTag (Lnet/minecraft/nbt/NBTBase;)V",
				tagList.name + " " + methods.get(0).name + " " + methods.get(0).desc);


		// public void setTag(String key, NBTBase value)
		methods = getMatchingMethods(tagCompound, null, "(Ljava/lang/String;L" + tagBase.name + ";)V");
		if (methods.size() != 1) return false;
		addMethodMapping("net/minecraft/nbt/NBTTagCompound setTag (Ljava/lang/String;Lnet/minecraft/nbt/NBTBase;)V",
				tagCompound.name + " " + methods.get(0).name + " " + methods.get(0).desc);


		return true;
	}


	@Mapping(providesFields={
			"net/minecraft/inventory/InventoryBasic slotsCount I",
			"net/minecraft/inventory/InventoryBasic inventoryTitle Ljava/lang/String;",
			"net/minecraft/inventory/InventoryBasic hasCustomName Z",
			"net/minecraft/inventory/InventoryBasic inventoryContent [Lnet/minecraft/item/ItemStack;"
	},
	providesMethods={
			"net/minecraft/inventory/IInventory getSizeInventory ()I",
			"net/minecraft/inventory/IInventory getInventoryStackLimit ()I",
			"net/minecraft/inventory/IInventory getFieldCount ()I",
			"net/minecraft/inventory/IInventory setInventorySlotContents (ILnet/minecraft/item/ItemStack;)V",
			"net/minecraft/inventory/IInventory getStackInSlot (I)Lnet/minecraft/item/ItemStack;",
			"net/minecraft/inventory/IInventory getStackInSlotOnClosing (I)Lnet/minecraft/item/ItemStack;",
			"net/minecraft/inventory/IInventory markDirty ()V",
			"net/minecraft/inventory/IInventory clear ()V"
	},
	depends={
			"net/minecraft/inventory/IInventory",
			"net/minecraft/inventory/InventoryBasic",
			"net/minecraft/item/ItemStack"
	})
	public static boolean processInventoryBasicClass()
	{
		ClassNode iInventory = getClassNodeFromMapping("net/minecraft/inventory/IInventory");
		ClassNode inventoryBasic = getClassNodeFromMapping("net/minecraft/inventory/InventoryBasic");
		ClassNode itemStack = getClassNodeFromMapping("net/minecraft/item/ItemStack");
		if (iInventory == null || inventoryBasic == null || itemStack == null) return false;

		String slotsCountField = null;
		String inventoryTitleField = null;
		String inventoryContentsField = null;
		String hasCustomNameField = null;

		List<FieldNode> fields = getMatchingFields(inventoryBasic, null, "I");
		if (fields.size() == 1) {
			slotsCountField = fields.get(0).name;
			addFieldMapping("net/minecraft/inventory/InventoryBasic slotsCount I",
					inventoryBasic.name + " " + slotsCountField + " I");
		}

		fields = getMatchingFields(inventoryBasic, null, "Ljava/lang/String;");
		if (fields.size() == 1) {
			inventoryTitleField = fields.get(0).name;
			addFieldMapping("net/minecraft/inventory/InventoryBasic inventoryTitle Ljava/lang/String;",
					inventoryBasic.name + " " + inventoryTitleField + " Ljava/lang/String;");
		}

		fields = getMatchingFields(inventoryBasic, null, "Z");
		if (fields.size() == 1) {
			hasCustomNameField = fields.get(0).name;
			addFieldMapping("net/minecraft/inventory/InventoryBasic hasCustomName Z",
					inventoryBasic.name + " " + hasCustomNameField + " Z");
		}

		fields = getMatchingFields(inventoryBasic, null, "[L" + itemStack.name + ";");
		if (fields.size() == 1) {
			inventoryContentsField = fields.get(0).name;
			addFieldMapping("net/minecraft/inventory/InventoryBasic inventoryContent [Lnet/minecraft/item/ItemStack;",
					inventoryBasic.name + " " + inventoryContentsField + " [L" + itemStack.name + ";");
		}


		// Parse methods that only return an int.
		// There should only be three.
		List<MethodNode> methods = getMatchingMethods(inventoryBasic, null, "()I");
		if (methods.size() != 3) return false;

		for (MethodNode method : methods)
		{
			AbstractInsnNode firstReal = getNextRealOpcode(method.instructions.getFirst());

			// Find IInventory.getSizeInventory()I
			if (firstReal.getOpcode() == Opcodes.ALOAD) {
				AbstractInsnNode nextNode = firstReal.getNext();
				if (nextNode != null && nextNode.getOpcode() == Opcodes.GETFIELD) {
					FieldInsnNode fn = (FieldInsnNode)nextNode;
					if (fn.owner.equals(inventoryBasic.name) && fn.name.equals(slotsCountField)) {
						addMethodMapping("net/minecraft/inventory/IInventory getSizeInventory ()I",
								iInventory.name + " " + method.name + " " + method.desc);
						continue;
					}
				}
			}

			// Find IInventory.getInventoryStackLimit()I
			if (firstReal instanceof IntInsnNode) {
				IntInsnNode in = (IntInsnNode)firstReal;
				if (in.operand == 64) {
					addMethodMapping("net/minecraft/inventory/IInventory getInventoryStackLimit ()I",
							iInventory.name + " " + method.name + " " + method.desc);
					continue;
				}
			}

			// Find IInventory.getFieldCount()I
			if (firstReal.getOpcode() == Opcodes.ICONST_0) {
				AbstractInsnNode nextNode = firstReal.getNext();
				if (nextNode != null && nextNode.getOpcode() == Opcodes.IRETURN) {
					addMethodMapping("net/minecraft/inventory/IInventory getFieldCount ()I",
							iInventory.name + " " + method.name + " " + method.desc);
					continue;
				}
			}
		}

		// public void setInventorySlotContents(int index, ItemStack stack)
		methods = getMatchingMethods(inventoryBasic, null, "(IL" + itemStack.name + ";)V");
		if (methods.size() != 1) return false;
		addMethodMapping("net/minecraft/inventory/IInventory setInventorySlotContents (ILnet/minecraft/item/ItemStack;)V",
				iInventory.name + " " + methods.get(0).name + " " + methods.get(0).desc);


		// Get the two methods with decriptor (I)Lnet/minecraft/item/ItemStack;
		methods = getMatchingMethods(inventoryBasic, null, "(I)L" + itemStack.name + ";");
		if (methods.size() != 2) return false;
		for (MethodNode method : methods)
		{
			boolean foundStore = false;
			boolean foundLength = false;
			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (insn.getOpcode() == Opcodes.AASTORE) foundStore = true;
				else if (insn.getOpcode() == Opcodes.ARRAYLENGTH) foundLength = true;
			}

			// public ItemStack getStackInSlot(int)
			if (foundLength && !foundStore) {
				addMethodMapping("net/minecraft/inventory/IInventory getStackInSlot (I)Lnet/minecraft/item/ItemStack;",
						iInventory.name + " " + method.name + " " + method.desc);
			}
			// ItemStack getStackInSlotOnClosing(int)
			else if (foundStore && !foundLength) {
				addMethodMapping("net/minecraft/inventory/IInventory getStackInSlotOnClosing (I)Lnet/minecraft/item/ItemStack;",
						iInventory.name + " " + method.name + " " + method.desc);
			}
		}


		methods = getMatchingMethods(inventoryBasic, null, "()V");
		if (methods.size() != 2) return false;
		for (MethodNode method : methods) {
			boolean hasList = false;
			boolean hasArray = false;
			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (insn.getOpcode() == Opcodes.GETFIELD) {
					FieldInsnNode fn = (FieldInsnNode)insn;
					if (fn.owner.equals(inventoryBasic.name) && fn.desc.equals("Ljava/util/List;")) hasList = true;
					if (fn.owner.equals(inventoryBasic.name) && fn.desc.equals("[L" + itemStack.name + ";")) hasArray = true;
				}
			}

			// public void markDirty()
			if (hasList && !hasArray) {
				addMethodMapping("net/minecraft/inventory/IInventory markDirty ()V",
						iInventory.name + " " + method.name + " " + method.desc);
			}
			// public void clear()
			else if (!hasList && hasArray) {
				addMethodMapping("net/minecraft/inventory/IInventory clear ()V",
						iInventory.name + " " + method.name + " " + method.desc);
			}
		}

		return true;
	}



	public static void generateClassMappings()
	{
		DynamicMappings.registerMappingsClass(DynamicMappings.class);

		generateMethodMappings();

	}


	//////////////////////////////////////////////////////////////////////////


	// Use untyped list in case someone compiles without debug version of ASM.
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static List<MethodNode> getMethodsWithDescriptor(List methods, String desc)
	{
		List<MethodNode> list = new ArrayList<MethodNode>();
		for (MethodNode method : (List<MethodNode>)methods) {
			if (method.desc.equals(desc)) list.add(method);
		}
		return list;
	}

	public static List<MethodNode> removeMethodsWithFlags(List<MethodNode> methods, int accFlags)
	{
		List<MethodNode> outList = new ArrayList<MethodNode>();
		for (MethodNode mn : methods) {
			if ((mn.access & accFlags) == 0) outList.add(mn);
		}
		return outList;
	}

	public static List<MethodNode> removeMethodsWithoutFlags(List<MethodNode> methods, int accFlags)
	{
		List<MethodNode> outList = new ArrayList<MethodNode>();
		for (MethodNode mn : methods) {
			if ((mn.access & accFlags) != 0) outList.add(mn);
		}
		return outList;
	}


	//public Item setUnlocalizedName(String unlocalizedName)
	@Mapping(providesMethods={
			"net/minecraft/item/Item getIdFromItem (Lnet/minecraft/item/Item;)I",
			"net/minecraft/item/Item getItemById (I)Lnet/minecraft/item/Item;",
			"net/minecraft/item/Item getItemFromBlock (Lnet/minecraft/block/Block;)Lnet/minecraft/item/Item;",
			"net/minecraft/item/Item getByNameOrId (Ljava/lang/String;)Lnet/minecraft/item/Item;",
			"net/minecraft/item/Item setUnlocalizedName (Ljava/lang/String;)Lnet/minecraft/item/Item;"
	},
	depends={
			"net/minecraft/item/Item",
			"net/minecraft/block/Block",
			"net/minecraft/item/ItemStack"
	})
	public static boolean getItemClassMethods()
	{
		ClassNode item = getClassNode(getClassMapping("net/minecraft/item/Item"));
		ClassNode block = getClassNode(getClassMapping("net/minecraft/block/Block"));
		ClassNode itemStack = getClassNode(getClassMapping("net/minecraft/item/ItemStack"));
		if (!MeddleUtil.notNull(item, block, itemStack)) return false;

		// Keep a local list because we'll remove them as we go to improve detection
		List<MethodNode> itemMethods = new ArrayList<MethodNode>();
		itemMethods.addAll(item.methods);

		List<MethodNode> methods;

		//public static int getIdFromItem(Item itemIn)
		methods = getMethodsWithDescriptor(itemMethods, "(L" + item.name + ";)I");
		methods = removeMethodsWithoutFlags(methods, Opcodes.ACC_STATIC);
		//for (MethodNode mn : methods) System.out.println(mn.name + " " + mn.desc);
		if (methods.size() == 1) {
			MethodNode mn = methods.get(0);
			itemMethods.remove(mn);
			addMethodMapping("net/minecraft/item/Item getIdFromItem (Lnet/minecraft/item/Item;)I",
					item.name + " " + mn.name + " " + mn.desc);
		}


		//public static Item getItemById(int id)
		methods = getMethodsWithDescriptor(itemMethods, "(I)L" + item.name + ";");
		methods = removeMethodsWithoutFlags(methods, Opcodes.ACC_STATIC);
		if (methods.size() == 1) {
			MethodNode mn = methods.get(0);
			itemMethods.remove(mn);
			addMethodMapping("net/minecraft/item/Item getItemById (I)Lnet/minecraft/item/Item;",
					item.name + " " + mn.name + " " + mn.desc);
		}


		//public static Item getItemFromBlock(Block blockIn)
		methods = getMethodsWithDescriptor(itemMethods, "(L" + block.name + ";)L" + item.name + ";");
		methods = removeMethodsWithoutFlags(methods, Opcodes.ACC_STATIC);
		if (methods.size() == 1) {
			MethodNode mn = methods.get(0);
			itemMethods.remove(mn);
			addMethodMapping("net/minecraft/item/Item getItemFromBlock (Lnet/minecraft/block/Block;)Lnet/minecraft/item/Item;",
					item.name + " " + mn.name + " " + mn.desc);
		}


		//public static Item getByNameOrId(String id)
		methods = getMethodsWithDescriptor(itemMethods, "(Ljava/lang/String;)L" + item.name + ";");
		methods = removeMethodsWithoutFlags(methods, Opcodes.ACC_STATIC);
		if (methods.size() == 1) {
			MethodNode mn = methods.get(0);
			itemMethods.remove(mn);
			addMethodMapping("net/minecraft/item/Item getByNameOrId (Ljava/lang/String;)Lnet/minecraft/item/Item;",
					item.name + " " + mn.name + " " + mn.desc);
		}


		// Item setUnlocalizedName(String)
		methods = getMethodsWithDescriptor(itemMethods, "(Ljava/lang/String;)L" + item.name + ";");
		methods = removeMethodsWithFlags(methods, Opcodes.ACC_STATIC);
		if (methods.size() == 1) {
			MethodNode mn = methods.get(0);
			itemMethods.remove(mn);
			addMethodMapping("net/minecraft/item/Item setUnlocalizedName (Ljava/lang/String;)Lnet/minecraft/item/Item;",
					item.name + " " + mn.name + " " + mn.desc);
		}

		return true;
	}



	public static boolean matchOpcodeSequence(AbstractInsnNode insn, int...opcodes)
	{
		for (int opcode : opcodes) {
			insn = getNextRealOpcode(insn);
			if (insn == null) return false;
			if (opcode != insn.getOpcode()) return false;
			insn = insn.getNext();
		}

		return true;
	}


	@Mapping(providesFields={
			"net/minecraft/item/ItemStack stackTagCompound Lnet/minecraft/nbt/NBTTagCompound;"
	},
	providesMethods={
			"net/minecraft/item/ItemStack loadItemStackFromNBT (Lnet/minecraft/nbt/NBTTagCompound;)Lnet/minecraft/item/ItemStack;",
			"net/minecraft/item/ItemStack setTagCompound (Lnet/minecraft/nbt/NBTTagCompound;)V",
			"net/minecraft/item/ItemStack readFromNBT (Lnet/minecraft/nbt/NBTTagCompound;)V",
			"net/minecraft/item/ItemStack isItemEnchanted ()Z",
			"net/minecraft/item/ItemStack hasTagCompound ()Z",
			"net/minecraft/item/ItemStack getItem ()Lnet/minecraft/item/Item;",
			"net/minecraft/item/ItemStack getTagCompound ()Lnet/minecraft/nbt/NBTTagCompound;",
			"net/minecraft/item/ItemStack getDisplayName ()Ljava/lang/String;",
			"net/minecraft/item/ItemStack getUnlocalizedName ()Ljava/lang/String;"
	},
	depends={
			"net/minecraft/item/ItemStack",
			"net/minecraft/item/Item",
			"net/minecraft/nbt/NBTTagCompound"
	})
	public static boolean processItemStackClass()
	{
		ClassNode itemStack = getClassNodeFromMapping("net/minecraft/item/ItemStack");
		ClassNode tagCompound = getClassNodeFromMapping("net/minecraft/nbt/NBTTagCompound");
		ClassNode item = getClassNodeFromMapping("net/minecraft/item/Item");
		if (itemStack == null || tagCompound == null || item == null) return false;

		// public static ItemStack loadItemStackFromNBT(NBTTagCompound nbt)
		List<MethodNode> methods = getMatchingMethods(itemStack, null, "(L" + tagCompound.name + ";)L" + itemStack.name + ";");
		if (methods.size() != 1) return false;
		addMethodMapping("net/minecraft/item/ItemStack loadItemStackFromNBT (Lnet/minecraft/nbt/NBTTagCompound;)Lnet/minecraft/item/ItemStack;",
				itemStack.name + " " + methods.get(0).name + " " + methods.get(0).desc);


		String stackTagCompoundField = null;

		methods = getMatchingMethods(itemStack, null, "(L" + tagCompound.name + ";)V");
		if (methods.size() != 2) return false;
		for (MethodNode method :  methods)
		{
			// public void setTagCompound(NBTTagCompound nbt)
			AbstractInsnNode firstReal = getNextRealOpcode(method.instructions.getFirst());
			if (firstReal.getOpcode() == Opcodes.ALOAD) {
				AbstractInsnNode nextNode = firstReal.getNext();
				if (nextNode != null && nextNode.getOpcode() == Opcodes.ALOAD) {
					nextNode = nextNode.getNext();
					if (nextNode != null && nextNode.getOpcode() == Opcodes.PUTFIELD) {
						FieldInsnNode fn = (FieldInsnNode)nextNode;
						if (fn.owner.equals(itemStack.name) && fn.desc.equals("L" + tagCompound.name + ";"))
						{
							stackTagCompoundField = fn.name;
							addFieldMapping("net/minecraft/item/ItemStack stackTagCompound Lnet/minecraft/nbt/NBTTagCompound;",
									itemStack.name + " " + fn.name + " " + fn.desc);
							addMethodMapping("net/minecraft/item/ItemStack setTagCompound (Lnet/minecraft/nbt/NBTTagCompound;)V",
									itemStack.name + " " + method.name + " " + method.desc);
							continue;
						}
					}
				}
			}

			// public void readFromNBT(NBTTagCompound nbt)
			for (AbstractInsnNode insn = firstReal; insn != null; insn = insn.getNext()) {
				if (!isLdcWithString(insn, "id")) continue;
				addMethodMapping("net/minecraft/item/ItemStack readFromNBT (Lnet/minecraft/nbt/NBTTagCompound;)V",
						itemStack.name + " " + method.name + " " + method.desc);
				break;
			}
		}

		if (stackTagCompoundField == null) return false;



		methods = getMatchingMethods(itemStack, null, "()Z");
		for (Iterator<MethodNode> iterator = methods.iterator(); iterator.hasNext();)
		{
			MethodNode method = iterator.next();
			if (!matchOpcodeSequence(method.instructions.getFirst(), Opcodes.ALOAD, Opcodes.GETFIELD, Opcodes.IFNULL)) {
				iterator.remove();
				continue;
			}

			boolean correctField = false;
			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (insn.getOpcode() != Opcodes.GETFIELD) continue;
				FieldInsnNode fn = (FieldInsnNode)insn;
				if (fn.owner.equals(itemStack.name) && fn.name.equals(stackTagCompoundField))
					correctField = true;
				break;
			}
			if (!correctField) { iterator.remove(); continue; }
		}

		if (methods.size() != 2) return false;

		for (MethodNode method : methods) {
			boolean isEnchant = false;
			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (isLdcWithString(insn, "ench")) { isEnchant = true; break; }
			}

			// public boolean isItemEnchanted()
			if (isEnchant) {
				addMethodMapping("net/minecraft/item/ItemStack isItemEnchanted ()Z", itemStack.name + " " + method.name + " ()Z");
			}
			// public boolean hasTagCompound()
			else {
				addMethodMapping("net/minecraft/item/ItemStack hasTagCompound ()Z", itemStack.name + " " + method.name + " ()Z");
			}
		}


		// public Item getItem()
		methods = getMatchingMethods(itemStack, null, "()L" + item.name + ";");
		if (methods.size() != 1) return false;
		addMethodMapping("net/minecraft/item/ItemStack getItem ()Lnet/minecraft/item/Item;",
				itemStack.name + " " + methods.get(0).name + " " + methods.get(0).desc);


		// public NBTTagCompound getTagCompound()
		methods = getMatchingMethods(itemStack, null, "()L" + tagCompound.name + ";");
		if (methods.size() != 1) return false;
		addMethodMapping("net/minecraft/item/ItemStack getTagCompound ()Lnet/minecraft/nbt/NBTTagCompound;",
				itemStack.name + " " + methods.get(0).name + " " + methods.get(0).desc);




		methods = getMatchingMethods(itemStack, null, "()Ljava/lang/String;");
		if (methods.size() != 3) return false;
		for (MethodNode method : methods) {
			if (method.name.equals("toString")) continue;

			boolean isDisplayName = false;
			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (isLdcWithString(insn, "display")) { isDisplayName = true; break; }
			}

			// public String getDisplayName()
			if (isDisplayName) {
				addMethodMapping("net/minecraft/item/ItemStack getDisplayName ()Ljava/lang/String;",
						itemStack.name + " " + method.name + " " + method.desc);
			}
			// public String getUnlocalizedName()
			else {
				addMethodMapping("net/minecraft/item/ItemStack getUnlocalizedName ()Ljava/lang/String;",
						itemStack.name + " " + method.name + " " + method.desc);
			}
		}

		return true;
	}



	public static void generateMethodMappings()
	{

	}


	public static String getClassMapping(String deobfClassName)
	{
		return classMappings.get(deobfClassName.replace(".",  "/"));
	}

	public static String getReverseClassMapping(String obfClassName)
	{
		return reverseClassMappings.get(obfClassName.replace(".",  "/"));
	}


	public static void addClassMapping(String deobfClassName, ClassNode node)
	{
		if (deobfClassName == null) return;
		addClassMapping(deobfClassName, node.name);
	}

	public static void addClassMapping(String deobfClassName, String obfClassName)
	{
		classMappings.put(deobfClassName, obfClassName);
		reverseClassMappings.put(obfClassName, deobfClassName);
	}



	public static String getMethodMapping(String className, String methodName, String methodDesc)
	{
		return methodMappings.get(className + " " + methodName + " " + methodDesc);
	}

	public static String getMethodMapping(String mapping)
	{
		return methodMappings.get(mapping);
	}

	public static String getMethodMappingName(String className, String methodName, String methodDesc)
	{
		String mapping = getMethodMapping(className, methodName, methodDesc);
		if (mapping == null) return null;
		String [] split = mapping.split(" ");
		return split.length >= 3 ? split[1] : null;
	}

	// Both in the format of "classname methodname methoddesc"
	public static void addMethodMapping(String deobfMethodDesc, String obfMethodDesc)
	{
		methodMappings.put(deobfMethodDesc, obfMethodDesc);
		reverseMethodMappings.put(obfMethodDesc, deobfMethodDesc);
	}

	public static void addFieldMapping(String deobfFieldDesc, String obfFieldDesc)
	{
		fieldMappings.put(deobfFieldDesc, obfFieldDesc);
		reverseFieldMappings.put(obfFieldDesc, deobfFieldDesc);
	}



	private static class MappingMethod
	{
		final Method method;
		final String[] provides;
		final String[] depends;

		final String[] providesMethods;
		final String[] dependsMethods;

		final String[] providesFields;
		final String[] dependsFields;

		public MappingMethod(Method m, Mapping mapping)
		{
			method = m;
			provides = mapping.provides();
			depends = mapping.depends();
			providesMethods = mapping.providesMethods();
			dependsMethods = mapping.dependsMethods();
			providesFields = mapping.providesFields();
			dependsFields = mapping.dependsFields();
		}
	}



	public static boolean registerMappingsClass(Class<? extends Object> mappingsClass)
	{
		List<MappingMethod> mappingMethods = new ArrayList<MappingMethod>();

		for (Method method : mappingsClass.getMethods())
		{
			if (!method.isAnnotationPresent(Mapping.class)) continue;
			Mapping mapping = method.getAnnotation(Mapping.class);
			mappingMethods.add(new MappingMethod(method, mapping));
		}

		while (true)
		{
			int startSize = mappingMethods.size();
			for (Iterator<MappingMethod> it = mappingMethods.iterator(); it.hasNext();)
			{
				MappingMethod mm = it.next();

				boolean hasDepends = true;
				for (String depend : mm.depends) {
					if (!classMappings.keySet().contains(depend)) hasDepends = false;
				}
				for (String depend : mm.dependsFields) {
					if (!fieldMappings.keySet().contains(depend)) hasDepends = false;
				}
				for (String depend : mm.dependsMethods) {
					if (!methodMappings.keySet().contains(depend)) hasDepends = false;
				}
				if (!hasDepends) continue;

				try {
					mm.method.invoke(null);
				} catch (Exception e) {
					e.printStackTrace();
				}

				for (String provider : mm.provides)
				{
					if (!classMappings.keySet().contains(provider))
						System.out.println(mm.method.getName() + " didn't provide mapping for class " + provider);
				}

				for (String provider : mm.providesFields)
				{
					if (!fieldMappings.keySet().contains(provider))
						System.out.println(mm.method.getName() + " didn't provide mapping for field " + provider);
				}

				for (String provider : mm.providesMethods)
				{
					if (!methodMappings.keySet().contains(provider))
						System.out.println(mm.method.getName() + " didn't provide mapping for method " + provider);
				}

				it.remove();
			}

			if (mappingMethods.size() == 0) return true;

			if (startSize == mappingMethods.size())
			{
				System.out.println("Unmet mapping dependencies in " + mappingsClass.getName() + "!");
				for (MappingMethod mm : mappingMethods) {
					System.out.println("  Method: " + mm.method.getName());
					for (String depend : mm.depends) {
						if (!classMappings.keySet().contains(depend)) System.out.println("    " + depend);
					}
				}
				return false;
			}
		}
	}


	public static void main(String[] args)
	{
		DynamicMappings.registerMappingsClass(DynamicMappings.class);
		if (MeddleUtil.isClientJar()) DynamicClientMappings.generateClassMappings();

		System.out.println("Minecraft jar type: " + (MeddleUtil.isClientJar() ? "client" : "server"));

		System.out.println("\nCLASSES:");

		List<String> sorted = new ArrayList<String>();
		sorted.addAll(classMappings.keySet());
		Collections.sort(sorted);
		for (String s : sorted) {
			System.out.println(s + " -> " + classMappings.get(s));
		}

		System.out.println("\nFIELDS:");

		sorted.clear();
		sorted.addAll(fieldMappings.keySet());
		Collections.sort(sorted);
		for (String s : sorted) {
			System.out.println(s + " -> " + fieldMappings.get(s));
		}

		System.out.println("\nMETHODS:");

		sorted.clear();
		sorted.addAll(methodMappings.keySet());
		Collections.sort(sorted);
		for (String s : sorted) {
			System.out.println(s + " -> " + methodMappings.get(s));
		}
	}



}

