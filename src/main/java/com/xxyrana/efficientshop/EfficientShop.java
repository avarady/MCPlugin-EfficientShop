package com.xxyrana.efficientshop;

import java.util.HashMap;
import java.util.logging.Logger;

import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 
 * @author xxyrana
 *
 */
public class EfficientShop extends JavaPlugin implements Listener {
	private static final Logger log = Logger.getLogger("Minecraft");
	public static Economy econ = null;
	public static Permission perms = null;
	public static Chat chat = null;
	private static String buyString = "§aBuy";
	private static String sellallString = "§cSell All";
	private static String titleString = "§a[Shop]";
	private static int[] itemnumbers = {1, 5, 10, 15, 20, 25, 30, 45, 64};
	private static HashMap<String, String> materials = new HashMap<String, String>();
	private static HashMap<String, Byte> mdata = new HashMap<String, Byte>();

	@Override
	public void onEnable() {
		loadConfiguration();
		getServer().getPluginManager().registerEvents(this, this);
		if (!setupEconomy() ) {
			log.severe(String.format("[%s] - Disabled due to no Vault dependency found!", getDescription().getName()));
			getServer().getPluginManager().disablePlugin(this);
			return;
		}
		setupPermissions();
		setupChat();
		setupMaterials();
	}

	@Override
	public void onDisable() {
		log.info(String.format("[%s] Disabled Version %s", getDescription().getName(), getDescription().getVersion()));
	}


	//-----
	// Event Handlers
	//-----
	/**
	 * Modifies properly-formatted signs
	 * @param event
	 */
	@EventHandler
	public void eventSignChanged(SignChangeEvent event)
	{ 
		Player player = event.getPlayer();
		reloadConfig();
		if (perms.has(player, "eshop.place") && isValid(event)){
			if(getMaterial(event.getLine(2))!=null){
				event.setLine(0, titleString);
				if(event.getLine(1).equalsIgnoreCase("Buy")){
					event.setLine(1, buyString);
				} else {
					event.setLine(1, sellallString);
				}
			} else {
				event.setLine(2, "?");
			}
		}
	}

	/**
	 * Handles EfficientShop sign clicks
	 * @param event
	 */
	@EventHandler
	public void onPlayerInteract(PlayerInteractEvent event){
		Player player = event.getPlayer();
		if(event.getAction() == Action.RIGHT_CLICK_BLOCK){
			Block b = event.getClickedBlock();
			if (b.getType() == Material.WALL_SIGN || b.getType() == Material.SIGN_POST) {

				//Get info from sign
				Sign sign = (Sign) event.getClickedBlock().getState();
				String sTitle = sign.getLine(0);

				//Check title
				reloadConfig();
				if(perms.has(player, "eshop.use") && sTitle.substring(2).equalsIgnoreCase("[Shop]")){
					//Get more sign info
					String sType = sign.getLine(1);
					String sItem = sign.getLine(2);
					String sPrice = sign.getLine(3);
					double price = Double.parseDouble(sPrice.replace(getConfig().getString("currency"), "").replace("/ea", ""));

					//-------------------//
					// Buy Sign Handling //
					//-------------------//
					if(sType.substring(2).equalsIgnoreCase("Buy")){
						//Set up Buy GUI
						IconMenu menu = new IconMenu(player, "Buy - " + sItem + " " + sPrice, 9, new myListener(player, sItem, price), this);
						reloadConfig();
						ItemStack is;
						for(int i=0; i<9; i++){
							//menu.setOption(i, getItemStack(sItem, itemnumbers[i]), sItem, "Price: " + getConfig().getString("currency") + String.format("%.2f", price*itemnumbers[i]));
							is = getItemStack(sItem, itemnumbers[i]);
							menu.setOption(i, is, is.getType().toString(), "Price: " + getConfig().getString("currency") + String.format("%.2f", price*itemnumbers[i]));
						}
						menu.open(player);

						//-----------------------//
						// SellAll Sign Handling //
						//-----------------------//
					} else if (sType.substring(2).equalsIgnoreCase("Sell All")){
						int i = getAmount(event.getPlayer(), sItem);
						reloadConfig();

						if(i>0){
							//Remove items
							ItemStack itemstack = getItemStack(sItem, i);
							player.getInventory().removeItem(itemstack);
							player.updateInventory();

							//Pay for items taken
							int j = getAmount(event.getPlayer(), sItem); //new amount
							if(j < i){
								double totalprice = price*(i-j);
								econ.depositPlayer(player, totalprice);
								String added = getConfig().getString("messages.addmoney").replace("&", "§");
								added = added.replace("<amount>", getConfig().getString("currency") + String.format("%.2f", totalprice));
								player.sendMessage(added);
								log.info(player.getName() + " has sold " + (i-j) + " " + itemstack.getType().toString() + " for " + getConfig().getString("currency") + totalprice + ".");
							} else {
								player.sendMessage(getConfig().getString("messages.nosellableitem").replace("&", "§"));
							}
						} else {
							player.sendMessage(getConfig().getString("messages.noitem").replace("&", "§"));
						}
						event.setCancelled(true);
					} else { //Something has gone wrong!
						log.info("An invalid shop sign has been used.");
					}
				}
			}
		}
	}

