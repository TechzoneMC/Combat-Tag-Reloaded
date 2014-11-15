package com.trc202.CombatTag;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import net.gravitydevelopment.updater.Updater;
import net.gravitydevelopment.updater.Updater.UpdateType;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.StringUtil;
import org.mcstats.Metrics;

import com.google.common.collect.ImmutableList;
import com.trc202.CombatTagListeners.CombatTagCommandPrevention;
import com.trc202.CombatTagListeners.NoPvpBlockListener;
import com.trc202.CombatTagListeners.NoPvpEntityListener;
import com.trc202.CombatTagListeners.NoPvpPlayerListener;
import com.trc202.settings.Settings;
import com.trc202.settings.SettingsHelper;
import com.trc202.settings.SettingsLoader;

import techcable.minecraft.combattag.PluginCompatibility;
import techcable.minecraft.combattag.Utils;

public class CombatTag extends JavaPlugin {

	private final SettingsHelper settingsHelper;
	private final File settingsFile;
	public Settings settings;
	public static final Logger log = Logger.getLogger("Minecraft");
	private HashMap<UUID, Long> tagged;
	private static final String mainDirectory = "plugins/CombatTag";
	private static final List<String> SUBCOMMANDS = ImmutableList.of("reload", "wipe", "command");
	private static final List<String> COMMAND_SUBCOMMANDS = ImmutableList.of("add", "remove");

	// public final CombatTagIncompatibles ctIncompatible = new CombatTagIncompatibles(this);
	private final NoPvpPlayerListener plrListener = new NoPvpPlayerListener(this);
	public final NoPvpEntityListener entityListener = new NoPvpEntityListener(this);
	private final NoPvpBlockListener blockListener = new NoPvpBlockListener(this);
	private final CombatTagCommandPrevention commandPreventer = new CombatTagCommandPrevention(this);

	private int npcNumber;

	public CombatTag() {
		settings = new Settings();
		new File(mainDirectory).mkdirs();
		settingsFile = new File(mainDirectory + File.separator + "settings.prop");
		settingsHelper = new SettingsHelper(settingsFile, "CombatTag");
		npcNumber = 0;
	}

	/**
	 * Change SL in NPCManager to:
	 *
	 * private class SL implements Listener {
	 *
	 * @SuppressWarnings("unused") public void disableNPCLib() { despawnAll(); Bukkit.getServer().getScheduler().cancelTask(taskid); } }
	 */
	/**
	 * Change NullSocket to:
	 *
	 * class NullSocket extends Socket { private final byte[] buffer = new byte[50];
	 *
	 * @Override public InputStream getInputStream() { return new ByteArrayInputStream(this.buffer); }
	 *
	 * @Override public OutputStream getOutputStream() { return new ByteArrayOutputStream(10); } }
	 */
	@Override
	public void onDisable() {
		Utils.getInstakillHooks().onDisable();
		Utils.getNPCHooks().onDisable();
		disableMetrics();
		// Just in case...
		log.info("[CombatTag] Disabled");
	}

	@Override
	public void onEnable() {
		PluginCompatibility.registerListeners();
		Utils.getInstakillHooks().init();
		Utils.getNPCHooks().init();
		tagged = new HashMap<UUID, Long>();
		settings = new SettingsLoader().loadSettings(settingsHelper, this.getDescription().getVersion());
		PluginManager pm = getServer().getPluginManager();
		// ctIncompatible.startup(pm);
		if (!initMetrics()) {
			log.warning("Unable to initialize metrics");
		} else {
			if (isDebugEnabled()) log.info("Enabled Metrics");
		}
		pm.registerEvents(plrListener, this);
		pm.registerEvents(entityListener, this);
		pm.registerEvents(commandPreventer, this);
		pm.registerEvents(blockListener, this);

		log.info("[" + getDescription().getName() + "]" + " has loaded with a tag time of " + settings.getTagDuration() + " seconds");
	}

