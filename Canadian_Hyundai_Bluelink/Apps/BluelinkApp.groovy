/**
 *  Hyundai Bluelink Application
 *
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
 *
 *  Note:  FavDefrost is for Front Windshield Defrost
 *         FavHeating is for Heated features like the steering wheel and rear window
 *
 * Special thanks to:
 *
 * @WindowWasher for his good work on this app and driver.  I started this app by using his and modifying it for Canada users
 *
 * @fuatakgun for his coding in Home Assistant for the Canadian version.  I look at his code to understand the Canadian version
 */

import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import groovy.transform.Field

static String appVersion()   { return "1.1.0" }
def setVersion(){
    state.name = "Hyundai Bluelink Application"
    state.version = "1.1.0"
    state.transactionId = ""
}

@Field static String global_apiURL = "https://mybluelink.ca"
@Field static String API_URL = "https://mybluelink.ca/tods/api/"
@Field static String client_id = "m66129Bb-em93-SPAHYN-bZ91-am4540zp19920"
@Field static String client_secret = "v558o935-6nne-423i-baa8"
@Field static Map    API_Headers =  ["content-type": "application/json;charset=UTF-8",
                                    "accept": "application/json, text/plain, */*",
                                    "accept-encoding": "gzip, deflate, br",
                                    "accept-language": "en-US,en;q=0.9",
                                    "user-agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/75.0.3770.142 Safari/537.36",
                                    "Host": "mybluelink.ca",
                                    "Connection": "keep-alive",
                                    "origin": "https://mybluelink.ca",
                                    "referer": "https://mybluelink.ca/login",
//                                    "from": "SPA",
                                    "from": "CWP",                                     
                                    "language": "0",
                                    "offset": "0",
                                    "sec-fetch-dest": "empty",
                                    "sec-fetch-mode": "cors",
                                    "sec-fetch-site": "same-origin"]
@Field static ArrayList CA_TEMP_RANGE = ["17", "17.5", "18", "18.5", "19", "19.5", "20", "20.5", "21", "21.5", "22", "22.5", "23", "23.5", "24", "24.5", "25", "25.5", "26", "26.5", "27"]

definition(
        name: "Hyundai Bluelink App",
        namespace: "jbilodea",
        author: "Jean Bilodeau",
        description: "Application for Canadian Hyundai Bluelink web service access.",
        importUrl:"https://raw.githubusercontent.com/jbilodea/Hyundai-Bluelink/main/BluelinkApp.groovy",
        category: "Convenience",
        iconUrl: "",
        iconX2Url: ""
)

preferences {
    page(name: "mainPage")
    page(name: "accountInfoPage")
    page(name: "profilesPage")
    page(name: "debugPage", title: "Debug Options", install: false)
}

def mainPage()
{
    dynamicPage(name: "mainPage", title: "Hyundai Bluelink App", install: true, uninstall: true) {
        section(getFormat("title","About Hyundai Bluelink Application")) {
            paragraph "This application and the corresponding driver are used to access the Canadian Hyundai Bluelink web services with Hubitat Elevation. Follow the steps below to configure the application."
        }
        section(getFormat("header-blue-grad","   1.  Set Bluelink Account Information")) {
        }
        getAccountLink()
        section(getFormat("item-light-grey","Account log-in")) {
            input(name: "stay_logged_in", type: "bool", title: "Stay logged in - turn off to force logging in before performing each action.", defaultValue: true, submitOnChange: true)
        }
        section(getFormat("header-blue-grad","   2.  Use This Button To Discover Vehicles and Create Drivers for Each")) {
            input 'discover', 'button', title: 'Discover Registered Vehicles', submitOnChange: true
        }
        listDiscoveredVehicles()
        section(getFormat("header-blue-grad","   3.  Review or Change Remote Start Options")) {
        }
        getProfileLink()
        section(getFormat("header-blue-grad","Change Logging Level")) {
            input name: "logging", type: "enum", title: "Log Level", description: "Debug logging", required: false, submitOnChange: true, defaultValue: "INFO", options: ["TRACE", "DEBUG", "INFO", "WARN", "ERROR"]
        }
        getDebugLink()
    }
}

def accountInfoPage()
{
    dynamicPage(name: "accountInfoPage", title: "<strong>Set Bluelink Account Information</strong>", install: false, uninstall: false) {
        section(getFormat("item-light-grey", "Username")) {
            input name: "user_name", type: "string", title: "Bluelink Username"
        }
        section(getFormat("item-light-grey", "Password")) {
            input name: "user_pwd", type: "string", title: "Bluelink Password"
        }
        section(getFormat("item-light-grey", "PIN")) {
            input name: "bluelink_pin", type: "string", title: "Bluelink PIN"
        }
    }
}

def getAccountLink() {
    section{
        href(
                name       : 'accountHref',
                title      : 'Account Information',
                page       : 'accountInfoPage',
                description: 'Set or Change Bluelink Account Information'
        )
    }
}

def profilesPage()
{
    dynamicPage(name: "profilesPage", title: "<strong>Review/Edit Vehicle Start Options</strong>", install: false, uninstall: false) {
        for (int i = 0; i < 3; i++) {
            String profileName = "Summer"
            switch(i)
            {
                case 0: profileName = "Summer"
                    break
                case 1: profileName = "Winter"
                    break
                case 2: profileName = "Profile3"
            }
            def tempOptions = ["LO", "64", "66", "68", "70", "72", "74", "76", "78", "80", "HI"]
            section(getFormat("header-blue-grad","Profile: ${profileName}")) {
                input(name: "${profileName}_climate", type: "bool", title: "Turn on climate control when starting", defaultValue: true, submitOnChange: true)
                input(name: "${profileName}_temp", type: "enum", title: "Climate temperature to set", options: tempOptions, defaultValue: "70", required: true)
                input(name: "${profileName}_defrost", type: "bool", title: "Turn on defrost when starting", defaultValue: false, submitOnChange: true)
                input(name: "${profileName}_heatAcc", type: "bool", title: "Turn on heated accessories when starting", defaultValue: false, submitOnChange: true)
                input(name: "${profileName}_ignitionDur", type: "number", title: "Minutes run engine? (1-10)", defaultValue: 10, range: "1..10", required: true, submitOnChange: true)
            }
        }
    }
}

def getProfileLink() {
    section{
        href(
                name       : 'profileHref',
                title      : 'Start Profiles',
                page       : 'profilesPage',
                description: 'View or edit vehicle start profiles'
        )
    }
}