	//-----
	// Helper Functions
	//-----
	private Boolean isValid(SignChangeEvent event){
		if(event.getLine(0).equalsIgnoreCase("[Shop]")
				&& (event.getLine(1).equalsIgnoreCase("Buy") || event.getLine(1).equalsIgnoreCase("SellAll") || event.getLine(1).equalsIgnoreCase("Sell All"))
				&& (event.getLine(3).startsWith(getConfig().getString("currency"), 0))
				&& (event.getLine(3)).endsWith("/ea")){ 
			return true;
		}
		return false;
	}



	//-----
	// Vault Setup
	//-----
	private boolean setupEconomy() {
		if (getServer().getPluginManager().getPlugin("Vault") == null) {
			return false;
		}
		RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
		if (rsp == null) {
			return false;
		}
		econ = rsp.getProvider();
		return econ != null;
	}

	private boolean setupChat() {
		RegisteredServiceProvider<Chat> rsp = getServer().getServicesManager().getRegistration(Chat.class);
		chat = rsp.getProvider();
		return chat != null;
	}

	private boolean setupPermissions() {
		RegisteredServiceProvider<Permission> rsp = getServer().getServicesManager().getRegistration(Permission.class);
		perms = rsp.getProvider();
		return perms != null;
	}


	//-----
	// Icon menu Listener
	//-----
	public class myListener implements IconMenu.OptionClickEventHandler
	{
		double price;
		String sItem;
		Player p;
		public myListener(Player p, String i, double price){
			this.p = p;
			this.price = price;
			sItem = i;
		}
		@Override
		public void onOptionClick(IconMenu.OptionClickEvent event) {
			reloadConfig();
			Player player = event.getPlayer();
			event.setWillClose(true);
			event.setWillDestroy(true);
			//Get info from sign
			if(!player.equals(p)){
				return;
			}
			int option = event.getPosition();
			double totalprice = price*itemnumbers[option];
			//Check balance
			if(econ.getBalance(player) >= totalprice){
				//Take money
				econ.withdrawPlayer(player, totalprice);
				String taken = getConfig().getString("messages.takemoney").replace("&", "§");
				taken = taken.replace("<amount>", getConfig().getString("currency") + String.format("%.2f", totalprice));
				player.sendMessage(taken);
				//Give items=
				PlayerInventory inventory = player.getInventory();
				ItemStack itemstack = getItemStack(sItem, itemnumbers[option]);
				inventory.addItem(itemstack);
				log.info(player.getName() + " has purchased " + itemnumbers[option] + " " + itemstack.getType().toString() + " for " + getConfig().getString("currency") + totalprice + ".");
			} else {
				player.sendMessage(getConfig().getString("messages.nomoney").replace("&", "§"));

			}
		}

	}

	//--------//
	// Config //
	//--------//
	public void loadConfiguration(){
		getConfig().options().copyDefaults(true);
		saveConfig();
	}

	//-------------//
	// Item Lookup //
	//-------------//
	private int getAmount(Player player, String sItem)
	{
		PlayerInventory inventory = player.getInventory();
		ItemStack[] items = inventory.getContents();
		int has = 0;
		for (ItemStack item : items)
		{
			if ((item != null) && (item.getType().equals(getMaterial(sItem))) && (item.getAmount() > 0)
					&& (item.getDurability() == getData(sItem)))
			{
				has += item.getAmount();
			}
		}
		return has;
	}
	private ItemStack getItemStack(String str, int num){
		Material m;
		//Check for id formatting
		if(str.contains(":")){
			String[] ids = str.split(":");
			m = Material.matchMaterial(ids[0]);
			ItemStack i = new ItemStack(m, num);
			i.setDurability(Byte.parseByte(ids[1]));
			return i;
		}
		//Check built in material function
		m = Material.matchMaterial(str);
		if(m!=null){
			ItemStack i = new ItemStack(m, num);
			return i;
		}
		//Check custom aliases
		String key = str.toLowerCase();
		if(key.contains(" ")){
			key = key.replace(" ", "");
		}
		if(materials.containsKey(key)){
			m = Material.matchMaterial(materials.get(key));
			if(m!=null){
				ItemStack i = new ItemStack(m, num);
				if(mdata.containsKey(key)){
					i.setDurability(mdata.get(key));
				}
				return i;
			}
		}
		//Does not exist or not accounted for
		return null;
	}
	private Material getMaterial(String str){
		Material m;
		//Check for id formatting
		if(str.contains(":")){
			String[] ids = str.split(":");
			m = Material.matchMaterial(ids[0]);
			return m;
		}
		//check built in material function
		m = Material.matchMaterial(str);
		if(m!=null){
			return m;
		}
		//Check custom aliases
		String key = str.toLowerCase();
		if(key.contains(" ")){
			key = key.replace(" ", "");
		}
		if(materials.containsKey(key)){
			m = Material.matchMaterial(materials.get(key));
			return m;
		}
		//Does not exist or not accounted for
		return null;
	}