	public long getRemainingTagTime(UUID uuid) {
		if (tagged.get(uuid) == null) {
			return -1;
		}
		return (tagged.get(uuid) - System.currentTimeMillis());
	}

	public boolean addTagged(Player player) {
		if (player.isOnline()) {
			tagged.remove(player.getUniqueId());
			tagged.put(player.getUniqueId(), PvPTimeout(getTagDuration()));
			return true;
		}
		return false;
	}

	public boolean inTagged(UUID name) {
		return tagged.containsKey(name);
	}

	public long removeTagged(UUID name) {
		if (inTagged(name)) {
			return tagged.remove(name);
		}
		return -1;
	}

	public long PvPTimeout(int seconds) {
		return System.currentTimeMillis() + (seconds * 1000);
	}

	public boolean isInCombat(UUID uuid) {
		if (getRemainingTagTime(uuid) < 0) {
			tagged.remove(uuid);
			return false;
		} else {
			return true;
		}
	}

	public String getNpcName(String plr) {
		String npcName = settings.getNpcName();
		if (!(npcName.contains("player") || npcName.contains("number"))) {
			npcName = npcName + getNpcNumber();
		}
		if (npcName.contains("player")) {
			npcName = npcName.replace("player", plr);
		}
		if (npcName.contains("number")) {
			npcName = npcName.replace("number", "" + getNpcNumber());
		}
		return npcName;
	}

	/**
	 *
	 * @return the system tag duration as set by the user
	 */
	public int getTagDuration() {
		return settings.getTagDuration();
	}

	public boolean isDebugEnabled() {
		return settings.isDebugEnabled();
	}

	public void emptyInventory(Player target) {
		PlayerInventory targetInv = target.getInventory();
		targetInv.clear();
		if (isDebugEnabled()) {
			log.info("[CombatTag] " + target.getName() + " has been killed by Combat Tag and their inventory has been emptied through UpdatePlayerData.");
		}
	}

