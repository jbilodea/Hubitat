/*  Hyundai Bluelink Driver
 *
 *  Device Type:    Custom
 *  Author:         Tim Yuhl & Jean Bilodeau for Canadian version
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation
 *  files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy,
 *  modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the
 *  Software is furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
 *  WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 *  COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 *  ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 *  History:
 *  8/29/22 - Initial work.
 *  11/27/22 - Initial Canadian only version
 *  01/09/23 - Add command ForceRefresh to refresh the data bypassing the cache
 *  01/14/24 - Add Battery Level
 *  12/29/25 - Minor improvements for Cloudflare compatibility (v1.2.2)
 */

String appVersion()   { return "1.2.8" }
def setVersion(){
    state.name = "Hyundai Bluelink Driver"
    state.version = "1.2.8"
}

metadata {
    definition(
            name: "Canadian Hyundai Bluelink Driver",
            namespace: "jbilodea",
            description: "Driver for accessing Canadian Hyundai Bluelink web services",
            importUrl: "https://github.com/jbilodea/Hubitat/blob/main/Canadian_Hyundai_Bluelink/Drivers/BluelinkDriver.groovy",
            author: "Jean Bilodeau")
            {
                capability "Initialize"
                capability "Actuator"
                capability "Sensor"
                capability "Refresh"
                
                attribute "Fav1-Name", "string"
                attribute "Fav1Defrost", "string"
                attribute "Fav1Temp", "string"
                attribute "Fav1Heating", "string"
                attribute "Fav2-Name", "string"
                attribute "Fav2Defrost", "string"
                attribute "Fav2Temp", "string"
                attribute "Fav2Heating", "string"
                attribute "Fav3-Name", "string"
                attribute "Fav3Defrost", "string"
                attribute "Fav3Temp", "string"
                attribute "Fav3Heating", "string"
                attribute "NickName", "string"
                attribute "VIN", "string"
                attribute "modelCode", "string"
                attribute "modelName", "string"
                attribute "modelYear", "string"
                attribute "Odometer", "string"
                attribute "fuelKindCode", "string"
                attribute "trim", "string"
                attribute "exteriorColor", "string"
                attribute "subscriptionStatus", "string"
                attribute "subscriptionEndDate", "string"
                attribute "mileageForNextService", "string"
                attribute "daysForNextService", "string"
                attribute "overviewMessage", "string"
                attribute "valetParkingModeOn", "string"
                attribute "Engine", "string"
                attribute "DoorLocks", "string"
                attribute "Trunk", "string"
                attribute "BatteryInCharge", "string"
                attribute "BatteryPercent", "string"
                attribute "BatteryTimeToCharge", "string"
                attribute "BatteryLevel", "string"
                attribute "ACLevel", "number"
                attribute "ACRange", "number"
                attribute "DCLevel", "number"
                attribute "DCRange", "number"
                attribute "LastRefreshTime", "string"
                attribute "locLatitude", "string"
                attribute "locLongitude", "string"
                attribute "locAltitude", "string"
                attribute "locSpeed", "string"
                attribute "locUpdateTime", "string"
                
                command "Lock"
                command "Unlock"
                command "Location"
                command "SetChargeLevel", [[name: "ACLevel", type:"ENUM", description: "AC Level", constraints: [50,60,70,80,90,100]],[name: "DCLevel", type:"ENUM", description: "DC Level", constraints: [50,60,70,80,90,100]]]
                command "ClimStop"
                command "ClimFavoritesStart", [[name: "FavoriteNbr", type: "ENUM", description: "Favorite to execute, " +
                                                                                                "FavDefrost is for Front Windshield Defrost," +                                                                                                
                                                                                                "FavHeating is for Heated features like the steering wheel and rear window ", 
                                                                                   constraints: ["1", "2", "3"]] ]
                command "ClimManual", [[name: "Temperature", type:"ENUM", description: "Climate Temperature", constraints: [17,17.5,18,18.5,19,19.5,20,20.5,21,21.5,22,22.5,23,23.5,24,24.5,25,25.5,26,26.5,27]],[name: "Front Defrost", type:"ENUM", description: "Front Defrost", constraints: [false,true]],[name: "Heating", type:"ENUM", description: "Rear Defrost/Steering", constraints: [false, true]]]
                command "StartCharge"
                command "StopCharge"
                command "ForceRefresh"
            }

    preferences {
        section("Driver Options") {
            input("fullRefresh", "bool",
                    title: "Full refresh - Turn on this option to directly query the vehicle for status instead of using the vehicle's cached status. Warning: Turning on this option will result in status refreshes that can take as long as 2 minutes.",
                    defaultValue: false)
            input("autoRefreshInterval", "enum",
                    title: "Auto-refresh interval (optional)",
                    description: "Automatically refresh vehicle status at this interval",
                    required: false,
                    defaultValue: "0",
                    options: ["0": "Disabled", "15": "Every 15 minutes", "30": "Every 30 minutes", "60": "Every hour"])
            }
            section("Logging") {
                input "logging", "enum", title: "Log Level", required: false, defaultValue: "INFO", options: ["TRACE", "DEBUG", "INFO", "WARN", "ERROR"]
            }
    }
}