////////
// Debug Stuff
///////
def getDebugLink() {
    section{
        href(
                name       : 'debugHref',
                title      : 'Debug buttons',
                page       : 'debugPage',
                description: 'Access debug buttons (refresh token, initialize)'
        )
    }
}

def debugPage() {
    dynamicPage(name:"debugPage", title: "Debug", install: false, uninstall: false) {
        section {
            paragraph "<strong>Debug buttons</strong>"
        }
        section {
            input 'refreshToken', 'button', title: 'Force Token Refresh', submitOnChange: true
        }
        section {
            input 'initialize', 'button', title: 'initialize', submitOnChange: true
        }
    }
}

def appButtonHandler(btn) {
    switch (btn) {
        case 'discover':
            authorize()
            getVehicles()
            break
        case 'refreshToken':
            refreshToken()
            break
        case 'initialize':
            initialize()
            break
        default:
            log("Invalid Button In Handler", "error")
    }
}

void installed() {
    log("Installed with settings: ${settings}", "trace")
    stay_logged_in = true // initialized to ensure token refresh happens with default setting
    initialize()
}

void updated() {
    log("Updated with settings: ${settings}", "trace")
    initialize()
}

void uninstalled() {
    log("Uninstalling Hyundai Bluelink App and deleting child devices", "info")
    unschedule()
    for (device in getChildDevices())
    {
        deleteChildDevice(device.deviceNetworkId)
    }
}

void initialize() {
    log("Initialize called", "trace")
    setVersion()
    unschedule()
    if(stay_logged_in && (state.refresh_token != null)) {
        refreshToken()
    }
}

void authorize() {
    log("authorize called", "info")
    
    // make sure there are no outstanding token refreshes scheduled
    unschedule()
    API_Headers =  ["content-type": "application/json;charset=UTF-8",
                                    "accept": "application/json, text/plain, */*",
                                    "accept-encoding": "gzip, deflate, br",
                                    "accept-language": "en-US,en;q=0.9",
                                    "user-agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/75.0.3770.142 Safari/537.36",
                                    "Host": "mybluelink.ca",
                                    "Connection": "keep-alive",
                                    "origin": "https://mybluelink.ca",
                                    "referer": "https://mybluelink.ca/login",
//                                    "from": "SPA",
                                    "from": "CWP",                                     
                                    "language": "0",
                                    "offset": "0",
                                    "sec-fetch-dest": "empty",
                                    "sec-fetch-mode": "cors",
                                    "sec-fetch-site": "same-origin"]
    log("API_Headers1 ${API_Headers}", "info")
    def body = [
            "loginId": user_name,
            "password": user_pwd
    ]
    def url = API_URL + "v2/login" 
    API_Headers.referer = "https://mybluelink.ca/login"
    def params = [uri: url, headers: API_Headers, requestContentType: "application/json", body: body, ignoreSSLIssues: true]  
    log("params1 ${params}", "info")

    try
    {
        httpPost(params) { response -> authResponse(response) }
    }
    catch (groovyx.net.http.HttpResponseException e)
    {
        log("Login failed -- ${e.getLocalizedMessage()}: ${e.response.data}", "error")
    }
}

void refreshToken(Boolean refresh=false) {
    log("refreshToken called", "trace")

    if (state.refresh_token != null)
    {       
        def body = [
                refresh_token: state.refresh_token
        ]
        def url = API_URL + "lgn"
        API_Headers.referer = "https://mybluelink.ca/login"
        def params = [uri: url, headers: API_Headers, requestContentType: "application/json", body: body, ignoreSSLIssues: true]  

        try
        {
            httpPost(params) { response -> authResponse(response) }
        }
        catch (java.net.SocketTimeoutException e)
        {
            if (!refresh) {
                log("Socket timeout exception, will retry refresh token", "info")
                refreshToken(true)
            }
        }
        catch (groovyx.net.http.HttpResponseException e)
        {
            log("Login failed -- ${e.getLocalizedMessage()}: ${e.response.data}", "error")
        }
    }
    else
    {
        log("Failed to refresh token, refresh token null.", "error")
    }
}

def authResponse(response)
{
    log("authResponse called", "info")

    unschedule()
    def reCode = response.getStatus()
    def reJson = response.getData()
    log("reCode: {$reCode}", "debug")
    log("reJson: {$reJson}", "debug")
    if (reJson.containsKey('error')) {
        log("reJsonerror: {$reJson.error}", "debug")
        if (reJson.error.containsKey('errorCode'))
        {
            log("reJsonerrorCode: {$reJson.error.errorCode}", "debug")
            if (reJson.error.errorCode == 7403)
            {
                
            }
        }
    }
    if (reCode == 200)
    {
        state.access_token = reJson.result.token.accessToken
        state.refresh_token = reJson.result.token.refreshToken
        def expireIn = reJson.result.token.expireIn

//      Integer expireTime = (Integer.parseInt(reJson.result.expireIn) - 180)
        Integer expireTime = reJson.result.token.expireIn - 180
        log("Bluelink token refreshed successfully, Next Scheduled in: ${expireTime} sec", "debug")
//     set up token refresh
        if (stay_logged_in) {
            runIn(expireTime, refreshToken)
        }
    }
    else
    {
        log("LoginResponse Failed HTTP Request Status: ${reCode}", "error")
    }
}

