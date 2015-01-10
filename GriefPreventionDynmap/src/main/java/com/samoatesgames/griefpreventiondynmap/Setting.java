/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.samoatesgames.griefpreventiondynmap;

/**
 *
 * @author Sam Oates <sam@samoatesgames.com>
 */
public class Setting {
    
    // Marker Settings
    public static final String DynmapUpdateRate = "marker.refreshRateInSeconds";
    public static final String ShowChildClaims = "marker.claim.showchildren";
    
    // Marker Style Settings
    public static final String MarkerLineColor = "marker.style.border.color";
    public static final String MarkerLineWeight = "marker.style.border.weight";
    public static final String MarkerLineOpacity = "marker.style.border.opacity";
    public static final String MarkerFillColor = "marker.style.fill.color";
    public static final String MarkerFillOpacity = "marker.style.fill.opacity";
    
    // Admin Marker Style Settings
    public static final String AdminMarkerLineColor = "marker.admin.style.border.color";
    public static final String AdminMarkerLineWeight = "marker.admin.style.border.weight";
    public static final String AdminMarkerLineOpacity = "marker.admin.style.border.opacity";
    public static final String AdminMarkerFillColor = "marker.admin.style.fill.color";
    public static final String AdminMarkerFillOpacity = "marker.admin.style.fill.opacity";
    
    // Layer Settings
    public static final String ClaimsLayerName = "layer.name";
    public static final String ClaimsLayerPriority = "layer.priority";
    public static final String ClaimsLayerHiddenByDefault = "layer.hiddenByDefault";
}