	private short getData(String str){
		if(str != null){
			String key = str.toLowerCase();
			if(key.contains(" ")){
				key = key.replace(" ", "");
			}
			if(mdata!=null && mdata.containsKey(key)){
				return mdata.get(key);
			}
		}
		return (byte) 0;
	}
	private void setupAdd(String[] names, String str){
		for(int i=0; i<names.length; i++){
			materials.put(names[i], str);
		}
	}
	private void setupMaterials(){
		String str;
		byte i=0;
		
		//-----------------//
		// Purely Aliases, //
		//   No Metadata   //
		//-----------------//
		materials.put("grassblock", "grass");
		materials.put("cobble", "cobblestone");
		materials.put("cobweb", "web");
		materials.put("mossstone", "mossy_cobblestone");
		materials.put("mossycobble", "mossy_cobblestone");
		materials.put("spawner", "mob_spawner");
		materials.put("monsterspawner", "mob_spawner");
		materials.put("stonebrick", "98");
		materials.put("stonebricks", "98");
		materials.put("ironbars", "101");
		materials.put("glasspane", "102");
		materials.put("vines", "vine");
		materials.put("mycelium", "110");
		materials.put("netherwart", "115");
		materials.put("enchantingtable", "116");
		materials.put("enchanttable", "116");
		materials.put("endportalframe", "120");
		materials.put("endportal", "120");
		materials.put("endportalblock", "120");
		materials.put("endstone", "121");
		materials.put("redstonelamp", "123");
		materials.put("lamp", "123");
		materials.put("hook", "tripwire_hook");
		materials.put("commandblock", "137");
		materials.put("daylightsensor", "daylight_detector");
		materials.put("quartzore", "153");
		materials.put("haybale", "hay_block");
		materials.put("hay", "hay_block");
		materials.put("ironshovel", "256");
		materials.put("ironpickaxe", "257");
		materials.put("ironpick", "257");
		materials.put("ironaxe", "258");
		materials.put("ironsword", "267");
		materials.put("woodensword", "268");
		materials.put("woodenshovel", "269");
		materials.put("woodenpickaxe", "270");
		materials.put("woodenpick", "270");
		materials.put("woodenaxe", "271");
		materials.put("diamondsword", "276");
		materials.put("diamondshovel", "276");
		materials.put("diamondpickaxe", "278");
		materials.put("diamondpick", "278");
		materials.put("diamondaxe", "279");
		materials.put("mushroomstew", "282");
		materials.put("mushroomsoup", "282");
		materials.put("gunpowder", "289");
		materials.put("diamondhelm", "310");
		materials.put("diamondchest", "311");
		materials.put("diamondlegs", "312");
		materials.put("diamondboots", "313");
		materials.put("porkchop", "pork");
		materials.put("cookedpork", "320");
		materials.put("cookedporkchop", "320");
		materials.put("sugarcane", "338");
		materials.put("sugarcanes", "338");
		materials.put("reeds", "338");
		materials.put("slimeball", "slime ball");
		materials.put("melonslice", "melon");
		materials.put("steak", "cooked beef");
		materials.put("bottle", "glass bottle");
		materials.put("glisteringmelon", "speckled melon");
		materials.put("fireworks", "firework");
		materials.put("pie", "pumpkin pie");

		//-------//
		// Stone //
		//-------//
		str = "stone";
		String[] stonenames = {
				"granite", "polishedgranite", "pgranite",
				"diorite", "polisheddiorite", "pdiorite",
				"andesite", "polishedandesite", "pandesite"
		};
		setupAdd(stonenames, str);

		i = 1;
		mdata.put("granite", i);
		i = 2;
		mdata.put("polishedgranite", i);
		mdata.put("pgranite", i);
		i = 3;
		mdata.put("diorite", i);
		i = 4;
		mdata.put("polisheddiorite", i);
		mdata.put("pdiorite", i);
		i = 5;
		mdata.put("andesite", i);
		i = 6;
		mdata.put("polishedandesite", i);
		mdata.put("pandesite", i);

		//------//
		// Misc //
		//------//
		str = "dirt";
		materials.put("coarsedirt", str);
		materials.put("podzol", str);
		i = 1;
		mdata.put("coarsedirt", i);
		i = 2;
		mdata.put("podzol", i);

		str = "sand";
		materials.put("redsand", str);
		i = 1;
		mdata.put("redsand", i);
		
		str = "31";
		materials.put("grass", str);
		materials.put("fern", str);
		i = 1;
		mdata.put("grass", i);
		i = 2;
		mdata.put("fern", i);


		//------//
		// Dyes //
		//------//
		str = "ink_sack";
		String[] inksacknames = {
				"inksac", "ink", "blackdye", "rosered", "reddye",
				"cactusgreen", "greendye", "cocobeans", "cocoabeans", "browndye",
				"lapislazuli", "lapis", "bluedye",
				"purpledye", "cyandye", "lightgraydye", "lightgreydye",
				"graydye", "greydye", "pinkdye", "limedye",
				"dandelionyellow", "yellowdye",
				"lightbluedye", "magentadye", "orangedye",
				"bonemeal", "whitedye"
		};
		setupAdd(inksacknames, str);

		i = 1;
		mdata.put("rosered", i);
		mdata.put("reddye", i);
		i = 2;
		mdata.put("cactusgreen", i);
		mdata.put("greendye", i);
		i = 3;
		mdata.put("cocobeans", i);
		mdata.put("cocoabeans", i);
		mdata.put("browndye", i);
		i = 4;
		mdata.put("lapislazuli", i);
		mdata.put("lapis", i);
		mdata.put("bluedye", i);
		i = 5;
		mdata.put("purpledye", i);
		i = 6;
		mdata.put("cyandye", i);
		i = 7;
		mdata.put("lightgraydye", i);
		mdata.put("lightgreydye", i);
		i = 8;
		mdata.put("graydye", i);
		mdata.put("greydye", i);
		i = 9;
		mdata.put("pinkdye", i);
		i = 10;
		mdata.put("limedye", i);
		i = 11;
		mdata.put("dandelionyellow", i);
		mdata.put("yellowdye", i);
		i = 12;
		mdata.put("lightbluedye", i);
		i = 13;
		mdata.put("magentadye", i);
		i = 14;
		mdata.put("orangedye", i);
		i = 15;
		mdata.put("bonemeal", i);
		mdata.put("whitedye", i);

		//------//
		// Wood //
		//------//
		str = "log";
		String[] lognames = {
			"oak", "oakwood", "oaklog",
			"spruce", "sprucewood", "sprucelog",
			"birch", "birchwood", "birchlog",
			"jungle", "junglewood", "junglelog"
		};
		setupAdd(lognames, str);

		str ="log_2";
		String[] log2names = {
			"acacia", "acaciawood", "acacialog",
			"darkoak", "darkoakwood", "darkoaklog",
			"darkwood", "darklog", "doakwood", "doaklog"
		};
		setupAdd(log2names, str);

		i = 1;
		mdata.put("spruce", i);
		mdata.put("sprucewood", i);
		mdata.put("sprucelog", i);
		i = 2;
		mdata.put("birch", i);
		mdata.put("birchwood", i);
		mdata.put("birchlog", i);
		i = 3;
		mdata.put("jungle", i);
		mdata.put("junglewood", i);
		mdata.put("junglelog", i);	
		i = 1;
		mdata.put("darkoak", i);
		mdata.put("darkoakwood", i);
		mdata.put("darkoaklog", i);
		mdata.put("darkwood", i);
		mdata.put("darklog", i);
		mdata.put("doakwood", i);
		mdata.put("doaklog", i);

		//--------//
		// Planks //
		//--------//
		str = "planks";
		String[] planknames = {
			"oakplank", "oakplanks", "spruceplank", "spruceplanks",
			"birchplank", "birchplanks", "jungleplank", "jungleplanks",
			"acaciaplank", "acaciaplanks", "darkoakplank", "darkoakplanks",
			"doakplank", "doakplanks", "darkplank", "darkplanks"
		};
		setupAdd(planknames, str);

		i = 1;
		mdata.put("spruceplank", i);
		mdata.put("spruceplanks", i);
		i = 2;
		mdata.put("birchplank", i);
		mdata.put("birchplanks", i);
		i = 3;
		mdata.put("jungleplank", i);
		mdata.put("jungleplanks", i);
		i = 4;
		mdata.put("acaciaplank", i);
		mdata.put("acaciaplanks", i);
		i = 5;
		mdata.put("darkoakplank", i);
		mdata.put("darkoakplanks", i);
		mdata.put("doakplank", i);
		mdata.put("doakplanks", i);
		mdata.put("darkplank", i);
		mdata.put("darkplanks", i);

		//----------//
		// Saplings //
		//----------//
		str = "sapling";
		String[] saplingnames = {
			"oaksapling", "oaktree", "sprucesapling", "sprucetree",
			"birchsapling", "birchtree", "junglesapling", "jungletree",
			"acaciasapling", "acaciatree", "darkoaksapling", "darkoaktree",
			"doaksapling", "doaktree", "darksapling", "darktree"
		};
		setupAdd(saplingnames, str);

		i = 1;
		mdata.put("sprucesapling", i);
		mdata.put("sprucetree", i);
		i = 2;
		mdata.put("birchsapling", i);
		mdata.put("birchtree", i);
		i = 3;
		mdata.put("junglesapling", i);
		mdata.put("jungletree", i);
		i = 4;
		mdata.put("acaciasapling", i);
		mdata.put("acaciatree", i);
		i = 5;
		mdata.put("darkoaksapling", i);
		mdata.put("darkoaktree", i);
		mdata.put("doaksapling", i);
		mdata.put("doaktree", i);
		mdata.put("darksapling", i);
		mdata.put("darktree", i);

		//--------//
		// Leaves //
		//--------//
		str = "leaves";
		materials.put("oakleaves", str);
		materials.put("oleaves", str);
		materials.put("spruceleaves", str);
		materials.put("sleaves", str);
		materials.put("birchleaves", str);
		materials.put("bleaves", str);
		materials.put("jungleleaves", str);
		materials.put("jleaves", str);

		str ="leaves_2";
		materials.put("acacialeaves", str);
		materials.put("aleaves", str);
		materials.put("darkoakleaves", str);
		materials.put("doakleaves", str);
		materials.put("darkleaves", str);
		materials.put("dleaves", str);

		i = 1;
		mdata.put("spruceleaves", i);
		mdata.put("sleaves", i);
		i = 2;
		mdata.put("birchleaves", i);
		mdata.put("bleaves", i);
		i = 3;
		mdata.put("jungleleaves", i);
		mdata.put("jleaves", i);	
		i = 1;
		mdata.put("darkoakleaves", i);
		mdata.put("doakleaves", i);
		mdata.put("darkleaves", i);
		mdata.put("dleaves", i);
		
		//------//
		// Wool //
		//------//
		str = "wool";
		materials.put("whitewool", str);
		materials.put("orangewool", str);
		materials.put("magentawool", str);
		materials.put("lightbluewool", str);
		materials.put("ltbluewool", str);
		materials.put("yellowwool", str);
		materials.put("limewool", str);
		materials.put("pinkwool", str);
		materials.put("graywool", str);
		materials.put("greywool", str);
		materials.put("lightgraywool", str);
		materials.put("lightgreywool", str);
		materials.put("ltgraywool", str);
		materials.put("ltgreywool", str);
		materials.put("cyanwool", str);
		materials.put("purplewool", str);
		materials.put("bluewool", str);
		materials.put("brownwool", str);
		materials.put("greenwool", str);
		materials.put("redwool", str);
		materials.put("blackwool", str);
		
		i = 1;
		mdata.put("orangewool", i);
		i = 2;
		mdata.put("magentawool", i);
		i = 3;
		mdata.put("lightbluewool", i);
		mdata.put("ltbluewool", i);
		i = 4;
		mdata.put("yellowwool", i);
		i = 5;
		mdata.put("limewool", i);
		i = 6;
		mdata.put("pinkwool", i);
		i = 7;
		mdata.put("graywool", i);
		mdata.put("greywool", i);
		i = 8;
		mdata.put("lightgraywool", i);
		mdata.put("lightgreywool", i);
		mdata.put("ltgraywool", i);
		mdata.put("ltgreywool", i);
		i = 9;
		mdata.put("cyanwool", i);
		i = 10;
		mdata.put("purplewool", i);
		i = 11;
		mdata.put("bluewool", i);
		i = 12;
		mdata.put("brownwool", i);
		i = 13;
		mdata.put("greenwool", i);
		i = 14;
		mdata.put("redwool", i);
		i = 15;
		mdata.put("blackwool", i);
		
		//--------//
		// Carpet //
		//--------//
		str = "carpet";
		materials.put("whitecarpet", str);
		materials.put("orangecarpet", str);
		materials.put("magentacarpet", str);
		materials.put("lightbluecarpet", str);
		materials.put("ltbluecarpet", str);
		materials.put("yellowcarpet", str);
		materials.put("limecarpet", str);
		materials.put("pinkcarpet", str);
		materials.put("graycarpet", str);
		materials.put("greycarpet", str);
		materials.put("lightgraycarpet", str);
		materials.put("lightgreycarpet", str);
		materials.put("ltgraycarpet", str);
		materials.put("ltgreycarpet", str);
		materials.put("cyancarpet", str);
		materials.put("purplecarpet", str);
		materials.put("bluecarpet", str);
		materials.put("browncarpet", str);
		materials.put("greencarpet", str);
		materials.put("redcarpet", str);
		materials.put("blackcarpet", str);
		
		i = 1;
		mdata.put("orangecarpet", i);
		i = 2;
		mdata.put("magentacarpet", i);
		i = 3;
		mdata.put("lightbluecarpet", i);
		mdata.put("ltbluecarpet", i);
		i = 4;
		mdata.put("yellowcarpet", i);
		i = 5;
		mdata.put("limecarpet", i);
		i = 6;
		mdata.put("pinkcarpet", i);
		i = 7;
		mdata.put("graycarpet", i);
		mdata.put("greycarpet", i);
		i = 8;
		mdata.put("lightgraycarpet", i);
		mdata.put("lightgreycarpet", i);
		mdata.put("ltgraycarpet", i);
		mdata.put("ltgreycarpet", i);
		i = 9;
		mdata.put("cyancarpet", i);
		i = 10;
		mdata.put("purplecarpet", i);
		i = 11;
		mdata.put("bluecarpet", i);
		i = 12;
		mdata.put("browncarpet", i);
		i = 13;
		mdata.put("greencarpet", i);
		i = 14;
		mdata.put("redcarpet", i);
		i = 15;
		mdata.put("blackcarpet", i);
		
		//---------//
		// Flowers //
		//---------//
		materials.put("dandelion", "yellow flower");
		str = "38";
		materials.put("poppy", str);
		materials.put("blueorchid", str);
		materials.put("orchid", str);
		materials.put("allium", str);
		materials.put("azurebluet", str);
		materials.put("redtulip", str);
		materials.put("orangetulip", str);
		materials.put("whitetulip", str);
		materials.put("pinktulip", str);
		materials.put("oxeyedaisy", str);
		materials.put("daisy", str);
		
		i = 1;
		mdata.put("blueorchid", i);
		mdata.put("orchid", i);
		i = 2;
		mdata.put("allium", i);
		i = 3;
		mdata.put("azurebluet", i);
		i = 4;
		mdata.put("redtulip", i);
		i = 5;
		mdata.put("orangetulip", i);
		i = 6;
		mdata.put("whitetulip", i);
		i = 7;
		mdata.put("pinktulip", i);
		i = 8;
		mdata.put("oxeyedaisy", i);
		mdata.put("daisy", i);
		
		//------------------------//
		// Stained Glass (Blocks) //
		//------------------------//
		str = "stained glass";
		materials.put("whitestainedglass", str);
		materials.put("whiteglass", str);
		materials.put("orangestainedglass", str);
		materials.put("orangeglass", str);
		materials.put("magentastainedglass", str);
		materials.put("magentaglass", str);
		materials.put("lightbluestainedglass", str);
		materials.put("lightblueglass", str);
		materials.put("ltbluestainedglass", str);
		materials.put("ltblueglass", str);
		materials.put("yellowstainedglass", str);
		materials.put("yellowglass", str);
		materials.put("limestainedglass", str);
		materials.put("limeglass", str);
		materials.put("pinkstainedglass", str);
		materials.put("pinkglass", str);
		materials.put("graystainedglass", str);
		materials.put("grayglass", str);
		materials.put("greystainedglass", str);
		materials.put("greyglass", str);
		materials.put("lightgraystainedglass", str);
		materials.put("lightgrayglass", str);
		materials.put("lightgreystainedglass", str);
		materials.put("lightgreyglass", str);
		materials.put("ltgraystainedglass", str);
		materials.put("ltgrayglass", str);
		materials.put("ltgreystainedglass", str);
		materials.put("ltgreyglass", str);
		materials.put("cyanstainedglass", str);
		materials.put("cyanglass", str);
		materials.put("purplestainedglass", str);
		materials.put("purpleglass", str);
		materials.put("bluestainedglass", str);
		materials.put("blueglass", str);
		materials.put("brownstainedglass", str);
		materials.put("brownglass", str);
		materials.put("greenstainedglass", str);
		materials.put("greenglass", str);
		materials.put("redstainedglass", str);
		materials.put("redglass", str);
		materials.put("blackstainedglass", str);
		materials.put("blackglass", str);
		
		i = 1;
		mdata.put("orangestainedglass", i);
		mdata.put("orangeglass", i);
		i = 2;
		mdata.put("magentastainedglass", i);
		mdata.put("magentaglass", i);
		i = 3;
		mdata.put("lightbluestainedglass", i);
		mdata.put("lightblueglass", i);
		mdata.put("ltbluestainedglass", i);
		mdata.put("ltblueglass", i);
		i = 4;
		mdata.put("yellowstainedglass", i);
		mdata.put("yellowglass", i);
		i = 5;
		mdata.put("limestainedglass", i);
		mdata.put("limeglass", i);
		i = 6;
		mdata.put("pinkstainedglass", i);
		mdata.put("pinkglass", i);
		i = 7;
		mdata.put("graystainedglass", i);
		mdata.put("grayglass", i);
		mdata.put("greystainedglass", i);
		mdata.put("greyglass", i);
		i = 8;
		mdata.put("lightgraystainedglass", i);
		mdata.put("lightgrayglass", i);
		mdata.put("lightgreystainedglass", i);
		mdata.put("lightgreyglass", i);
		mdata.put("ltgraystainedglass", i);
		mdata.put("ltgrayglass", i);
		mdata.put("ltgreystainedglass", i);
		mdata.put("ltgreyglass", i);
		i = 9;
		mdata.put("cyanstainedglass", i);
		mdata.put("cyanglass", i);
		i = 10;
		mdata.put("purplestainedglass", i);
		mdata.put("purpleglass", i);
		i = 11;
		mdata.put("bluestainedglass", i);
		mdata.put("blueglass", i);
		i = 12;
		mdata.put("brownstainedglass", i);
		mdata.put("brownglass", i);
		i = 13;
		mdata.put("greenstainedglass", i);
		mdata.put("greenglass", i);
		i = 14;
		mdata.put("redstainedglass", i);
		mdata.put("redglass", i);
		i = 15;
		mdata.put("blackstainedglass", i);
		mdata.put("blackglass", i);
		
		//-----------------------//
		// Stained Glass (Panes) //
		//-----------------------//
		str = "stained glass pane";
		materials.put("whiteglasspane", str);
		materials.put("whitepane", str);
		materials.put("orangeglasspane", str);
		materials.put("orangepane", str);
		materials.put("magentaglasspane", str);
		materials.put("magentapane", str);
		materials.put("lightblueglasspane", str);
		materials.put("lightbluepane", str);
		materials.put("ltblueglasspane", str);
		materials.put("ltbluepane", str);
		materials.put("yellowglasspane", str);
		materials.put("yellowpane", str);
		materials.put("limeglasspane", str);
		materials.put("limepane", str);
		materials.put("pinkglasspane", str);
		materials.put("pinkpane", str);
		materials.put("grayglasspane", str);
		materials.put("graypane", str);
		materials.put("greyglasspane", str);
		materials.put("greypane", str);
		materials.put("lightgrayglasspane", str);
		materials.put("lightgraypane", str);
		materials.put("lightgreyglasspane", str);
		materials.put("lightgreypane", str);
		materials.put("ltgrayglasspane", str);
		materials.put("ltgraypane", str);
		materials.put("ltgreyglasspane", str);
		materials.put("ltgreypane", str);
		materials.put("cyanglasspane", str);
		materials.put("cyanpane", str);
		materials.put("purpleglasspane", str);
		materials.put("purplepane", str);
		materials.put("blueglasspane", str);
		materials.put("bluepane", str);
		materials.put("brownglasspane", str);
		materials.put("brownpane", str);
		materials.put("greenglasspane", str);
		materials.put("greenpane", str);
		materials.put("redglasspane", str);
		materials.put("redpane", str);
		materials.put("blackglasspane", str);
		materials.put("blackpane", str);
		
		i = 1;
		mdata.put("orangeglasspane", i);
		mdata.put("orangepane", i);
		i = 2;
		mdata.put("magentaglasspane", i);
		mdata.put("magentapane", i);
		i = 3;
		mdata.put("lightblueglasspane", i);
		mdata.put("lightbluepane", i);
		mdata.put("ltblueglasspane", i);
		mdata.put("ltbluepane", i);
		i = 4;
		mdata.put("yellowglasspane", i);
		mdata.put("yellowpane", i);
		i = 5;
		mdata.put("limeglasspane", i);
		mdata.put("limepane", i);
		i = 6;
		mdata.put("pinkglasspane", i);
		mdata.put("pinkpane", i);
		i = 7;
		mdata.put("grayglasspane", i);
		mdata.put("graypane", i);
		mdata.put("greyglasspane", i);
		mdata.put("greypane", i);
		i = 8;
		mdata.put("lightgrayglasspane", i);
		mdata.put("lightgraypane", i);
		mdata.put("lightgreyglasspane", i);
		mdata.put("lightgreypane", i);
		mdata.put("ltgrayglasspane", i);
		mdata.put("ltgraypane", i);
		mdata.put("ltgreyglasspane", i);
		mdata.put("ltgreypane", i);
		i = 9;
		mdata.put("cyanglasspane", i);
		mdata.put("cyanpane", i);
		i = 10;
		mdata.put("purpleglasspane", i);
		mdata.put("purplepane", i);
		i = 11;
		mdata.put("blueglasspane", i);
		mdata.put("bluepane", i);
		i = 12;
		mdata.put("brownglasspane", i);
		mdata.put("brownpane", i);
		i = 13;
		mdata.put("greenglasspane", i);
		mdata.put("greenpane", i);
		i = 14;
		mdata.put("redglasspane", i);
		mdata.put("redpane", i);
		i = 15;
		mdata.put("blackglasspane", i);
		mdata.put("blackpane", i);
		
		//--------------//
		// Stained Clay //
		//--------------//
		str = "159";
		materials.put("whitestainedclay", str);
		materials.put("whiteclay", str);
		materials.put("orangestainedclay", str);
		materials.put("orangeclay", str);
		materials.put("magentastainedclay", str);
		materials.put("magentaclay", str);
		materials.put("lightbluestainedclay", str);
		materials.put("lightblueclay", str);
		materials.put("ltbluestainedclay", str);
		materials.put("ltblueclay", str);
		materials.put("yellowstainedclay", str);
		materials.put("yellowclay", str);
		materials.put("limestainedclay", str);
		materials.put("limeclay", str);
		materials.put("pinkstainedclay", str);
		materials.put("pinkclay", str);
		materials.put("graystainedclay", str);
		materials.put("grayclay", str);
		materials.put("greystainedclay", str);
		materials.put("greyclay", str);
		materials.put("lightgraystainedclay", str);
		materials.put("lightgrayclay", str);
		materials.put("lightgreystainedclay", str);
		materials.put("lightgreyclay", str);
		materials.put("ltgraystainedclay", str);
		materials.put("ltgrayclay", str);
		materials.put("ltgreystainedclay", str);
		materials.put("ltgreyclay", str);
		materials.put("cyanstainedclay", str);
		materials.put("cyanclay", str);
		materials.put("purplestainedclay", str);
		materials.put("purpleclay", str);
		materials.put("bluestainedclay", str);
		materials.put("blueclay", str);
		materials.put("brownstainedclay", str);
		materials.put("brownclay", str);
		materials.put("greenstainedclay", str);
		materials.put("greenclay", str);
		materials.put("redstainedclay", str);
		materials.put("redclay", str);
		materials.put("blackstainedclay", str);
		materials.put("blackclay", str);
		
		i = 1;
		mdata.put("orangestainedclay", i);
		mdata.put("orangeclay", i);
		i = 2;
		mdata.put("magentastainedclay", i);
		mdata.put("magentaclay", i);
		i = 3;
		mdata.put("lightbluestainedclay", i);
		mdata.put("lightblueclay", i);
		mdata.put("ltbluestainedclay", i);
		mdata.put("ltblueclay", i);
		i = 4;
		mdata.put("yellowstainedclay", i);
		mdata.put("yellowclay", i);
		i = 5;
		mdata.put("limestainedclay", i);
		mdata.put("limeclay", i);
		i = 6;
		mdata.put("pinkstainedclay", i);
		mdata.put("pinkclay", i);
		i = 7;
		mdata.put("graystainedclay", i);
		mdata.put("grayclay", i);
		mdata.put("greystainedclay", i);
		mdata.put("greyclay", i);
		i = 8;
		mdata.put("lightgraystainedclay", i);
		mdata.put("lightgrayclay", i);
		mdata.put("lightgreystainedclay", i);
		mdata.put("lightgreyclay", i);
		mdata.put("ltgraystainedclay", i);
		mdata.put("ltgrayclay", i);
		mdata.put("ltgreystainedclay", i);
		mdata.put("ltgreyclay", i);
		i = 9;
		mdata.put("cyanstainedclay", i);
		mdata.put("cyanclay", i);
		i = 10;
		mdata.put("purplestainedclay", i);
		mdata.put("purpleclay", i);
		i = 11;
		mdata.put("bluestainedclay", i);
		mdata.put("blueclay", i);
		i = 12;
		mdata.put("brownstainedclay", i);
		mdata.put("brownclay", i);
		i = 13;
		mdata.put("greenstainedclay", i);
		mdata.put("greenclay", i);
		i = 14;
		mdata.put("redstainedclay", i);
		mdata.put("redclay", i);
		i = 15;
		mdata.put("blackstainedclay", i);
		mdata.put("blackclay", i);
	}
}