def getVehicles(Boolean retry=false)
{
    log("getVehicles called", "trace")

    def uri = API_URL +  "vhcllst"  
    API_Headers.referer = "https://mybluelink.ca/login"
    def headers = API_Headers
    headers.put('accessToken', state.access_token)
    def params = [ uri: uri, headers: headers, requestContentType: "application/json", ignoreSSLIssues: true]
    log("getVehicles ${params}", "debug")

    //add error checking
    LinkedHashMap reJson = []
    try
    {
        httpPost(params) { response ->
            def reCode = response.getStatus()
            reJson = response.getData()
            log("reCodegetVeh: ${reCode}", "debug")
            log("reJsongetVeh: ${reJson}", "debug")
        }
    }
    catch (groovyx.net.http.HttpResponseException e)
    {
        if (e.getStatusCode() == 401 && !retry)
        {
            log('Authorization token expired, will refresh and retry.', 'warn')
            refreshToken()
            getVehicles(true)
        }
        log("getVehicles failed -- ${e.getLocalizedMessage()}: ${e.response.data}", "error")
        return
    }

    if (reJson.result.vehicles == null) {
        log("No enrolled vehicles found.", "info")
    }
    else {
            reJson.result.vehicles.each{ vehicle ->
                log("Found vehicle: ${vehicle.nickName} with VIN: ${vehicle.vin}", "info")
                def newDevice = CreateChildDriver(vehicle.nickName, vehicle.vin)
                if (newDevice != null) {
                    //populate attributes
                    state.vehicleId = vehicle.vehicleId
                    sendEvent(newDevice, [name: "NickName", value:  vehicle.nickName])
                    sendEvent(newDevice, [name: "VIN", value:  vehicle.vin])
                    sendEvent(newDevice, [name: "modelCode", value:  vehicle.modelCode])
                    sendEvent(newDevice, [name: "modelName", value:  vehicle.modelName])
                    sendEvent(newDevice, [name: "modelYear", value:  vehicle.modelYear])
                    sendEvent(newDevice, [name: "fuelKindCode", value:  vehicle.fuelKindCode])
                    sendEvent(newDevice, [name: "trim", value:  vehicle.trim])
                    sendEvent(newDevice, [name: "exteriorColor", value:  vehicle.exteriorColor])
                    sendEvent(newDevice, [name: "subscriptionStatus", value:  vehicle.subscriptionStatus])
                    sendEvent(newDevice, [name: "subscriptionEndDate", value:  vehicle.subscriptionEndDate])
                    sendEvent(newDevice, [name: "mileageForNextService", value:  vehicle.mileageForNextService])
                    sendEvent(newDevice, [name: "daysForNextService", value:  vehicle.daysForNextService])
                    sendEvent(newDevice, [name: "overviewMessage", value:  vehicle.overviewMessage])
                    sendEvent(newDevice, [name: "valetParkingModeOn", value:  vehicle.valetParkingModeOn])
                 }
            }
    }
}

void getVehicleStatus(com.hubitat.app.DeviceWrapper device, Boolean refresh = false, Boolean retry=false)
{
    log("getVehicleStatus() called", "trace")

    if( !stay_logged_in ) {
        authorize()
    }

    //Note: this API can take up to a minute tor return if REFRESH=true because it contacts the car's modem and
    //doesn't use cached info.
    def uri = API_URL + "lstvhclsts"
    API_Headers.referer = "https://mybluelink.ca/overview"
    def headers = API_Headers
    headers.put('accessToken', state.access_token)
    headers.put('vehicleId', state.vehicleId)
    headers.put('offset', '-5')
    headers.put('REFRESH', refresh.toString())
    int valTimeout = refresh ? 240 : 30 //timeout in sec.
    def params = [ uri: uri, headers: headers, timeout: valTimeout ]
    log("getVehicleStatus params ${params}", "debug")

    //add error checking
    LinkedHashMap  reJson = []
    try
    {
        httpPost(params) { response ->
            def reCode = response.getStatus()
            reJson = response.getData()
            log("reCode: ${reCode}", "debug")
            log("reJson: ${reJson}", "debug")
        }

        // Update relevant device attributes
        sendEvent(device, [name: 'Engine', value: reJson.result.status.engine ? 'On' : 'Off'])
        sendEvent(device, [name: 'DoorLocks', value: reJson.result.status.doorLock ? 'Locked' : 'Unlocked'])
        sendEvent(device, [name: 'Trunk', value: reJson.result.status.trunkOpen ? 'Open' : 'Closed'])
        sendEvent(device, [name: "LastRefreshTime", value: Date.parse('yyyyMMddHHmmSS', reJson.result.status.lastStatusDate).format('E MMM dd HH:mm:ss z yyyy')])
// JBJB temporary
        sendEvent(device, [name: 'BatteryInCharge', value: reJson.result.status.evStatus.batteryCharge ? 'On' : 'Off'])
        sendEvent(device, [name: 'BatteryPercent', value: reJson.result.status.evStatus.batteryStatus])
        def mins = reJson.result.status.evStatus.remainTime2.etc3.value
        int hours = mins / 60
        int minutes = mins % 60
        def timeToDisplay = String.format("%02d:%02d", hours, minutes)
        sendEvent(device, [name: 'BatteryTimeToCharge', value: timeToDisplay])

    }
    catch (groovyx.net.http.HttpResponseException e)
    {
        if (e.getStatusCode() == 401 && !retry)
        {
            log('Authorization token expired, will refresh and retry.', 'warn')
            refreshToken()          
            getVehicleStatus(device, refresh, true)
        }
        log("getVehicleStatus failed -- ${e.getLocalizedMessage()}: ${e.response.data}", "error")
    }
        getNextService(device)
        getChargeLimits(device)
        ClimFavoritesDisplay(device)
}

void getForceVehicleStatus(com.hubitat.app.DeviceWrapper device, Boolean refresh = false, Boolean retry=false)
{
    log("getVehicleStatus() called", "trace")

    if( !stay_logged_in ) {
        authorize()
    }

    //Note: this API can take up to a minute tor return if REFRESH=true because it contacts the car's modem and
    //doesn't use cached info.
    def uri = API_URL + "rltmvhclsts"
    API_Headers.referer = "https://mybluelink.ca/cpw/overview"
    def headers = API_Headers
    headers.put('accessToken', state.access_token)
    headers.put('vehicleId', state.vehicleId)
    headers.put('offset', '-5')
    headers.put('REFRESH', refresh.toString())
    int valTimeout = refresh ? 240 : 30 //timeout in sec.
    def params = [ uri: uri, headers: headers, timeout: valTimeout ]
    log("getVehicleStatus params ${params}", "debug")

    //add error checking
    LinkedHashMap  reJson = []
    try
    {
        httpPost(params) { response ->
            def reCode = response.getStatus()
            reJson = response.getData()
            log("reCode: ${reCode}", "debug")
            log("reJson: ${reJson}", "debug")
        }

        // Update relevant device attributes
        sendEvent(device, [name: 'Engine', value: reJson.result.status.engine ? 'On' : 'Off'])
        sendEvent(device, [name: 'DoorLocks', value: reJson.result.status.doorLock ? 'Locked' : 'Unlocked'])
        sendEvent(device, [name: 'Trunk', value: reJson.result.status.trunkOpen ? 'Open' : 'Closed'])
        sendEvent(device, [name: "LastRefreshTime", value: Date.parse('yyyyMMddHHmmSS', reJson.result.status.lastStatusDate).format('E MMM dd HH:mm:ss z yyyy')])
// JBJB temporary
        sendEvent(device, [name: 'BatteryInCharge', value: reJson.result.status.evStatus.batteryCharge ? 'On' : 'Off'])
        sendEvent(device, [name: 'BatteryPercent', value: reJson.result.status.evStatus.batteryStatus])
        def mins = reJson.result.status.evStatus.remainTime2.etc3.value
        int hours = mins / 60
        int minutes = mins % 60
        def timeToDisplay = String.format("%02d:%02d", hours, minutes)
        sendEvent(device, [name: 'BatteryTimeToCharge', value: timeToDisplay])

    }
    catch (groovyx.net.http.HttpResponseException e)
    {
        if (e.getStatusCode() == 401 && !retry)
        {
            log('Authorization token expired, will refresh and retry.', 'warn')
            refreshToken()          
            getVehicleStatus(device, refresh, true)
        }
        log("getVehicleStatus failed -- ${e.getLocalizedMessage()}: ${e.response.data}", "error")
    }
        getNextService(device)
        getChargeLimits(device)
        ClimFavoritesDisplay(device)
}


