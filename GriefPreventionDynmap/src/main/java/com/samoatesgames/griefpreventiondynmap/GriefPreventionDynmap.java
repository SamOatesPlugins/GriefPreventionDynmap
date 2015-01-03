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
     * The ID of the scheduler update task
     */
    private int m_updateTaskID = -1;
    
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
            this.logError("The dynmap plugin was not found on this server...");
            pluginManager.disablePlugin(this);
            return;
        }

        m_dynmapAPI = (DynmapAPI) dynmapPlugin;
        m_dynmapMarkerAPI = m_dynmapAPI.getMarkerAPI();

        Plugin griefPreventionPlugin = pluginManager.getPlugin("GriefPrevention");

        // GriefPrevention isn't installed, disble this plugin
        if (griefPreventionPlugin == null) {
            this.logError("The grief prevention plugin was not found on this server...");
            pluginManager.disablePlugin(this);
            return;
        }

        m_griefPreventionPlugin = (GriefPrevention) griefPreventionPlugin;

        // If either dynmap or grief prevention are disabled, disable this plugin
        if (!(dynmapPlugin.isEnabled() && griefPreventionPlugin.isEnabled())) {
            this.logError("Either dynmap or grief prevention is disabled...");
            pluginManager.disablePlugin(this);
            return;
        }
        
        if (!setupMarkerSet()) {
            this.logError("Failed to setup a marker set...");
            pluginManager.disablePlugin(this);
            return;
        }

        BukkitScheduler scheduler = getServer().getScheduler();
        m_updateTaskID = scheduler.scheduleSyncRepeatingTask(
                this,
                new Runnable() {
                    @Override
                    public void run() {
                        updateClaims();
                    }
                },
                20L,
                20L * this.getSetting(Setting.DynmapUpdateRate, 30)
        );

        this.logInfo("Succesfully enabled.");
    }

    /**
     * Register all configuration settings
     */
    public void setupConfigurationSettings() {
        
        this.registerSetting(Setting.ShowChildClaims, true);    // Should child claims be shown on the dynmap
        this.registerSetting(Setting.DynmapUpdateRate, 30);     // How many seconds should we wait before refreshing the dynmap layer
        
        this.registerSetting(Setting.ClaimsLayerName, "Claims");    // The name of the claims layer shown on dynmap
        this.registerSetting(Setting.ClaimsLayerPriority, 10);      // The render priority of the claims layer shown on dynmap
        this.registerSetting(Setting.ClaimsLayerHiddenByDefault, false);    // Should the claims layer be hidden by default on the dynmap
        
        this.registerSetting(Setting.MarkerLineColor, "FF0000");    // The color of the border of the marker (in hex)
        this.registerSetting(Setting.MarkerLineWeight, 2);          // The thickness of the border of the marker
        this.registerSetting(Setting.MarkerLineOpacity, 0.8);       // The alpha transparacy level of the border for the marker
        this.registerSetting(Setting.MarkerFillColor, "FF0000");    // THe fill color of the marker (in hex)
        this.registerSetting(Setting.MarkerFillOpacity, 0.35);      // The alpha transparacy level of the fill for the marker
    }
    
    /**
     * Called when the plugin is disabled
     */
    @Override
    public void onDisable() {
        
        if (m_updateTaskID != -1) {
            BukkitScheduler scheduler = getServer().getScheduler();
            scheduler.cancelTask(m_updateTaskID);
            m_updateTaskID = -1;
        }
        
        for (AreaMarker marker : m_claims.values()) {
            marker.deleteMarker();
        }
        m_claims.clear();

        m_griefPreventionMarkerSet.deleteMarkerSet();
    }

    /**
     * Setup the marker set
     */
    private boolean setupMarkerSet() {

        m_griefPreventionMarkerSet = m_dynmapMarkerAPI.getMarkerSet("griefprevention.markerset");

        final String layerName = this.getSetting(Setting.ClaimsLayerName, "Claims");
        if (m_griefPreventionMarkerSet == null) {
            m_griefPreventionMarkerSet = m_dynmapMarkerAPI.createMarkerSet("griefprevention.markerset", layerName, null, false);
        } else {
            m_griefPreventionMarkerSet.setMarkerSetLabel(layerName);
        }

        if (m_griefPreventionMarkerSet == null) {
            this.logError("Failed to create a marker set with the name 'griefprevention.markerset'.");
            return false;
        }

        m_griefPreventionMarkerSet.setLayerPriority(this.getSetting(Setting.ClaimsLayerPriority, 10));
        m_griefPreventionMarkerSet.setHideByDefault(this.getSetting(Setting.ClaimsLayerHiddenByDefault, false));

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
        } catch (NoSuchFieldException ex) {
            this.logException("Error reflecting claims member from gried prevention, the field 'claims' does not exist!", ex);
            return;
        } catch (SecurityException ex) {
            this.logException("Error reflecting claims member from gried prevention, you don't have permission to do this.", ex);
            return;
        } catch (IllegalArgumentException ex) {
            this.logException("Error reflecting claims member from gried prevention, the specified arguments are invalid.", ex);
            return;
        } catch (IllegalAccessException ex) {
            this.logException("Error reflecting claims member from gried prevention, you don't have permission to access the feild 'claims'.", ex);
            return;
        }

        // We have found claims! Create markers for them all
        if (claims != null) {
            for (Claim claim : claims) {
                createClaimMarker(claim, newClaims);
                if (claim.children != null && this.getSetting(Setting.ShowChildClaims, true)) {
                    for (Claim children : claim.children) {
                        createClaimMarker(children, newClaims);
                    }
                }
            }
        }
        
        // Remove any markers for claims which no longer exist
        for (AreaMarker oldm : m_claims.values()) {
            oldm.deleteMarker();
        }

        // And replace with new map
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
        setMarkerStyle(marker);

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
    private void setMarkerStyle(AreaMarker marker) {
        
        // Get the style settings
        int lineColor = 0xFF0000;
        int fillColor = 0xFF0000;
        
        try {
            lineColor = Integer.parseInt(this.getSetting(Setting.MarkerLineColor, "FF0000"), 16);
            fillColor = Integer.parseInt(this.getSetting(Setting.MarkerFillColor, "FF0000"), 16);
        }
        catch (Exception ex) {
            this.logException("Invalid syle color specified. Defaulting to red.", ex);
        }
        
        int lineWeight = this.getSetting(Setting.MarkerLineWeight, 2);
        double lineOpacity = this.getSetting(Setting.MarkerLineOpacity, 0.8);
        double fillOpacity = this.getSetting(Setting.MarkerFillOpacity, 0.35);
        
        // Set the style of the marker
        marker.setLineStyle(lineWeight, lineOpacity, lineColor);
        marker.setFillStyle(fillOpacity, fillColor);
    }

    /**
     * Setup the markers format window
     * @param claim The claim to setup the window for
     * @return Html representation of the information window
     */
    private String formatInfoWindow(Claim claim) {
        final String owner = claim.getOwnerName();
        return "<div class=\"regioninfo\">" + 
                    "<center>" + 
                        "<div class=\"infowindow\">"+ 
                            "<span style=\"font-weight:bold;\">" + owner + "'s claim</span><br/>" + 
                            "<img src='https://minotar.net/helm/" + owner + "/20' />" +
                        "</div>" + 
                    "</center>" +
                "</div>";
    }
}
