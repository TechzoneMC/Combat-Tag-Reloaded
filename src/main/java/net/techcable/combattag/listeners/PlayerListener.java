package net.techcable.combattag.listeners;

import net.techcable.combattag.CombatPlayer;
import techcable.minecraft.combattag.CombatTagAPI;
import net.techcable.combattag.Utils;
import net.techcable.combattag.event.CombatLogEvent;
import net.techcable.combattag.event.CombatTagEvent;
import net.techcable.combattag.event.CombatTagEvent.TagCause;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import static net.techcable.combattag.Utils.getPlugin;

public class PlayerListener implements Listener {

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onDamage(EntityDamageByEntityEvent event) {
    	LivingEntity attacker = Utils.getRootDamager(event.getDamager());
    	LivingEntity defender = (LivingEntity) event.getEntity();
    	if (CombatTagAPI.isNPC(defender) || CombatTagAPI.isNPC(attacker)) return;
    	if (attacker instanceof Player) onAttack(CombatPlayer.getPlayer((Player)attacker), defender);
    	if (defender instanceof Player) onDefend(CombatPlayer.getPlayer((Player)defender), attacker);
    }
    
    public void onAttack(CombatPlayer attacker, LivingEntity defender) {
    	if (!(defender instanceof Player) && !Utils.getPlugin().getSettings().isTagMobs()) return;
    	if (attacker.isTagged()) return;
    	CombatTagEvent event = new CombatTagEvent(attacker, defender, TagCause.ATTACK);
    	Utils.fire(event);
    	if (event.isCancelled()) return;
    	attacker.tag();
    }
    
    public void onDefend(CombatPlayer defender, LivingEntity attacker) {
    	if (!(attacker instanceof Player) && !Utils.getPlugin().getSettings().isTagMobs()) return;
    	if (defender.isTagged()) return;
    	CombatTagEvent event = new CombatTagEvent(defender, attacker, TagCause.DEFEND);
    	Utils.fire(event);
    	if (event.isCancelled()) return;
    	defender.tag();
    }
    
    @EventHandler(priority=EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
    	if (CombatTagAPI.isNPC(event.getPlayer())) return; //Don't combat log npcs
    	CombatPlayer player = getPlugin().getPlayer(event.getPlayer().getUniqueId());
    	if (player.isTagged()) {
    		Utils.fire(new CombatLogEvent(player));
    	}
    }
    
    @EventHandler(priority=EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
    	if (CombatTagAPI.isNPC(event.getEntity())) return;
    	CombatPlayer player = CombatPlayer.getPlayer(event.getEntity());
    	player.untag();
    }
}