void getNextService(com.hubitat.app.DeviceWrapper device)
{
    log("getNextService() called", "trace")
    API_Headers.referer = "https://mybluelink.ca/login"
    def headers = API_Headers
    headers.put('accessToken', state.access_token)
    headers.put('vehicleId', state.vehicleId)
    headers.put('offset', '-5')
    headers.put('REFRESH', refresh.toString())
    int valTimeout = refresh ? 240 : 30 //timeout in sec.
    def uri = API_URL + "nxtsvc"
    def params = [ uri: uri, headers: headers, timeout: valTimeout ]

    try
    {
        httpPost(params) { response ->
            def reCode = response.getStatus()
            reJson = response.getData()
            log("reCode NextServ: ${reCode}", "debug")
            log("reJson NextServ: ${reJson}", "debug")
        }
        sendEvent(device, [name: 'Odometer', value: reJson.result.maintenanceInfo.currentOdometer])
    }
    catch (groovyx.net.http.HttpResponseException e)
    {
        if (e.getStatusCode() == 401 && !retry)
        {
            log('Authorization token expired, will refresh and retry.', 'warn')
            refreshToken()
            getNextService(device)
        }
        log("getNextService(device) failed -- ${e.getLocalizedMessage()}: ${e.response.data}", "error")
    }
}
void getLocation(com.hubitat.app.DeviceWrapper device, Boolean refresh=false)
{
    log("getLocation() called", "trace")

    if( !stay_logged_in ) {
        authorize()
    }

    def uri = API_URL + "fndmcr"
    API_Headers.referer = "https://mybluelink.ca/login"
    def headers = API_Headers
    headers.put('accessToken', state.access_token)
    headers.put('vehicleId', state.vehicleId)
    headers.put('offset', '-5')
    headers.put('pAuth', getPinToken(device))

    def thePin = ["pin": bluelink_pin]
    def params = [uri: uri, headers: headers, requestContentType: "application/json", body: thePin, ignoreSSLIssues: true, timeout: 120]  
    log("getLocation params ${params}", "debug")

    try
    {
        httpPost(params) { response ->
            int reCode = response.getStatus()
            reJson = response.getData()
            log("reCode getLocation: ${reCode}", "debug")
            log("reJson getLocation: ${reJson}", "debug")

            if (reCode == 200) {
                log("getLocation successful.","info")
                sendEventHelper(device, "Location", true)
            }
            if( reJson.result.coord != null) {
                sendEvent(device, [name: 'locLatitude', value: reJson.result.coord.lat])
                sendEvent(device, [name: 'locLongitude', value: reJson.result.coord.lon])
                sendEvent(device, [name: 'locAltitude', value: reJson.result.coord.alt])
                sendEvent(device, [name: 'locSpeed', value: reJson.result.speed.value])
                sendEvent(device, [name: 'locUpdateTime', value: reJson.result.time])
            }
        }
    }
    catch (groovyx.net.http.HttpResponseException e)
    {
        if (e.getStatusCode() == 401 && !retry)
        {
            log('Authorization token expired, will refresh and retry.', 'warn')
            refreshToken()
            getLocation(device, true)
            return
        }
        log("getLocation failed -- ${e.getLocalizedMessage()}: Status: ${e.response.getStatus()}", "error")
        sendEventHelper(device, "Location", false)
    }
}

def getPinToken(com.hubitat.app.DeviceWrapper device)
{
    log("getPinToken() called", "trace")

    def uri = API_URL + "vrfypin"
    API_Headers.referer = "https://mybluelink.ca/remote/climate"
    def headers = API_Headers
    headers.put('accessToken', state.access_token)
    headers.put('vehicleId', state.vehicleId)

    def thePin = [pin: bluelink_pin]
    log("getPinToken thePin ${thePin}", "debug")
    
    def params = [uri: uri, headers: headers, requestContentType: "application/json", body: thePin, ignoreSSLIssues: true]  
    log("getPinToken params ${params}", "debug")

    try
    {
        httpPost(params) { response ->
            int reCode = response.getStatus()
            reJson = response.getData()
            log("reCode getPinToken: ${reCode}", "debug")
            log("reJson getPinToken: ${reJson}", "debug")

            if (reCode == 200) {
                log("getPinToken successful.","info")
            }
        }
    }
    catch (groovyx.net.http.HttpResponseException e)
    {
        if (e.getStatusCode() == 401 && !retry)
        {
            log('Authorization token expired, will refresh and retry.', 'warn')
//          refreshToken()
//          getPinToken(device, true)
            return
        }
        log("getPinToken failed -- ${e.getLocalizedMessage()}: Status: ${e.response.getStatus()}", "error")
    }
    return reJson.result.pAuth
}

void Lock(com.hubitat.app.DeviceWrapper device)
{
    if( !LockUnlockHelper(device, 'drlck') )
    {
        log("Lock call failed -- try waiting before retrying", "info")
        sendEventHelper(device, "Lock", false)
    } else
    {
        log("Lock call made to car -- can take some time to lock", "info")
        sendEventHelper(device, "Lock", true)
    }
}

void Unlock(com.hubitat.app.DeviceWrapper device)
{
    if( !LockUnlockHelper(device, 'drulck') )
    {
        log("Unlock call failed -- try waiting before retrying", "info")
        sendEventHelper(device, "Unlock", false)
    } else
    {
        log("Unlock call made to car -- can take some time to unock", "info")
        sendEventHelper(device, "Unlock", true)
    }
}

