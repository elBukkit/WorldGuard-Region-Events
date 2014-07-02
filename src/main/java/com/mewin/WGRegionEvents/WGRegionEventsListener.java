package com.mewin.WGRegionEvents;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import com.mewin.WGRegionEvents.events.RegionEnterEvent;
import com.mewin.WGRegionEvents.events.RegionEnteredEvent;
import com.mewin.WGRegionEvents.events.RegionLeaveEvent;
import com.mewin.WGRegionEvents.events.RegionLeftEvent;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

/**
 * 
 * @author mewin
 */
public class WGRegionEventsListener implements Listener {
	private WorldGuardPlugin wgPlugin;
	private WGRegionEventsPlugin plugin;

	private static final HashMap<UUID, ApplicableRegionSet> cache = new HashMap<UUID, ApplicableRegionSet>();

	public WGRegionEventsListener(WGRegionEventsPlugin plugin, WorldGuardPlugin wgPlugin) {
		this.plugin = plugin;
		this.wgPlugin = wgPlugin;

	}

	protected void onPlayerLeave(Player player) {
		Location location = player.getLocation();
		ApplicableRegionSet set = cache.remove(player.getUniqueId());

		for (ProtectedRegion region : set) {
			RegionLeaveEvent leaveEvent = new RegionLeaveEvent(region, player, MovementWay.DISCONNECT, location);
			plugin.getServer().getPluginManager().callEvent(leaveEvent);
		}
	}

	@EventHandler
	public void onPlayerKick(PlayerKickEvent e) {
		onPlayerLeave(e.getPlayer());
	}

	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent e) {
		onPlayerLeave(e.getPlayer());
	}

	@EventHandler
	public void onPlayerMove(PlayerMoveEvent e) {
		long a = System.nanoTime();
		Location from = e.getFrom();
		Location to = e.getTo();
		if (from.getWorld() != to.getWorld() && from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to	.getBlockZ()) {
			e.setCancelled(updateRegions(e.getPlayer(), MovementWay.MOVE, to, from));
		}
		long b = System.nanoTime();
		System.out.println("Done! (" + (b - a) + "ns)");
	}

	@EventHandler
	public void onPlayerTeleport(PlayerTeleportEvent e) {
		e.setCancelled(updateRegions(e.getPlayer(), MovementWay.TELEPORT, e.getTo(), e.getFrom()));
	}

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent e) {
		updateRegions(e.getPlayer(), MovementWay.SPAWN, e.getPlayer().getLocation(), null);
	}

	@EventHandler
	public void onPlayerRespawn(PlayerRespawnEvent e) {
		updateRegions(e.getPlayer(), MovementWay.SPAWN, e.getRespawnLocation(), null);
	}

	private boolean updateRegions(Player player, MovementWay movement, Location to, Location from) {
		boolean ret = false;
		if (from != null && to != null) {
			ApplicableRegionSet toset = wgPlugin.getRegionManager(to.getWorld()).getApplicableRegions(to);
			ApplicableRegionSet fromset = cache.get(player.getUniqueId());
			if (fromset != null && fromset.size() != toset.size()) {
				Collection<ProtectedRegion> one = getApplicable(fromset);
				Collection<ProtectedRegion> two = getApplicable(toset);
				one.removeAll(getApplicable(toset));
				two.removeAll(getApplicable(fromset));
				if (!one.isEmpty()) {
					for (ProtectedRegion region : one) {
						RegionLeaveEvent e = new RegionLeaveEvent(region, player, movement, from);
						Bukkit.getPluginManager().callEvent(e);
						if (e.isCancelled()) {
							ret = true;
						}
						RegionLeftEvent e1 = new RegionLeftEvent(region, player, movement, from);
						Bukkit.getPluginManager().callEvent(e1);
					}
				}
				if (!two.isEmpty()) {
					for (ProtectedRegion region : two) {
						RegionEnterEvent e = new RegionEnterEvent(region, player, movement, from);
						Bukkit.getPluginManager().callEvent(e);
						if (e.isCancelled()) {
							ret = true;
						}
						RegionEnteredEvent e1 = new RegionEnteredEvent(region, player, movement, from);
						Bukkit.getPluginManager().callEvent(e1);
					}
				}
			}
			cache.put(player.getUniqueId(), toset);
		}
		return ret;
	}

	public Collection<ProtectedRegion> getApplicable(ApplicableRegionSet set) {
		try {
			Field f = set.getClass().getDeclaredField("applicable");
			f.setAccessible(true);
			Collection<ProtectedRegion> region = (Collection<ProtectedRegion>) f.get(set);
			return region;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
}