	public int getNpcNumber() {
		npcNumber = npcNumber + 1;
		return npcNumber;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String commandLabel, String[] args) {
		if (command.getName().equalsIgnoreCase("ct") || (command.getName().equalsIgnoreCase("combattag"))) {
			if (args.length == 0) {
				if (sender instanceof Player) {
					if (isInCombat(((Player) sender).getUniqueId())) {
						String message = settings.getCommandMessageTagged();
						message = message.replace("[time]", "" + (getRemainingTagTime(((Player) sender).getUniqueId()) / 1000));
						sender.sendMessage(message);
					} else {
						tagged.remove(((Player) sender).getUniqueId());
						sender.sendMessage(settings.getCommandMessageNotTagged());
					}
				} else {
					log.info("[CombatTag] /ct can only be used by a player!");
				}
				return true;
			} else if (args[0].equalsIgnoreCase("reload")) {
				if (sender.hasPermission("combattag.reload")) {
					settings = new SettingsLoader().loadSettings(settingsHelper, this.getDescription().getVersion());
					if (sender instanceof Player) {
						sender.sendMessage(ChatColor.RED + "[CombatTag] Settings were reloaded!");
					} else {
						log.info("[CombatTag] Settings were reloaded!");
					}
				} else {
					if (sender instanceof Player) {
						sender.sendMessage(ChatColor.RED + "[CombatTag] You don't have the permission 'combattag.reload'!");
					}
				}
				return true;
			} else if (args[0].equalsIgnoreCase("wipe")) {
				if (sender.hasPermission("combattag.wipe")) {
					int numNPC = Utils.getNPCHooks().wipeAll();
					sender.sendMessage("[CombatTag] Wiped " + numNPC + " pvploggers!");
				}
				return true;
			} else if (args[0].equalsIgnoreCase("command")) {
				if (sender.hasPermission("combattag.command")) {
					if (args.length > 2) {
						if (args[1].equalsIgnoreCase("add")) {
							if (args[2].length() == 0 || !args[2].startsWith("/")) {
								sender.sendMessage(ChatColor.RED + "[CombatTag] Correct Usage: /ct command add /<command>");
							} else {
								String disabledCommands = settingsHelper.getProperty("disabledCommands");
								if (!disabledCommands.contains(args[2])) {
									disabledCommands = disabledCommands.substring(0, disabledCommands.length() - 1) + "," + args[2] + "]";
									disabledCommands = disabledCommands.replace("[,", "[");
									disabledCommands = disabledCommands.replaceAll(",,", ",");
									settingsHelper.setProperty("disabledCommands", disabledCommands);
									settingsHelper.saveConfig();
									sender.sendMessage(ChatColor.RED + "[CombatTag] Added " + args[2] + " to combat blocked commands.");
									settings = new SettingsLoader().loadSettings(settingsHelper, this.getDescription().getVersion());
								} else {
									sender.sendMessage(ChatColor.RED + "[CombatTag] That command is already in the blocked commands list.");
								}
							}
						} else if (args[1].equalsIgnoreCase("remove")) {
							if (args[2].length() == 0 || !args[2].startsWith("/")) {
								sender.sendMessage(ChatColor.RED + "[CombatTag] Correct Usage: /ct command remove /<command>");
							} else {
								String disabledCommands = settingsHelper.getProperty("disabledCommands");
								if (disabledCommands.contains(args[2] + ",") || disabledCommands.contains(args[2] + "]")) {
									disabledCommands = disabledCommands.replace(args[2] + ",", "");
									disabledCommands = disabledCommands.replace(args[2] + "]", "]");
									disabledCommands = disabledCommands.replace(",]", "]");
									disabledCommands = disabledCommands.replaceAll(",,", ",");
									settingsHelper.setProperty("disabledCommands", disabledCommands);
									settingsHelper.saveConfig();
									sender.sendMessage(ChatColor.RED + "[CombatTag] Removed " + args[2] + " from combat blocked commands.");
									settings = new SettingsLoader().loadSettings(settingsHelper, this.getDescription().getVersion());
								} else {
									sender.sendMessage(ChatColor.RED + "[CombatTag] That command is not in the blocked commands list.");
								}
							}
						}
					} else {
						sender.sendMessage(ChatColor.RED + "[CombatTag] Correct Usage: /ct command <add/remove> /<command>");
					}
				}
				return true;
			} else {
				sender.sendMessage(ChatColor.RED + "[CombatTag] That is not a valid command!");
				return true;
			}
		}
		return false;
	}

	@Override
	public final List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		if (args.length == 1) {
			return StringUtil.copyPartialMatches(args[0], SUBCOMMANDS, new ArrayList<String>(SUBCOMMANDS.size()));
		} else if (args.length == 2) {
			System.out.println(args[1]);
			if (args[0].equalsIgnoreCase("command")) {
				return StringUtil.copyPartialMatches(args[1], COMMAND_SUBCOMMANDS, new ArrayList<String>(COMMAND_SUBCOMMANDS.size()));
			}
		}
		return ImmutableList.of();
	}

	public double healthCheck(double health) {
		if (health < 0) {
			health = 0;
		}
		if (health > 20) {
			health = 20;
		}
		return health;
	}

	public SettingsHelper getSettingsHelper() {
		return this.settingsHelper;
	}

	private Metrics metrics;

	public boolean initMetrics() {
		try {
			if (metrics == null) {
				metrics = new Metrics(this);
			}
			metrics.start();
			return true;
		} catch (IOException ex) {
			return false;
		}
	}

	public void disableMetrics() {

	}

	private static final int projectId = 86389;
	
	public void updateProject() {
		@SuppressWarnings("unused")
		Updater updater = new Updater(this, CombatTag.projectId, this.getFile(), UpdateType.DEFAULT, true);
	}
}