void ClimFavoritesDisplay(com.hubitat.app.DeviceWrapper device)
{
    log("ClimFavoritesDisplay() called", "trace")

    def uri = API_URL + "gtfvsttng"
    API_Headers.referer = "https://mybluelink.ca/charge"
    def headers = API_Headers
    headers.put('accessToken', state.access_token)
    headers.put('vehicleId', state.vehicleId)
    headers.put('offset', '-5')
    headers.put('REFRESH', refresh.toString())
    int valTimeout = refresh ? 240 : 30 //timeout in sec.
    def params = [ uri: uri, headers: headers, timeout: valTimeout ]
    log("ClimFavoritesDisplay params ${params}", "debug")

    LinkedHashMap  reJson = []

    try
    {
        httpPost(params) { response ->
            def reCode = response.getStatus()
            reJson = response.getData()
            log("ClimFavoritesDisplay reCode: ${reCode}", "debug")
            log("ClimFavoritesDisplay reJson: ${reJson}", "debug")

            if (reCode == 200) {
                log("ClimFavoritesDisplay successful.","info")
            }
            if( reJson.result[0] != null) {
// Favorite 1                
                def tempHex1 = reJson.result[0].airTemp.value
                tempIndex1 = tempHex1.substring(0,2)
                int index1 = Integer.parseInt(tempIndex1, 16) - 6
                def temperature1 = CA_TEMP_RANGE.get(index1)
                
                sendEvent(device, [name: 'Fav1-Name', value: reJson.result[0].settingName])
                sendEvent(device, [name: 'Fav1Defrost', value: reJson.result[0].defrost])                
                sendEvent(device, [name: 'Fav1Temp', value: temperature1])                
                sendEvent(device, [name: 'Fav1Heating', value: reJson.result[0].heating1])
            }
            if( reJson.result[1] != null) {
// Favorite 2                
                def tempHex2 = reJson.result[1].airTemp.value
                tempIndex2 = tempHex2.substring(0,2)
                int index2 = Integer.parseInt(tempIndex2, 16) - 6
                def temperature2 = CA_TEMP_RANGE.get(index2)
                
                sendEvent(device, [name: 'Fav2-Name', value: reJson.result[1].settingName])
                sendEvent(device, [name: 'Fav2Defrost', value: reJson.result[1].defrost])                
                sendEvent(device, [name: 'Fav2Temp', value: temperature2])                
                sendEvent(device, [name: 'Fav2Heating', value: reJson.result[1].heating1])                
            }
            if( reJson.result[2] != null) {
// Favorite 3
                def tempHex3 = reJson.result[2].airTemp.value
                tempIndex3 = tempHex3.substring(0,2)
                int index3 = Integer.parseInt(tempIndex3, 16) - 6
                def temperature3 = CA_TEMP_RANGE.get(index3)
                
                sendEvent(device, [name: 'Fav3-Name', value: reJson.result[2].settingName])
                sendEvent(device, [name: 'Fav3Defrost', value: reJson.result[2].defrost])                
                sendEvent(device, [name: 'Fav3Temp', value: temperature3])                
                sendEvent(device, [name: 'Fav3Heating', value: reJson.result[2].heating1])                
            }
        }
    }
    catch (groovyx.net.http.HttpResponseException e)
    {
        if (e.getStatusCode() == 401 && !retry)
        {
            log('Authorization token expired, will refresh and retry.', 'warn')
            refreshToken()          
//          ClimFavoritesDisplay(device)
        }
        log("ClimFavoritesDisplay failed -- ${e.getLocalizedMessage()}: ${e.response.data}", "error")
    }    

}

void ClimFavoritesStart(com.hubitat.app.DeviceWrapper device, pcmdFavoriteNbr)
{
    log("ClimFavoritesStart() called", "trace")

    if( !stay_logged_in ) {
        authorize()
    }

    def uri = API_URL + "evc/rfon"
    API_Headers.referer = "https://mybluelink.ca/remote/climate"
    def headers = API_Headers
    headers.put('accessToken', state.access_token)
    headers.put('vehicleId', state.vehicleId)
    headers.put('pAuth', getPinToken(device))    
    headers.put('offset', '-5')
    headers.put('REFRESH', refresh.toString())
    int valTimeout = refresh ? 240 : 60 //timeout in sec.

    def FavTemp = ""
    def FavDefrost = ""
    def FavHeating = ""
    if (pcmdFavoriteNbr == "1") {
        FavTemp = device.currentValue("Fav1Temp") 
        FavDefrost = device.currentValue("Fav1Defrost")
        FavHeating = device.currentValue("Fav1Heating")
    }
    else {
        if (pcmdFavoriteNbr == "2") {
            FavTemp = device.currentValue("Fav2Temp") 
            FavDefrost = device.currentValue("Fav2Defrost")
            FavHeating = device.currentValue("Fav2Heating")
        }
        else {
            if (pcmdFavoriteNbr == "3") {
                FavTemp = device.currentValue("Fav3Temp") 
                FavDefrost = device.currentValue("Fav3Defrost")
                FavHeating = device.currentValue("Fav3Heating")
            }
        }
    }
        
    def temperatureIndex = CA_TEMP_RANGE.indexOf(FavTemp) + 6
    def indexHex = Integer.toHexString(temperatureIndex).toUpperCase()
    if (indexHex.length() == 1) {
        indexHex = '0' + indexHex
    }
    indexHex = indexHex + "H"

    def hvacInfo = ["hvacInfo": [
                        "airCtrl": 1, 
                        "airTemp": [
                            "hvacTempType": 1, 
                            "unit": 0, 
                            "value": indexHex],
                        "defrost": FavDefrost, 
                        "heating1": FavHeating], 
                    "pin": bluelink_pin]

        log("ClimFavoritesStart hvacInfo ${hvacInfo}", "debug")

    def params = [uri: uri, headers: headers, requestContentType: "application/json", body: hvacInfo, ignoreSSLIssues: true, timeout: valTimeout]  
    
    log("ClimFavoritesStart params ${params}", "debug")

    LinkedHashMap  reJson = []

    try
    {
        httpPost(params) { response ->
            def reCode = response.getStatus()
            reJson = response.getData()
            state.transactionId = response.headers['transactionId']
            log("ClimFavoritesStart reCode: ${reCode}", "debug")
            log("ClimFavoritesStart reJson: ${reJson}", "debug")

            if (reCode == 200) {
                log("ClimFavoritesStart successful.","info")
                sendEventHelper(device, "ClimFavoritesStart", true)                
            }
            else {
                sendEventHelper(device, "ClimFavoritesStart", false)
            }
        }
    }
    catch (groovyx.net.http.HttpResponseException e)
    {
        if (e.getStatusCode() == 401 && !retry)
        {
            log('Authorization token expired, will refresh and retry.', 'warn')
            refreshToken()          
            ClimFavoritesStart(device)
        }
        log("ClimFavoritesStart failed -- ${e.getLocalizedMessage()}: ${e.response.data}", "error")
    }    

}

