package net.fybertech.dynamicmappings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fybertech.meddle.Meddle;
import net.fybertech.meddle.MeddleUtil;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;



public class DynamicClientMappings
{
	
	public static void addClassMapping(String className, ClassNode cn) {
		DynamicMappings.addClassMapping(className, cn);
	}
	
	public static void addClassMapping(String className, String cn) {
		DynamicMappings.addClassMapping(className, cn);
	}
	
	public static void addMethodMapping(String deobf, String obf) {
		DynamicMappings.addMethodMapping(deobf, obf);
	}
	
	public static ClassNode getClassNode(String className) {
		return DynamicMappings.getClassNode(className);
	}
	
	public static ClassNode getClassNodeFromMapping(String mapping) {
		return DynamicMappings.getClassNodeFromMapping(mapping);
	}
	

	@Mapping(provides="net/minecraft/client/main/Main")
	public static boolean getMainClass()
	{
		ClassNode main = getClassNode("net/minecraft/client/main/Main");
		if (main == null) return false;
		addClassMapping("net/minecraft/client/main/Main", main);
		return true;
	}


	@Mapping(provides={
			"net/minecraft/client/Minecraft",
			"net/minecraft/client/main/GameConfiguration"},			
			depends="net/minecraft/client/main/Main")
	public static boolean getMinecraftClass()
	{
		ClassNode main = getClassNodeFromMapping("net/minecraft/client/main/Main");
		if (main == null) return false;

		List<MethodNode> methods = DynamicMappings.getMatchingMethods(main, "main", "([Ljava/lang/String;)V");
		if (methods.size() != 1) return false;
		MethodNode mainMethod = methods.get(0);

		String minecraftClassName = null;
		String gameConfigClassName = null;
		boolean confirmed = false;

		// We're looking for these instructions:
		// NEW net/minecraft/client/Minecraft
		// INVOKESPECIAL net/minecraft/client/Minecraft.<init> (Lnet/minecraft/client/main/GameConfiguration;)V
		// INVOKEVIRTUAL net/minecraft/client/Minecraft.run ()V
		for (AbstractInsnNode insn = mainMethod.instructions.getLast(); insn != null; insn = insn.getPrevious())
		{
			if (insn.getOpcode() == Opcodes.INVOKEVIRTUAL) {
				MethodInsnNode mn = (MethodInsnNode)insn;
				minecraftClassName = mn.owner;
			}

			else if (insn.getOpcode() == Opcodes.INVOKESPECIAL) {
				MethodInsnNode mn = (MethodInsnNode)insn;

				// Check for something wrong
				if (minecraftClassName == null || !mn.owner.equals(minecraftClassName)) return false;

				Type t = Type.getMethodType(mn.desc);
				Type[] args = t.getArgumentTypes();
				if (args.length != 1) return false;

				// Get this while we're here
				gameConfigClassName = args[0].getClassName();
			}

			else if (insn.getOpcode() == Opcodes.NEW) {
				TypeInsnNode vn = (TypeInsnNode)insn;
				if (minecraftClassName != null && vn.desc.equals(minecraftClassName)) {
					confirmed = true;
					break;
				}
			}
		}

		if (confirmed) {
			addClassMapping("net/minecraft/client/Minecraft", getClassNode(minecraftClassName));
			addClassMapping("net/minecraft/client/main/GameConfiguration", getClassNode(gameConfigClassName));
			return true;
		}

		return false;
	}


	
	
