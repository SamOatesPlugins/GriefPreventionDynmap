package com.samoatesgames.griefpreventiondynmap;

import com.samoatesgames.samoatesplugincore.plugin.SamOatesPlugin;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.dynmap.DynmapAPI;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerSet;

/**
 * The main plugin class
 *
 * @author Sam Oates <sam@samoatesgames.com>
 */
public final class GriefPreventionDynmap extends SamOatesPlugin {

    /**
     * The dynmap api
     */
    private DynmapAPI m_dynmapAPI = null;

    /**
     * The dynmap marker api
     */
    private MarkerAPI m_dynmapMarkerAPI = null;

    /**
     * The grief prevention plugin
     */
    private GriefPrevention m_griefPreventionPlugin = null;

    /**
     * The marker set used for the grief prevention layer
     */
    private MarkerSet m_griefPreventionMarkerSet = null;

    /**
     * All claims
     */
    private Map<String, AreaMarker> m_claims = new HashMap<String, AreaMarker>();

    /**
     * Class constructor
     */
    public GriefPreventionDynmap() {
        super("GriefPreventionDynmap", "GriefPreventionDynmap", ChatColor.RED);
    }

    /**
     * Called when the plugin is enabled
     */
    @Override
    public void onEnable() {
        super.onEnable();

        PluginManager pluginManager = this.getServer().getPluginManager();
        Plugin dynmapPlugin = pluginManager.getPlugin("dynmap");

        // Dynmap isn't installed, disble this plugin
        if (dynmapPlugin == null) {
            this.LogError("The dynmap plugin was not found on this server...");
            pluginManager.disablePlugin(this);
            return;
        }

        m_dynmapAPI = (DynmapAPI) dynmapPlugin;
        m_dynmapMarkerAPI = m_dynmapAPI.getMarkerAPI();

        Plugin griefPreventionPlugin = pluginManager.getPlugin("GriefPrevention");

        // GriefPrevention isn't installed, disble this plugin
        if (griefPreventionPlugin == null) {
            this.LogError("The grief prevention plugin was not found on this server...");
            pluginManager.disablePlugin(this);
            return;
        }

        m_griefPreventionPlugin = (GriefPrevention) griefPreventionPlugin;

        // If either dynmap or grief prevention are disabled, disable this plugin
        if (!(dynmapPlugin.isEnabled() && griefPreventionPlugin.isEnabled())) {
            this.LogError("Either dynmap or grief prevention is disabled...");
            pluginManager.disablePlugin(this);
            return;
        }

        if (!setupMarkerSet()) {
            this.LogError("Failed to setup a marker set...");
            pluginManager.disablePlugin(this);
            return;
        }

        BukkitScheduler scheduler = getServer().getScheduler();
        scheduler.scheduleSyncRepeatingTask(
                this,
                new Runnable() {
                    @Override
                    public void run() {
                        updateClaims();
                    }
                },
                20L,
                500L // Update every 30 seconds  
        );

        this.LogInfo("Succesfully enabled.");
    }

    /**
     * Called when the plugin is disabled
     */
    @Override
    public void onDisable() {
        m_claims.clear();
    }

    /**
     * Setup the marker set
     */
    private boolean setupMarkerSet() {

        m_griefPreventionMarkerSet = m_dynmapMarkerAPI.getMarkerSet("griefprevention.markerset");

        if (m_griefPreventionMarkerSet == null) {
            m_griefPreventionMarkerSet = m_dynmapMarkerAPI.createMarkerSet("griefprevention.markerset", "Claims", null, false);
        } else {
            m_griefPreventionMarkerSet.setMarkerSetLabel("GriefPrevention");
        }

        if (m_griefPreventionMarkerSet == null) {
            this.LogError("Failed to create a marker set with the name 'griefprevention.markerset'.");
            return false;
        }

        m_griefPreventionMarkerSet.setLayerPriority(10);
        m_griefPreventionMarkerSet.setHideByDefault(false);

        return true;
    }

    /**
     * Update all claims
     */
    private void updateClaims() {

        Map<String, AreaMarker> newClaims = new HashMap<String, AreaMarker>();

        ArrayList<Claim> claims = null;
        try {
            Field fld = DataStore.class.getDeclaredField("claims");
            fld.setAccessible(true);
            Object o = fld.get(m_griefPreventionPlugin.dataStore);
            if (o instanceof ArrayList) {
                claims = (ArrayList<Claim>) o;
            }
        } catch (Exception e) {
            return;
        }

        /* If claims, process them */
        if (claims != null) {
            for (Claim claim : claims) {
                createClaimMarker(claim, newClaims);
                if ((claim.children != null) && (claim.children.size() > 0)) {
                    for (Claim children : claim.children) {
                        createClaimMarker(children, newClaims);
                    }
                }
            }
        }
        /* Now, review old map - anything left is gone */
        for (AreaMarker oldm : m_claims.values()) {
            oldm.deleteMarker();
        }

        /* And replace with new map */
        m_claims.clear();
        m_claims = newClaims;
    }

    /**
     * Create a new claim marker
     * @param claim    The claim to create a marker for
     * @param claimsMap    The map of new claims
     */
    private void createClaimMarker(Claim claim, Map<String, AreaMarker> claimsMap) {

        Location lowerBounds = claim.getLesserBoundaryCorner();
        Location higherBounds = claim.getGreaterBoundaryCorner();
        if (lowerBounds == null || higherBounds == null) {
            return;
        }

        String worldname = lowerBounds.getWorld().getName();
        String owner = claim.getOwnerName();

        // Make outline
        double[] x = new double[4];
        double[] z = new double[4];
        x[0] = lowerBounds.getX();
        z[0] = lowerBounds.getZ();
        x[1] = lowerBounds.getX();
        z[1] = higherBounds.getZ() + 1.0;
        x[2] = higherBounds.getX() + 1.0;
        z[2] = higherBounds.getZ() + 1.0;
        x[3] = higherBounds.getX() + 1.0;
        z[3] = lowerBounds.getZ();
        
        final String markerid = "Claim_" + claim.getID();
        AreaMarker marker = m_claims.remove(markerid);
        if (marker == null) {
            marker = m_griefPreventionMarkerSet.createAreaMarker(markerid, owner, false, worldname, x, z, false);
            if (marker == null) {
                return;
            }
        } else {
            marker.setCornerLocations(x, z);
            marker.setLabel(owner);
        }

        // Set line and fill properties
        addStyle(marker);

        // Build popup
        String desc = formatInfoWindow(claim);
        marker.setDescription(desc);

        // Add to map
        claimsMap.put(markerid, marker);
    }

    /**
     * Setup the markers styling
     * @param marker 
     */
    private void addStyle(AreaMarker marker) {
        int sc = 0xFF0000;
        int fc = 0xFF0000;
        marker.setLineStyle(3, 0.8, sc);
        marker.setFillStyle(0.35, fc);
    }

    /**
     * Setup the markers format window
     * @param claim The claim to setup the window for
     * @return Html representation of the information window
     */
    private String formatInfoWindow(Claim claim) {
        return "<div class=\"regioninfo\">" + 
                "<div class=\"infowindow\"><span style=\"font-weight:bold;\">" +
                claim.getOwnerName() + 
                "'s claim</span><br/></div>" + 
                "</div>";
    }
}