void ClimManual(com.hubitat.app.DeviceWrapper device, pcmdTemp, pstrCmdDefrost, pcmdHeating)
{
    log("ClimManual() called", "trace")
    log("ClimManual() pstrCmdDefrost ${pstrCmdDefrost}", "trace")

    if( !stay_logged_in ) {
        authorize()
    }

    def uri = API_URL + "evc/rfon"
    API_Headers.referer = "https://mybluelink.ca/remote/climate"
    def headers = API_Headers
    headers.put('accessToken', state.access_token)
    headers.put('vehicleId', state.vehicleId)
    headers.put('pAuth', getPinToken(device))    
    headers.put('offset', '-5')
    headers.put('REFRESH', refresh.toString())
    int valTimeout = refresh ? 240 : 60 //timeout in sec.

    def temperatureIndex = CA_TEMP_RANGE.indexOf(pcmdTemp) + 6
    def indexHex = Integer.toHexString(temperatureIndex).toUpperCase()
    if (indexHex.length() == 1) {
        indexHex = '0' + indexHex
    }
    indexHex = indexHex + "H"
    def boolean pcmdDefrost
    if (pstrCmdDefrost == "false") {
        pcmdDefrost = false
    log("ClimManual1() called", "trace")
    }
    else {
        pcmdDefrost = true
    log("ClimManual2() called", "trace")
    }
    if (pcmdHeating == "false") {
        pcmdHeating = 0
    }
    else {
        pcmdHeating = 1
    }

    def hvacInfo = ["hvacInfo": [
                        "airCtrl": 1, 
                        "airTemp": [
                            "hvacTempType": 1, 
                            "unit": 0, 
                            "value": indexHex],
                        "defrost": pcmdDefrost, 
                        "heating1": pcmdHeating], 
                    "pin": bluelink_pin]

        log("ClimManual hvacInfo ${hvacInfo}", "debug")

    def params = [uri: uri, headers: headers, requestContentType: "application/json", body: hvacInfo, ignoreSSLIssues: true, timeout: valTimeout]   
    log("ClimManual params ${params}", "debug")

    LinkedHashMap  reJson = []

    try
    {
        httpPost(params) { response ->
            def reCode = response.getStatus()
            reJson = response.getData()
            state.transactionId = response.headers['transactionId']
            log("ClimManual reCode: ${reCode}", "debug")
            log("ClimManual reJson: ${reJson}", "debug")

            if (reCode == 200) {
                log("ClimManual successful.","info")
                sendEventHelper(device, "ClimManual", true)                
            }
            else {
                sendEventHelper(device, "ClimManual", false)
            }
        }
    }
    catch (groovyx.net.http.HttpResponseException e)
    {
        if (e.getStatusCode() == 401 && !retry)
        {
            log('Authorization token expired, will refresh and retry.', 'warn')
            refreshToken()          
            ClimManual(device)
        }
        log("ClimManual failed -- ${e.getLocalizedMessage()}: ${e.response.data}", "error")
    }    

}

void ClimStop(com.hubitat.app.DeviceWrapper device)
{
    log("ClimStop() called", "trace")

    if( !stay_logged_in ) {
        authorize()
    }

    def uri = API_URL + "evc/rfoff"
    API_Headers.referer = "https://mybluelink.ca/remote/climate"
    def headers = API_Headers
    headers.put('accessToken', state.access_token)
    headers.put('vehicleId', state.vehicleId)
    headers.put('pAuth', getPinToken(device))    
    headers.put('offset', '-5')
    headers.put('REFRESH', refresh.toString())
    int valTimeout = refresh ? 240 : 30 //timeout in sec.
    def thePin = ["pin": bluelink_pin]

    def params = [uri: uri, headers: headers, requestContentType: "application/json", body: thePin, ignoreSSLIssues: true, timeout: valTimeout]  
    
    log("ClimStop params ${params}", "debug")

    LinkedHashMap  reJson = []

    try
    {
        httpPost(params) { response ->
            def reCode = response.getStatus()
            reJson = response.getData()
            log("ClimStop reCode: ${reCode}", "debug")
            log("ClimStop reJson: ${reJson}", "debug")

            if (reCode == 200) {
                log("ClimStop successful.","info")
                sendEventHelper(device, "ClimStop", true)                
            }
            else {
                sendEventHelper(device, "ClimStop", false)
            }
        }
    }
    catch (groovyx.net.http.HttpResponseException e)
    {
        if (e.getStatusCode() == 401 && !retry)
        {
            log('Authorization token expired, will refresh and retry.', 'warn')
            refreshToken()          
            ClimStop(device)
        }
        log("ClimStop failed -- ${e.getLocalizedMessage()}: ${e.response.data}", "error")
    }    

}

void StartCharge(com.hubitat.app.DeviceWrapper device)
{
    log("StartCharge() called", "trace")

    if( !stay_logged_in ) {
        authorize()
    }

    def uri = API_URL + "evc/rcstrt"
    API_Headers.referer = "https://mybluelink.ca/login"
    def headers = API_Headers
    headers.put('accessToken', state.access_token)
    headers.put('vehicleId', state.vehicleId)
    headers.put('pAuth', getPinToken(device))    
    headers.put('offset', '-5')
    headers.put('REFRESH', refresh.toString())
    int valTimeout = refresh ? 240 : 30 //timeout in sec.
    def thePin = ["pin": bluelink_pin]

    def params = [uri: uri, headers: headers, requestContentType: "application/json", body: thePin, ignoreSSLIssues: true, timeout: valTimeout]  
    
    log("StartCharge params ${params}", "debug")

    LinkedHashMap  reJson = []

    try
    {
        httpPost(params) { response ->
            def reCode = response.getStatus()
            reJson = response.getData()
            log("StartCharge reCode: ${reCode}", "debug")
            log("StartCharge reJson: ${reJson}", "debug")

            if (reCode == 200) {
                log("StartCharge successful.","info")
                sendEventHelper(device, "StartCharge", true)                
            }
            else {
                sendEventHelper(device, "StartCharge", false)
            }
        }
    }
    catch (groovyx.net.http.HttpResponseException e)
    {
        if (e.getStatusCode() == 401 && !retry)
        {
            log('Authorization token expired, will refresh and retry.', 'warn')
            refreshToken()          
            StartCharge(device)
        }
        log("StartCharge failed -- ${e.getLocalizedMessage()}: ${e.response.data}", "error")
    }    

}