	@Mapping(provides={
			"net/minecraft/world/WorldSettings"
			},
			providesMethods={
			"net/minecraft/client/Minecraft getMinecraft ()Lnet/minecraft/client/Minecraft;",
			"net/minecraft/client/Minecraft getRenderItem ()Lnet/minecraft/client/renderer/entity/RenderItem;",
			"net/minecraft/client/Minecraft refreshResources ()V",
			"net/minecraft/client/Minecraft launchIntegratedServer (Ljava/lang/String;Ljava/lang/String;Lnet/minecraft/world/WorldSettings;)V"
			},
			depends={
			"net/minecraft/client/Minecraft",
			"net/minecraft/client/renderer/entity/RenderItem"
			})
	public static boolean parseMinecraftClass()
	{
		ClassNode minecraft = getClassNodeFromMapping("net/minecraft/client/Minecraft");
		ClassNode renderItem = getClassNodeFromMapping("net/minecraft/client/renderer/entity/RenderItem");
		if (minecraft == null || renderItem == null) return false;
		
		List<MethodNode> methods = DynamicMappings.getMatchingMethods(minecraft,  null, "()L" + minecraft.name + ";");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/client/Minecraft getMinecraft ()Lnet/minecraft/client/Minecraft;",
					minecraft.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		methods = DynamicMappings.getMatchingMethods(minecraft, null, "()L" + renderItem.name + ";");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/client/Minecraft getRenderItem ()Lnet/minecraft/client/renderer/entity/RenderItem;",
					minecraft.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		boolean found = false;
		
		// public void refreshResources()
		methods = DynamicMappings.getMatchingMethods(minecraft, null, "()V");		
		for (MethodNode method : methods) {
			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (!DynamicMappings.isLdcWithString(insn, "Caught error stitching, removing all assigned resourcepacks")) continue;				
				addMethodMapping("net/minecraft/client/Minecraft refreshResources ()V", minecraft.name + " " + method.name + " ()V");
				found = true;				
			}
			if (found) break;
		}
		
		methods.clear();
		for (MethodNode method : minecraft.methods) {
			if (!DynamicMappings.checkMethodParameters(method,  Type.OBJECT, Type.OBJECT, Type.OBJECT)) continue;
			if (Type.getMethodType(method.desc).getReturnType().getSort() != Type.VOID) continue;
			if (!method.desc.startsWith("(Ljava/lang/String;Ljava/lang/String;L")) continue;
			methods.add(method);
		}
		if (methods.size() == 1) {
			MethodNode method = methods.get(0);
			Type t = Type.getMethodType(method.desc);
			
			String worldSettings = t.getArgumentTypes()[2].getClassName();
			addClassMapping("net/minecraft/world/WorldSettings", worldSettings);
			addMethodMapping("net/minecraft/client/Minecraft launchIntegratedServer (Ljava/lang/String;Ljava/lang/String;Lnet/minecraft/world/WorldSettings;)V",
					minecraft.name + " " + method.name + " " + method.desc);
		}
		