/**
 * Boilerplate callback methods called by the framework
 */

void installed()
{
    log("installed() called", "trace")
    setVersion()
    fullRefresh = false
    scheduleAutoRefresh()
}

void updated()
{
    log("updated() called", "trace")
    initialize()
}

void parse(String message)
{
    log("parse called with message: ${message}", "trace")
}

/* End of built-in callbacks */

///
// Commands
///
void initialize() {
    log("initialize() called", "trace")
    scheduleAutoRefresh()
    refresh()
}

void refresh()
{
    log("refresh called", "trace")
    
    // Protection: éviter les refresh trop fréquents (rate limiting)
    def lastRefresh = state.lastRefreshTime ?: 0
    def currentTime = now()
    def timeSinceLastRefresh = (currentTime - lastRefresh) / 1000 // en secondes
    
    if (timeSinceLastRefresh < 30) {
        log("Refresh called too soon (${timeSinceLastRefresh}s ago). Please wait at least 30 seconds between refreshes to avoid rate limiting.", "warn")
        return
    }
    
    state.lastRefreshTime = currentTime
    parent.getVehicleStatus(device, fullRefresh, false)
    updateHtml()
}

void ForceRefresh()
{
    log("ForceRefresh called", "trace")
    
    // Protection: éviter les force refresh trop fréquents (rate limiting plus strict)
    def lastForceRefresh = state.lastForceRefreshTime ?: 0
    def currentTime = now()
    def timeSinceLastForce = (currentTime - lastForceRefresh) / 1000 / 60 // en minutes
    
    if (timeSinceLastForce < 10) {
        log("ForceRefresh called too soon (${Math.round(timeSinceLastForce)} minutes ago). Please wait at least 10 minutes between force refreshes to avoid rate limiting.", "warn")
        return
    }
    
    state.lastForceRefreshTime = currentTime
    parent.getVehicleStatus(device, true, false) // Force refresh = true
    updateHtml()
}

void Lock()
{
    log("Lock called", "trace")
    parent.Lock(device)
    unschedule(refresh) // Unschedule only refresh, not auto-refresh
    runIn(45, "refresh")
}

void Unlock()
{
    log("Unlock called", "trace")
    parent.Unlock(device)
    unschedule(refresh)
    runIn(45, "refresh")
}

void Location()
{
    log("Location called", "trace")
    parent.getLocation(device)
}

def SetChargeLevel(pcmdACLevel, pcmdDCLevel) 
{
    log("SetChargeLevel called: AC=${pcmdACLevel}%, DC=${pcmdDCLevel}%", "info")
    if (pcmdACLevel != null && pcmdDCLevel != null) {
        parent.setChargeLimits(device, pcmdACLevel, pcmdDCLevel)
        unschedule(refresh)
        runIn(30, "refresh")
    } else {
        log("SetChargeLevel: Invalid parameters - both AC and DC levels are required", "error")
    }
}

def ClimFavoritesStart(pcmdFavoriteNbr) 
{
    log("ClimFavoritesStart called: Favorite #${pcmdFavoriteNbr}", "info")
    if (pcmdFavoriteNbr != null) {
        parent.ClimFavoritesStart(device, pcmdFavoriteNbr)
        unschedule(refresh)
        runIn(60, "refresh") // Attendre 60s avant de refresh
    } else {
        log("ClimFavoritesStart: Invalid parameter - favorite number required", "error")
    }
}

void ClimManual(pcmdTemp, pcmdDefrost, pcmdHeating)
{
    log("ClimManual called: Temp=${pcmdTemp}°C, Defrost=${pcmdDefrost}, Heating=${pcmdHeating}", "info")
    if (pcmdTemp != null && pcmdDefrost != null && pcmdHeating != null) {
        parent.ClimManual(device, pcmdTemp, pcmdDefrost, pcmdHeating)
        unschedule(refresh)
        runIn(60, "refresh")
    } else {
        log("ClimManual: Invalid parameters - all parameters are required", "error")
    }
}

void ClimStop()
{
    log("ClimStop called", "trace")
    parent.ClimStop(device)
    unschedule(refresh)
    runIn(30, "refresh")
}

void StartCharge()
{
    log("StartCharge called", "info")
    parent.StartCharge(device)
    unschedule(refresh)
    runIn(60, "refresh")
}

void StopCharge()
{
    log("StopCharge called", "info")
    parent.StopCharge(device)
    unschedule(refresh)
    runIn(30, "refresh")
}

///
// Auto-refresh scheduling
///
private void scheduleAutoRefresh() {
    unschedule(autoRefresh)
    
    def interval = settings?.autoRefreshInterval?.toInteger() ?: 0
    
    if (interval > 0) {
        log("Scheduling auto-refresh every ${interval} minutes", "info")
        // Utiliser une méthode de schedule appropriée selon l'intervalle
        switch(interval) {
            case 15:
                schedule("0 */15 * * * ?", autoRefresh)
                break
            case 30:
                schedule("0 */30 * * * ?", autoRefresh)
                break
            case 60:
                schedule("0 0 * * * ?", autoRefresh)
                break
        }
    } else {
        log("Auto-refresh disabled", "debug")
    }
}