void StopCharge(com.hubitat.app.DeviceWrapper device)
{
    log("StopCharge() called", "trace")

    if( !stay_logged_in ) {
        authorize()
    }

    def uri = API_URL + "evc/rcstp"
    API_Headers.referer = "https://mybluelink.ca/login"
    def headers = API_Headers
    headers.put('accessToken', state.access_token)
    headers.put('vehicleId', state.vehicleId)
    headers.put('pAuth', getPinToken(device))    
    headers.put('offset', '-5')
    headers.put('REFRESH', refresh.toString())
    int valTimeout = refresh ? 240 : 30 //timeout in sec.
    def thePin = ["pin": bluelink_pin]

    def params = [uri: uri, headers: headers, requestContentType: "application/json", body: thePin, ignoreSSLIssues: true, timeout: valTimeout]  
    
    log("StopCharge params ${params}", "debug")

    LinkedHashMap  reJson = []

    try
    {
        httpPost(params) { response ->
            def reCode = response.getStatus()
            reJson = response.getData()
            log("StopCharge reCode: ${reCode}", "debug")
            log("StopCharge reJson: ${reJson}", "debug")

            if (reCode == 200) {
                log("StopCharge successful.","info")
                sendEventHelper(device, "StopCharge", true)                
            }
            else {
                sendEventHelper(device, "StopCharge", false)
            }
        }
    }
    catch (groovyx.net.http.HttpResponseException e)
    {
        if (e.getStatusCode() == 401 && !retry)
        {
            log('Authorization token expired, will refresh and retry.', 'warn')
            refreshToken()          
            StopCharge(device)
        }
        log("StopCharge failed -- ${e.getLocalizedMessage()}: ${e.response.data}", "error")
    }    

}

void transactionStatus(com.hubitat.app.DeviceWrapper device, pAuth)
{//Not used for now 
    log("transactionStatus() called", "trace")
    unschedule()

//    if( !stay_logged_in ) {
//      authorize()
//  }
    def uri = API_URL + "rmtsts"
    API_Headers.referer = "https://mybluelink.ca/remote/climate"
    def headers = API_Headers
    headers.put('accessToken', state.access_token)
    headers.put('vehicleId', state.vehicleId)
    headers.put('pAuth', getPinToken(device))
    headers.put('transactionId', state.transactionId.elements[0].name )
    headers.put('offset', '-5')
    headers.put('REFRESH', refresh.toString())
    int valTimeout = refresh ? 240 : 30 //timeout in sec.
    sendEventHelper(device, "transactionStatus", false)

    def params = [ uri: uri, headers: headers, timeout: valTimeout ]    
    log("testProc params ${params}", "debug")

    try
    {
        httpPost(params) { response ->
            def reCode = response.getStatus()
            def reJson = response.getData()
            log("transactionStatus reCode: ${reCode}", "debug")
            log("transactionStatus reJson: ${reJson}", "debug")

            if (reCode == 200) {
                if (reJson.result.transaction.apiStatusCode == "null") {
//jbjb                    
log("transactionStatus runIn:", "debug")
                    runIn(6, callTransactionStatus)
                }
                else {
                    log("transactionStatus2 successful.","info")
                }
            }
        }
    }
    catch (groovyx.net.http.HttpResponseException e)
    {
        if (e.getStatusCode() == 401 && !retry)
        {
            log('Authorization token expired, will refresh and retry.', 'warn')
            refreshToken()          
            ClimStop(device)
        }
        log("ClimStop failed -- ${e.getLocalizedMessage()}: ${e.response.data}", "error")
    }    

}

void getChargeLimits(com.hubitat.app.DeviceWrapper device)
{
    log("getChargeLimits() called", "trace")

    def uri = API_URL + "evc/selsoc"
    API_Headers.referer = "https://mybluelink.ca/login"
    def headers = API_Headers
    headers.put('accessToken', state.access_token)
    headers.put('vehicleId', state.vehicleId)
    headers.put('offset', '-5')
    headers.put('REFRESH', refresh.toString())
    int valTimeout = refresh ? 240 : 30 //timeout in sec.
    def params = [ uri: uri, headers: headers, timeout: valTimeout ]
    log("getChargeLimits params ${params}", "debug")

    //add error checking
    LinkedHashMap  reJson = []

    try
    {
        httpPost(params) { response ->
            def reCode = response.getStatus()
            reJson = response.getData()
            log("getChargeLimits reCode: ${reCode}", "debug")
            log("getChargeLimits reJson: ${reJson}", "debug")
        }

        // Update relevant device attributes
        sendEvent(device, [name: 'ACLevel', value: reJson.result[0].level])
        sendEvent(device, [name: 'ACRange', value: reJson.result[0].dte.rangeByFuel.totalAvailableRange.value])
        sendEvent(device, [name: 'DCLevel', value: reJson.result[1].level])
        sendEvent(device, [name: 'DCRange', value: reJson.result[1].dte.rangeByFuel.totalAvailableRange.value])

    }
    catch (groovyx.net.http.HttpResponseException e)
    {
        if (e.getStatusCode() == 401 && !retry)
        {
            log('Authorization token expired, will refresh and retry.', 'warn')
            refreshToken()          
            getChargeLimits(device, pcmdACLevel, pcmdDCLevel)
        }
        log("getChargeLimits failed -- ${e.getLocalizedMessage()}: ${e.response.data}", "error")
    }
}


void setChargeLimits(com.hubitat.app.DeviceWrapper device, pcmdACLevel, pcmdDCLevel)
{
    log("setChargeLimits() called", "trace")

    if( !stay_logged_in ) {
        authorize()
    }

    def uri = API_URL + "evc/setsoc"
    API_Headers.referer = "https://mybluelink.ca/login"
    def headers = API_Headers
    headers.put('accessToken', state.access_token)
    headers.put('vehicleId', state.vehicleId)
    headers.put('pAuth', getPinToken(device))
    headers.put('offset', '-5')
    headers.put('REFRESH', refresh.toString())
    int valTimeout = refresh ? 240 : 30 //timeout in sec.
    def theBody = ["pin": bluelink_pin, "tsoc": [["level": pcmdDCLevel, "plugType": "0"], ["level": pcmdACLevel, "plugType": "1"]]]
    def params = [uri: uri, headers: headers, requestContentType: "application/json", body: theBody, ignoreSSLIssues: true, timeout: 120]  
    log("setChargeLimits params ${params}", "debug")

    //add error checking
    LinkedHashMap  reJson = []

    try
    {
        httpPost(params) { response ->
            def reCode = response.getStatus()
            reJson = response.getData()
            log("setChargeLimits reCode: ${reCode}", "debug")
            log("setChargeLimits reJson: ${reJson}", "debug")
        }

    }
    catch (groovyx.net.http.HttpResponseException e)
    {
        if (e.getStatusCode() == 401 && !retry)
        {
            log('Authorization token expired, will refresh and retry.', 'warn')
            refreshToken()          
            setChargeLimits(device, pcmdACLevel, pcmdDCLevel)
        }
        log("setChargeLimits failed -- ${e.getLocalizedMessage()}: ${e.response.data}", "error")
    }

}

