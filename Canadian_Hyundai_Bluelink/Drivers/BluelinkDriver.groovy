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
 *
 */

String appVersion()   { return "1.0.0" }
def setVersion(){
    state.name = "Hyundai Bluelink Driver"
    state.version = "1.0.0"
}

metadata {
    definition(
            name: "Canadian Hyundai Bluelink Driver",
            namespace: "jbilodea",
            description: "Driver for accessing Canadian Hyundai Bluelink web services",
            importUrl: "https://raw.githubusercontent.com/jbilodea/Hyundai-Bluelink/main/BluelinkApp.groovy",
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
                attribute "ACLevel", "number"
                attribute "ACRange", "number"
                attribute "DCLevel", "number"
                attribute "DCRange", "number"
                
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
            }

    preferences {
        section("Driver Options") {
            input("fullRefresh", "bool",
                    title: "Full refresh - Turn on this option to directly query the vehicle for status instead of using the vehicle's cached status. Warning: Turning on this option will result in status refreshes that can take as long as 2 minutes.",
                    defaultValue: false)
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
    fullRefresh = false;
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
    refresh()
}

void refresh()
{
    log("refresh called", "trace")
    parent.getVehicleStatus(device, fullRefresh, false)
    updateHtml()
}

void Lock()
{
    log("Lock called", "trace")
    parent.Lock(device)
    unschedule()
    runIn(45, "refresh")
}

void Unlock()
{
    log("Unlock called", "trace")
    parent.Unlock(device)
    unschedule()
    runIn(45, "refresh")
}

void Location()
{
    log("Location called", "trace")
    parent.getLocation(device)
}

def SetChargeLevel(pcmdACLevel, pcmdDCLevel) 
{
    log("SetChargeLevel called", "trace")
    if (pcmdACLevel != null && pcmdDCLevel != null) {
        parent.setChargeLimits(device, pcmdACLevel, pcmdDCLevel)
        unschedule()
        runIn(30, "refresh")
    }
}

def ClimFavoritesStart(pcmdFavoriteNbr) 
{
    log("ClimFavoritesStart called", "trace")
    if (pcmdFavoriteNbr != null) {
        parent.ClimFavoritesStart(device, pcmdFavoriteNbr)
    }
}

void ClimManual(pcmdTemp, pcmdDefrost, pcmdHeating)
{
    log("ClimManual called ${pcmdTemp} / ${pcmdDefrost} / ${pcmdHeating}", "trace")
    if (pcmdTemp != null && pcmdDefrost != null && pcmdHeating != null) {
        parent.ClimManual(device, pcmdTemp, pcmdDefrost, pcmdHeating)
    }
}

void ClimStop()
{
    log("ClimStop called", "trace")
    parent.ClimStop(device)
}

void StartCharge()
{
    log("StartCharge called", "trace")
    parent.StartCharge(device)
}

void StopCharge()
{
    log("StopCharge called", "trace")
    parent.StopCharge(device)
}

///
// Supporting helpers
///
private void updateHtml()
{
    log("updateHtml", "trace")
    log("updateHtml DoorLocks ${device.currentValue("DoorLocks")}", "trace")

    def builder = new StringBuilder()
    builder << "<table class=\"bldr-tbl\">"
    String statDoors = device.currentValue("DoorLocks")
    def statDoor = device.currentValue("DoorLocks")
    log("updateHtml statDoors ${statDoors}", "trace")
    log("updateHtml statDoor ${statDoor}", "trace")
    builder << "<tr><td class=\"bldr-label\" style=\"text-align:left;\">" + "Doors:" + "</td><td class=\"bldr-text\" style=\"text-align:left;padding-left:5px\">" + statDoors + "</td></tr>"
    String statTrunk = device.currentValue("Trunk")
    builder << "<tr><td class=\"bldr-label\" style=\"text-align:left;\">" + "Trunk:" + "</td><td class=\"bldr-text\" style=\"text-align:left;padding-left:5px\">" + statTrunk + "</td></tr>"
    String statEngine = device.currentValue("Engine")
    builder << "<tr><td class=\"bldr-label\" style=\"text-align:left;\">" + "Engine:" + "</td><td class=\"bldr-text\" style=\"text-align:left;padding-left:5px\">" + statEngine + "</td></tr>"
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