void autoRefresh() {
    log("Auto-refresh triggered", "debug")
    // Auto-refresh utilise le cache (fullRefresh = false) pour éviter de surcharger l'API
    parent.getVehicleStatus(device, false, false)
    updateHtml()
}

///
// Supporting helpers
///
private void updateHtml()
{
    log("updateHtml", "trace")

    def builder = new StringBuilder()
    builder << "<style>"
    builder << ".bldr-tbl { width: 100%; border-collapse: collapse; }"
    builder << ".bldr-label { font-weight: bold; padding: 5px; }"
    builder << ".bldr-text { padding: 5px; }"
    builder << ".bldr-status-ok { color: green; }"
    builder << ".bldr-status-warn { color: orange; }"
    builder << ".bldr-status-error { color: red; }"
    builder << "</style>"
    
    builder << "<table class=\"bldr-tbl\">"
    
    // Doors
    String statDoors = device.currentValue("DoorLocks") ?: "Unknown"
    def doorClass = statDoors == "Locked" ? "bldr-status-ok" : "bldr-status-warn"
    builder << "<tr><td class=\"bldr-label\" style=\"text-align:left;\">" + "Doors:" + "</td>"
    builder << "<td class=\"bldr-text ${doorClass}\" style=\"text-align:left;padding-left:5px\">" + statDoors + "</td></tr>"
    
    // Trunk
    String statTrunk = device.currentValue("Trunk") ?: "Unknown"
    def trunkClass = statTrunk == "Closed" ? "bldr-status-ok" : "bldr-status-warn"
    builder << "<tr><td class=\"bldr-label\" style=\"text-align:left;\">" + "Trunk:" + "</td>"
    builder << "<td class=\"bldr-text ${trunkClass}\" style=\"text-align:left;padding-left:5px\">" + statTrunk + "</td></tr>"
    
    // Engine
    String statEngine = device.currentValue("Engine") ?: "Unknown"
    builder << "<tr><td class=\"bldr-label\" style=\"text-align:left;\">" + "Engine:" + "</td>"
    builder << "<td class=\"bldr-text\" style=\"text-align:left;padding-left:5px\">" + statEngine + "</td></tr>"
    
    // Battery (pour véhicules EV)
    String batteryPercent = device.currentValue("BatteryPercent")
    if (batteryPercent) {
        builder << "<tr><td class=\"bldr-label\" style=\"text-align:left;\">" + "Battery:" + "</td>"
        builder << "<td class=\"bldr-text\" style=\"text-align:left;padding-left:5px\">" + batteryPercent + "%</td></tr>"
        
        String batteryInCharge = device.currentValue("BatteryInCharge")
        if (batteryInCharge) {
            builder << "<tr><td class=\"bldr-label\" style=\"text-align:left;\">" + "Charging:" + "</td>"
            builder << "<td class=\"bldr-text\" style=\"text-align:left;padding-left:5px\">" + batteryInCharge + "</td></tr>"
        }
        
        String dcRange = device.currentValue("DCRange")
        if (dcRange) {
            builder << "<tr><td class=\"bldr-label\" style=\"text-align:left;\">" + "Range:" + "</td>"
            builder << "<td class=\"bldr-text\" style=\"text-align:left;padding-left:5px\">" + dcRange + " km</td></tr>"
        }
    }
    
    // Last update
    String lastRefresh = device.currentValue("LastRefreshTime")
    if (lastRefresh) {
        builder << "<tr><td class=\"bldr-label\" style=\"text-align:left;\">" + "Last Update:" + "</td>"
        builder << "<td class=\"bldr-text\" style=\"text-align:left;padding-left:5px;font-size:0.9em\">" + lastRefresh + "</td></tr>"
    }
    
    builder << "</table>"
    String newHtml = builder.toString()
    sendEvent(name:"statusHtml", value: newHtml)
}

private determineLogLevel(data) {
    switch (data?.toUpperCase()) {
        case "TRACE":
            return 0
            break
        case "DEBUG":
            return 1
            break
        case "INFO":
            return 2
            break
        case "WARN":
            return 3
            break
        case "ERROR":
            return 4
            break
        default:
            return 1
    }
}

def log(Object data, String type) {
    data = "-- ${device.label} -- ${data ?: ''}"

    if (determineLogLevel(type) >= determineLogLevel(settings?.logging ?: "INFO")) {
        switch (type?.toUpperCase()) {
            case "TRACE":
                log.trace "${data}"
                break
            case "DEBUG":
                log.debug "${data}"
                break
            case "INFO":
                log.info "${data}"
                break
            case "WARN":
                log.warn "${data}"
                break
            case "ERROR":
                log.error "${data}"
                break
            default:
                log.error("-- ${device.label} -- Invalid Log Setting")
        }
    }
}