///
// Supporting helpers
///
private void sendEventHelper(com.hubitat.app.DeviceWrapper device, String sentCommand, Boolean result)
{
    log("sendEventHelper() called", "trace")
    String strResult = result ? "successfully sent to vehicle" : "sent to vehicle - error returned"
    String strDesc = "Command ${sentCommand} ${strResult}"
    String strVal = result ? "Successful" : "Error"
    sendEvent(device, [name: sentCommand, value: strVal, descriptionText: strDesc, isStateChange: true])
}

private Boolean LockUnlockHelper(com.hubitat.app.DeviceWrapper device, String urlSuffix, Boolean retry=false)
{
    log("LockUnlockHelper() called", "trace")

    if( !stay_logged_in ) {
        authorize()
    }

    def uri = API_URL + urlSuffix
    API_Headers.referer = "https://mybluelink.ca/login"
    def headers = API_Headers
    headers.put('accessToken', state.access_token)
    headers.put('vehicleId', state.vehicleId)  
    headers.put('pAuth', getPinToken(device))

    headers.put('offset', '-5')
    def thePin = ["pin": bluelink_pin]
    def params = [uri: uri, headers: headers, requestContentType: "application/json", body: thePin, ignoreSSLIssues: true, timeout: 120]  
    log("LockUnlockHelper ${params}", "debug")

    int reCode = 0

    try
    {
        httpPost(params) { response ->
            reCode = response.getStatus()
            state.transactionId = response.headers['transactionId']    
        }
    }
    catch (groovyx.net.http.HttpResponseException e)
    {
        if (e.getStatusCode() == 401 && !retry)
        {
            log('Authorization token expired, will refresh and retry.', 'warn')
            refreshToken()
            LockUnlockHelper(device, urlSuffix, true)
        }
        log("LockUnlockHelper failed -- ${e.getLocalizedMessage()}: Status: ${e.response.getStatus()}", "error")
    }
    return (reCode == 200)
}

private void listDiscoveredVehicles() {
    def children = getChildDevices()
    def builder = new StringBuilder()
    builder << "<ul>"
    children.each {
        if (it != null) {
            builder << "<li><a href='/device/edit/${it.getId()}'>${it.getLabel()}</a></li>"
        }
    }
    builder << "</ul>"
    def theCars = builder.toString()
    if (!children.isEmpty())
    {
        section {
            paragraph "Discovered vehicles are listed below:"
            paragraph theCars
        }
    }
}


private LinkedHashMap<String, String> getDefaultHeaders(com.hubitat.app.DeviceWrapper device) {
    log("getDefaultHeaders() called", "trace")

    LinkedHashMap<String, String> theHeaders = []
    try {
        String theVIN = device.currentValue("VIN")
        String regId = device.currentValue("RegId")
        String generation = device.currentValue("vehicleGeneration")
        String brand = device.currentValue("brandIndicator")
        theHeaders = [
                'access_token' : state.access_token,
                'client_id'   : client_id,
                'language'    : '0',
                'vin'         : theVIN,
                'APPCLOUD-VIN' : theVIN,
                'username' : user_name,
                'registrationId' : regId,
                'gen' : generation,
                'to' : 'ISS',
                'from' : 'SPA',
                'encryptFlag' : 'false',
                'bluelinkservicepin' : bluelink_pin,
                'brandindicator' : brand
        ]
    } catch(Exception e) {
        log("Unable to generate API headers - Did you fill in all required information?", "error")
    }

    return theHeaders
}

private com.hubitat.app.ChildDeviceWrapper CreateChildDriver(String Name, String Vin)
{
    log("CreateChildDriver called", "trace")
    String vehicleNetId = "Hyundai_" + Vin
    com.hubitat.app.ChildDeviceWrapper newDevice = null
    try {
            newDevice = addChildDevice(
                'jbilodea',
                'Canadian Hyundai Bluelink Driver',
                vehicleNetId,
                [
                        name : "Canadian Hyundai Bluelink Driver",
                        label: Name
                ])
    }
    catch (com.hubitat.app.exception.UnknownDeviceTypeException e) {
        log("${e.message} - you need to install the appropriate driver.", "info")
    }
    catch (IllegalArgumentException e) {
        //Intentionally ignored.  Expected if device id already exists in HE.
        log("Ignored: ${e.message}", "trace")
    }
    return newDevice
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
    data = "-- ${app.label} -- ${data ?: ''}"

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

// concept stolen bptworld, who stole from @Stephack Code
def getFormat(type, myText="") {
    if(type == "header-green") return "<div style='color:#ffffff; border-radius: 5px 5px 5px 5px; font-weight: bold; padding-left: 10px; background-color:#81BC00; border: 1px solid;box-shadow: 2px 3px #A9A9A9'>${myText}</div>"
    if(type == "header-light-grey") return "<div style='color:#000000; border-radius: 5px 5px 5px 5px; font-weight: bold; padding-left: 10px; background-color:#D8D8D8; border: 1px solid;box-shadow: 2px 3px #A9A9A9'>${myText}</div>"
    if(type == "header-blue-grad") return "<div style='color:#000000; border-radius: 5px 5px 5px 5px; line-height: 2.0; font-weight: bold; padding-left: 10px; background: linear-gradient(to bottom, #d4e4ef 0%,#86aecc 100%);  border: 2px'>${myText}</div>"
    if(type == "header-center-blue-grad") return "<div style='text-align:center; color:#000000; border-radius: 5px 5px 5px 5px; font-weight: bold; padding-left: 10px; background: linear-gradient(to bottom, #d4e4ef 0%,#86aecc 100%);  border: 2px'>${myText}</div>"
    if(type == "item-light-grey") return "<div style='color:#000000; border-radius: 5px 5px 5px 5px; font-weight: normal; padding-left: 10px; background-color:#D8D8D8; border: 1px solid'>${myText}</div>"
    if(type == "line") return "<hr style='background-color:#1A77C9; height: 1px; border: 0;'>"
    if(type == "title") return "<h2 style='color:#1A77C9;font-weight: bold'>${myText}</h2>"
}