		return true;
	}


	
	
	@Mapping(providesMethods={
			"net/minecraft/client/renderer/entity/RenderItem getItemModelMesher ()Lnet/minecraft/client/renderer/ItemModelMesher;"
			},
			depends={
			"net/minecraft/client/renderer/entity/RenderItem",
			"net/minecraft/client/renderer/ItemModelMesher"
			})
	public static boolean parseRenderItemClass()
	{
		ClassNode renderItem = getClassNodeFromMapping("net/minecraft/client/renderer/entity/RenderItem");
		ClassNode modelMesher = getClassNodeFromMapping("net/minecraft/client/renderer/ItemModelMesher");
		if (renderItem == null || modelMesher == null) return false;
		
		List<MethodNode> methods = DynamicMappings.getMatchingMethods(renderItem,  null, "()L" + modelMesher.name + ";");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/client/renderer/entity/RenderItem getItemModelMesher ()Lnet/minecraft/client/renderer/ItemModelMesher;",
					renderItem.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		return true;
	}
	
	

	@Mapping(provides="net/minecraft/client/renderer/entity/RenderItem", depends="net/minecraft/client/Minecraft")
	public static boolean getRenderItemClass()
	{
		ClassNode minecraft = getClassNodeFromMapping("net/minecraft/client/Minecraft");
		if (minecraft == null) return false;

		for (MethodNode method : (List<MethodNode>)minecraft.methods) {
			Type t = Type.getMethodType(method.desc);
			if (t.getArgumentTypes().length != 0) continue;
			if (t.getReturnType().getSort() != Type.OBJECT) continue;

			String className = t.getReturnType().getClassName();
			if (className.contains(".")) continue;

			if (DynamicMappings.searchConstantPoolForStrings(className, "textures/misc/enchanted_item_glint.png", "Rendering item")) {
				addClassMapping("net/minecraft/client/renderer/entity/RenderItem", getClassNode(className));
				return true;
			}

			// TODO - Use this to process other getters from Minecraft class

		}

		return false;
	}


	@Mapping(provides="net/minecraft/client/renderer/ItemModelMesher", depends="net/minecraft/client/renderer/entity/RenderItem")
	public static boolean getItemModelMesherClass() 
	{
		ClassNode renderItem = getClassNodeFromMapping("net/minecraft/client/renderer/entity/RenderItem");
		if (renderItem == null) return false;

		// Find constructor RenderItem(TextureManager, ModelManager)
		MethodNode initMethod = null;
		int count = 0;
		for (MethodNode method : (List<MethodNode>)renderItem.methods) {
			if (!method.name.equals("<init>")) continue;
			if (!DynamicMappings.checkMethodParameters(method, Type.OBJECT, Type.OBJECT)) continue;
			count++;
			initMethod = method;
		}
		if (count != 1) return false;

		Type t = Type.getMethodType(initMethod.desc);
		Type[] args = t.getArgumentTypes();
		// TODO: Get TextureManager and ModelManager from args

		String className = null;

		count = 0;
		for (AbstractInsnNode insn = initMethod.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn.getOpcode() == Opcodes.NEW) {
				TypeInsnNode tn = (TypeInsnNode)insn;
				className = tn.desc;
				count++;
			}
		}
		if (count != 1 || className == null) return false;

		// We'll assume this is it, might do more detailed confirmations later if necessary
		addClassMapping("net/minecraft/client/renderer/ItemModelMesher", getClassNode(className));
		return true;
	}


	@Mapping(provides={
			"net/minecraft/client/gui/GuiMainMenu",  
			"net/minecraft/client/gui/GuiIngame",
			"net/minecraft/client/multiplayer/GuiConnecting",
			"net/minecraft/client/renderer/RenderGlobal"},
			depends="net/minecraft/client/Minecraft")
	public static boolean getGuiMainMenuClass()
	{
		ClassNode minecraft = getClassNodeFromMapping("net/minecraft/client/Minecraft");
		if (minecraft == null) return false;

		List<String> postStartupClasses = new ArrayList<String>();
		List<String> startupClasses = new ArrayList<String>();

		boolean foundMethod = false;
		for (MethodNode method : (List<MethodNode>)minecraft.methods) {
			//if (!DynamicMappings.checkMethodParameters(method, Type.OBJECT)) continue;
			Type t = Type.getMethodType(method.desc);
			if (t.getReturnType().getSort() != Type.VOID) continue;
			if (t.getArgumentTypes().length != 0) continue;

			boolean foundLWJGLVersion = false;
			boolean foundPostStartup = false;
			boolean foundStartup = false;
			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext())
			{
				if (!foundLWJGLVersion && !DynamicMappings.isLdcWithString(insn, "LWJGL Version: ")) continue;
				foundLWJGLVersion = true;
				
				if (!foundStartup && !DynamicMappings.isLdcWithString(insn, "Startup")) continue;
				foundStartup = true;
				
				foundMethod = true;
				
				if (foundStartup && !foundPostStartup) {
					if (insn.getOpcode() == Opcodes.NEW) {
						TypeInsnNode tn = (TypeInsnNode)insn;
						startupClasses.add(tn.desc);
					}
				}
				
				if (!foundPostStartup && !DynamicMappings.isLdcWithString(insn, "Post startup")) continue;
				foundPostStartup = true;

				if (insn.getOpcode() == Opcodes.NEW) {
					TypeInsnNode tn = (TypeInsnNode)insn;
					postStartupClasses.add(tn.desc);
				}
			}

			if (foundMethod) break;
		}

		String guiIngame = null;
		String guiConnecting = null;
		String guiMainMenu = null;
		String loadingScreenRenderer = null;

		for (String className : postStartupClasses) {

			if (guiIngame == null && DynamicMappings.searchConstantPoolForStrings(className, "textures/misc/vignette.png", "bossHealth")) {
				guiIngame = className;
				continue;
			}

			if (guiConnecting == null && DynamicMappings.searchConstantPoolForStrings(className, "Connecting to", "connect.connecting")) {
				guiConnecting = className;
				continue;
			}

			if (guiMainMenu == null && DynamicMappings.searchConstantPoolForStrings(className, "texts/splashes.txt", "Merry X-mas!")) {
				guiMainMenu = className;
				continue;
			}

			// TODO - Figure out a way to scan for the class
			//if (loadingScreenRenderer == null
		}

		String renderGlobal = null;
		for (String className : startupClasses) {
			if (renderGlobal == null && DynamicMappings.searchConstantPoolForStrings(className, "textures/environment/moon_phases.png", "Exception while adding particle", "random.click")) {
				renderGlobal = className;
				continue;
			}
		}

		if (guiMainMenu != null)
			addClassMapping("net/minecraft/client/gui/GuiMainMenu", getClassNode(guiMainMenu));
		
		if (guiIngame != null)
			addClassMapping("net/minecraft/client/gui/GuiIngame", getClassNode(guiIngame));
		
		if (guiConnecting != null)
			addClassMapping("net/minecraft/client/multiplayer/GuiConnecting", getClassNode(guiConnecting));
		
		if (renderGlobal != null)
			addClassMapping("net/minecraft/client/renderer/RenderGlobal", getClassNode(renderGlobal));
		
		return true;
	}


	@Mapping(provides="net/minecraft/client/resources/model/ModelResourceLocation",
			 depends={
			"net/minecraft/item/Item",
			"net/minecraft/client/renderer/ItemModelMesher"})
	public static boolean getModelResourceLocationClass()
	{
		ClassNode item = getClassNodeFromMapping("net/minecraft/item/Item");
		ClassNode itemModelMesher = getClassNodeFromMapping("net/minecraft/client/renderer/ItemModelMesher");
		if (!MeddleUtil.notNull(item, itemModelMesher)) return false;

		for (MethodNode method : (List<MethodNode>)itemModelMesher.methods) {
			if (!DynamicMappings.checkMethodParameters(method, Type.OBJECT, Type.INT, Type.OBJECT)) continue;
			Type t = Type.getMethodType(method.desc);
			if (!t.getArgumentTypes()[0].getClassName().equals(item.name)) continue;
			
			addClassMapping("net/minecraft/client/resources/model/ModelResourceLocation", 
					getClassNode(t.getArgumentTypes()[2].getClassName()));
			return true;
		}

		return false;
	}
	
	
	@Mapping(providesMethods={
			"net/minecraft/item/Item getColorFromItemStack (Lnet/minecraft/item/ItemStack;I)I",
			},
			depends={
			"net/minecraft/item/Item",
			"net/minecraft/item/ItemStack"
			})
	public static boolean getItemClassMethods()
	{
		ClassNode item = getClassNodeFromMapping("net/minecraft/item/Item");
		ClassNode itemStack = getClassNodeFromMapping("net/minecraft/item/ItemStack");
		
		// public int getColorFromItemStack(ItemStack, int)
		List<MethodNode> methods = DynamicMappings.getMethodsWithDescriptor(item.methods, "(L" + itemStack.name + ";I)I");
		methods = DynamicMappings.removeMethodsWithFlags(methods, Opcodes.ACC_STATIC);
		if (methods.size() == 1) {
			MethodNode mn = methods.get(0);
			DynamicMappings.addMethodMapping(
					"net/minecraft/item/Item getColorFromItemStack (Lnet/minecraft/item/ItemStack;I)I",
					item.name + " " + mn.name + " " + mn.desc);
		}
		
		return true;
	}
	

	@Mapping(providesMethods={
			"net/minecraft/client/renderer/ItemModelMesher register (Lnet/minecraft/item/Item;ILnet/minecraft/client/resources/model/ModelResourceLocation;)V"
			},
			depends={
			"net/minecraft/item/Item",
			"net/minecraft/client/renderer/ItemModelMesher",
			"net/minecraft/client/resources/model/ModelResourceLocation"
			})
	public static boolean parseItemModelMesherClass()
	{
		ClassNode item = getClassNodeFromMapping("net/minecraft/item/Item");
		ClassNode modelMesher = getClassNodeFromMapping("net/minecraft/client/renderer/ItemModelMesher");
		ClassNode modelResLoc = getClassNodeFromMapping("net/minecraft/client/resources/model/ModelResourceLocation");
		if (item == null || modelMesher == null || modelResLoc == null) return false;
		
		List<MethodNode> methods = DynamicMappings.getMatchingMethods(modelMesher, null, "(L" + item.name + ";IL" + modelResLoc.name + ";)V");
		if (methods.size() == 1) {			
			addMethodMapping("net/minecraft/client/renderer/ItemModelMesher register (Lnet/minecraft/item/Item;ILnet/minecraft/client/resources/model/ModelResourceLocation;)V",
					modelMesher.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		return true;		
	}
	
	
	@Mapping(provides={
			"net/minecraft/client/gui/Gui",
			"net/minecraft/client/gui/GuiScreen",
			"net/minecraft/client/gui/GuiYesNoCallback"
			},
			depends={
			"net/minecraft/client/gui/GuiMainMenu"
			})	
	public static boolean findGuiStuff()
	{
		ClassNode guiMainMenu = getClassNodeFromMapping("net/minecraft/client/gui/GuiMainMenu");
		if (guiMainMenu == null || guiMainMenu.superName == null) return false;		
				
		ClassNode guiScreen = null;
		String guiScreenName = null;
		
		if (DynamicMappings.searchConstantPoolForStrings(guiMainMenu.superName, "Invalid Item!", "java.awt.Desktop")) {
			guiScreenName = guiMainMenu.superName;
			guiScreen = getClassNode(guiScreenName);
			addClassMapping("net/minecraft/client/gui/GuiScreen", guiScreenName);
		}		
		
		if (guiScreen == null || guiScreen.superName == null) return false;
		
		if (guiScreen.interfaces.size() == 1) {
			addClassMapping("net/minecraft/client/gui/GuiYesNoCallback", guiScreen.interfaces.get(0));
		}
		
		if (DynamicMappings.searchConstantPoolForStrings(guiScreen.superName, "textures/gui/options_background.png", "textures/gui/icons.png")) {			
			addClassMapping("net/minecraft/client/gui/Gui", guiScreen.superName);
		}
		
		return true;
	}
	
	

	public static void generateClassMappings()
	{
		if (!MeddleUtil.isClientJar()) return;
		
		DynamicMappings.registerMappingsClass(DynamicClientMappings.class);		
	}



	public static void main(String[] args)
	{
		DynamicMappings.generateClassMappings();
		generateClassMappings();

		String[] sortedKeys = DynamicMappings.classMappings.keySet().toArray(new String[0]);
		Arrays.sort(sortedKeys);
		for (String key : sortedKeys) {
			String className = DynamicMappings.getClassMapping(key);
			System.out.println(key + " -> " + (className != null ? className : "[MISSING]"));
		}
	}


}

