/**
 *  Hyundai Bluelink Application - CLOUDFLARE FIX v1.2.7
 *
 *  Author:         Tim Yuhl & Jean Bilodeau for Canadian version
 *  Modified:       2026-01-16 - Added exponential backoff for Cloudflare rate limits
 *
 *  Changes in v1.2.7:
 *  - Added exponential backoff strategy for Cloudflare rate limits
 *    Cooldown periods: 30min -> 1h -> 2h -> 4h -> 8h -> 16h -> 24h (max)
 *  - Cooldown level resets on successful authentication
 *  - Better logging with cooldown level and time remaining
 *
 *  Changes in v1.2.6:
 *  - CRITICAL FIX: Get PIN token BEFORE building headers in all commands
 *    This ensures that if getPinToken triggers re-auth, the fresh token is used
 *  - Fixed: setChargeLimits, ClimFavoritesStart, ClimManual, ClimStop,
 *    StartCharge, StopCharge, LockUnlockHelper, getLocation
 *
 *  Changes in v1.2.5:
 *  - Fixed authResponse to properly handle error 7602 (token deleted)
 *  - Fixed refreshToken to fall back to full auth when needed
 *  - Added getChargeLimits() call in getVehicleStatus()
 *  - Better error propagation - stop operations when auth fails
 *  - Fixed getPinToken to handle errors properly
 *  - Added token validation before API calls
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
 */
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import groovy.transform.Field
import java.security.MessageDigest
static String appVersion()   { return "1.2.7" }
def setVersion(){
    state.name = "Hyundai Bluelink Application"
    state.version = "1.2.7"
    state.transactionId = ""
}
@Field static String global_apiURL = "https://mybluelink.ca"
@Field static String API_URL = "https://mybluelink.ca/tods/api/"
@Field static String client_id = "m66129Bb-em93-SPAHYN-bZ91-am4540zp19920"
@Field static String client_secret = "v558o935-6nne-423i-baa8"
// UPDATED: Modern headers to bypass Cloudflare detection
@Field static Map API_Headers = [
    "content-type": "application/json",
    "accept": "application/json, text/plain, */*",
    "accept-encoding": "gzip, deflate, br",
    "accept-language": "en-US,en;q=0.9,fr-CA;q=0.8,fr;q=0.7",
    "user-agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
    "Host": "mybluelink.ca",
    "Connection": "keep-alive",
    "origin": "https://mybluelink.ca",
    "referer": "https://mybluelink.ca/login",
    "from": "CWP",
    "language": "0",
    "offset": "-5",
    "sec-fetch-dest": "empty",
    "sec-fetch-mode": "cors",
    "sec-fetch-site": "same-origin",
    "sec-ch-ua": "\"Google Chrome\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"",
    "sec-ch-ua-mobile": "?0",
    "sec-ch-ua-platform": "\"Windows\"",
    "cache-control": "no-cache",
    "pragma": "no-cache"
]
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
// NEW: Generate unique but consistent deviceId
def generateDeviceId() {
    if (!state.deviceId) {
        def seed = "${location.hub.id}-${user_name}-hubitat"
        def hash = MessageDigest.getInstance("MD5").digest(seed.bytes)
        state.deviceId = hash.encodeHex().toString().take(64)
    }
    return state.deviceId
}
// NEW: Rate limiting delay to avoid Cloudflare detection
def rateLimitDelay() {
    def delay = 3000 + new Random().nextInt(3000) // 3-6 seconds random
    pauseExecution(delay)
}
// Exponential backoff strategy for Cloudflare rate limits (in seconds)
@Field static ArrayList BACKOFF_STRATEGY = [
    1800,   // 30 minutes - first attempt
    3600,   // 1 hour
    7200,   // 2 hours
    14400,  // 4 hours
    28800,  // 8 hours
    57600,  // 16 hours
    86400   // 24 hours - maximum
]

// Check if we're in a rate limit cooldown (with exponential backoff)
def isRateLimited() {
    if (state.lastRateLimitTime) {
        def cooldownLevel = state.cooldownLevel ?: 0
        def cooldownSeconds = BACKOFF_STRATEGY[Math.min(cooldownLevel, BACKOFF_STRATEGY.size() - 1)]
        def timeSinceSeconds = (now() - state.lastRateLimitTime) / 1000

        if (timeSinceSeconds < cooldownSeconds) {
            def remainingSeconds = cooldownSeconds - timeSinceSeconds
            def remainingMinutes = Math.round(remainingSeconds / 60)
            def cooldownMinutes = Math.round(cooldownSeconds / 60)
            log("Still in rate limit cooldown (level ${cooldownLevel + 1}/${BACKOFF_STRATEGY.size()}). ${remainingMinutes} of ${cooldownMinutes} minutes remaining", "warn")
            return true
        } else {
            // Cooldown period has passed - reset for next potential rate limit
            log("Cooldown period ended. Resuming normal operations.", "info")
            // Don't reset cooldownLevel here - it will be reset on successful API call
        }
    }
    return false
}

// Call this on successful API calls to reset the cooldown level
def resetCooldown() {
    if (state.cooldownLevel > 0) {
        log("Successful API call - resetting cooldown level from ${state.cooldownLevel} to 0", "info")
    }
    state.cooldownLevel = 0
    state.lastRateLimitTime = null
}

// Call this when rate limited to increase the backoff level
def triggerRateLimitCooldown() {
    def currentLevel = state.cooldownLevel ?: 0
    def newLevel = Math.min(currentLevel + 1, BACKOFF_STRATEGY.size() - 1)
    state.cooldownLevel = newLevel
    state.lastRateLimitTime = now()

    def cooldownSeconds = BACKOFF_STRATEGY[newLevel]
    def cooldownMinutes = Math.round(cooldownSeconds / 60)
    def cooldownHours = cooldownMinutes / 60

    def timeDisplay = cooldownMinutes >= 60 ? "${cooldownHours} hours" : "${cooldownMinutes} minutes"
    log("RATE LIMITED: Entering cooldown level ${newLevel + 1}/${BACKOFF_STRATEGY.size()} for ${timeDisplay}", "error")
}
// NEW: Check if we have a valid token
def hasValidToken() {
    return (state.access_token != null && state.access_token != "")
}
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
            paragraph "<b>Version ${appVersion()}</b> - Cloudflare bypass enabled"
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
def getDebugLink() {
    section{
        href(
                name       : 'debugHref',
                title      : 'Debug buttons',
                page       : 'debugPage',
                description: 'Access debug buttons (refresh token, initialize, clear cookies)'
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
            input 'forceLogin', 'button', title: 'Force Full Login', submitOnChange: true
        }
        section {
            input 'initialize', 'button', title: 'initialize', submitOnChange: true
        }
        section {
            input 'clearCookies', 'button', title: 'Clear Cookies & Session', submitOnChange: true
        }
        section {
            paragraph "<strong>Current Status:</strong>"
            paragraph "Access Token: ${state.access_token ? 'Present' : 'Missing'}"
            paragraph "Refresh Token: ${state.refresh_token ? 'Present' : 'Missing'}"
            paragraph "Session Cookies: ${state.sessionCookies ? 'Present' : 'Missing'}"
        }
    }
}
def appButtonHandler(btn) {
    switch (btn) {
        case 'discover':
            if (authorize()) {
                rateLimitDelay()
                getVehicles()
            }
            break
        case 'refreshToken':
            refreshToken()
            break
        case 'forceLogin':
            state.access_token = null
            state.refresh_token = null
            authorize()
            break
        case 'initialize':
            initialize()
            break
        case 'clearCookies':
            state.sessionCookies = null
            state.deviceId = null
            state.access_token = null
            state.refresh_token = null
            log("Cookies, tokens and session cleared", "info")
            break
        default:
            log("Invalid Button In Handler", "error")
    }
}
void installed() {
    log("Installed with settings: ${settings}", "trace")
    stay_logged_in = true
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
    generateDeviceId()

    if(stay_logged_in && (state.refresh_token != null)) {
        refreshToken()
    }
}
// MODIFIED: Now returns Boolean to indicate success/failure
Boolean authorize() {
    log("authorize called - Using Cloudflare bypass", "info")

    unschedule()

    API_Headers = [
        "content-type": "application/json;charset=UTF-8",
        "accept": "application/json, text/plain, */*",
        "accept-encoding": "gzip, deflate, br",
        "accept-language": "en-US,en;q=0.9,fr-CA;q=0.8,fr;q=0.7",
        "user-agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        "Host": "mybluelink.ca",
        "Connection": "keep-alive",
        "Deviceid": generateDeviceId(),
        "origin": "https://mybluelink.ca",
        "referer": "https://mybluelink.ca/login",
        "from": "CWP",
        "language": "0",
        "offset": "-5",
        "sec-fetch-dest": "empty",
        "sec-fetch-mode": "cors",
        "sec-fetch-site": "same-origin",
        "sec-ch-ua": "\"Google Chrome\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"",
        "sec-ch-ua-mobile": "?0",
        "sec-ch-ua-platform": "\"Windows\"",
        "cache-control": "no-cache",
        "pragma": "no-cache"
    ]

    if (state.sessionCookies) {
        API_Headers.put("Cookie", state.sessionCookies)
    }
    def body = [
        "loginId": user_name,
        "password": user_pwd
    ]
    def url = API_URL + "v2/login" 
    API_Headers.referer = "https://mybluelink.ca/login"

    int valTimeout = 90
    def params = [
        uri: url,
        headers: API_Headers,
        timeout: valTimeout,
        requestContentType: "application/json",
        body: body,
        ignoreSSLIssues: true
    ]  

    log("Attempting login with Cloudflare bypass...", "info")
    Boolean success = false

    try {
        httpPost(params) { response ->
            success = authResponse(response)
            def cookies = response.headers['Set-Cookie']
            if (cookies) {
                state.sessionCookies = cookies
                log.debug "✓ Session cookies saved"
            }
        }
    } catch (groovyx.net.http.HttpResponseException e) {
        if (e.getStatusCode() == 429) {
            log("RATE LIMIT ERROR (429): You are being rate limited by Cloudflare.", "error")
            triggerRateLimitCooldown()
        } else if (e.getStatusCode() == 403) {
            log("ACCESS DENIED (403): Cloudflare is blocking your requests. You may need to clear cookies or wait.", "error")
        } else {
            log("Login failed -- ${e.getLocalizedMessage()}: ${e.response.data}", "error")
        }
        success = false
    } catch (java.net.SocketTimeoutException e) {
        log("Connection timeout - Cloudflare may be challenging the request", "error")
        success = false
    }

    return success
}
// MODIFIED: Now returns Boolean to indicate success/failure
Boolean refreshToken(Boolean isRetry=false) {
    log("refreshToken called", "trace")
    if (state.refresh_token != null) {       
        def body = [
            refresh_token: state.refresh_token
        ]
        def url = API_URL + "lgn"
        API_Headers.referer = "https://mybluelink.ca/login"
        API_Headers.put("Deviceid", generateDeviceId())
        if (state.sessionCookies) {
            API_Headers.put("Cookie", state.sessionCookies)
        }
        def params = [
            uri: url,
            headers: API_Headers,
            requestContentType: "application/json",
            body: body,
            ignoreSSLIssues: true,
            timeout: 90
        ]  
        rateLimitDelay()
        Boolean success = false
        try {
            httpPost(params) { response ->
                success = authResponse(response)
                def cookies = response.headers['Set-Cookie']
                if (cookies) {
                    state.sessionCookies = cookies
                    log.debug "✓ Cookies refreshed"
                }
            }

            // If refresh failed (token deleted), try full auth
            if (!success && !isRetry) {
                log("Token refresh failed, attempting full re-authentication", "warn")
                pauseExecution(3000)
                return authorize()
            }

            return success

        } catch (java.net.SocketTimeoutException e) {
            if (!isRetry) {
                log("Socket timeout, will retry refresh token", "info")
                pauseExecution(5000)
                return refreshToken(true)
            }
            return false
        } catch (groovyx.net.http.HttpResponseException e) {
            if (e.getStatusCode() == 429) {
                log("RATE LIMITED during token refresh", "error")
                triggerRateLimitCooldown()
                return false
            } else {
                log("Token refresh failed -- ${e.getLocalizedMessage()}: ${e.response.data}", "error")
                // Try full auth on failure
                if (!isRetry) {
                    log("Attempting full re-authentication", "warn")
                    pauseExecution(3000)
                    return authorize()
                }
                return false
            }
        }
    } else {
        log("No refresh token available, attempting full authentication.", "warn")
        return authorize()
    }
}
// MODIFIED: Now returns Boolean to indicate success/failure
Boolean authResponse(response) {
    log("authResponse called", "info")
    unschedule()
    def reCode = response.getStatus()
    def reJson = response.getData()
    log("reCode: ${reCode}", "debug")
    log("reJson: ${reJson}", "debug")
    def cookies = response.headers['Set-Cookie']
    if (cookies) {
        state.sessionCookies = cookies
        log.debug "✓ Auth cookies saved"
    }
    // FIXED: Check for errors FIRST before processing success
    if (reJson.containsKey('error')) {
        log("reJsonerror: ${reJson.error}", "debug")
        if (reJson.error.containsKey('errorCode')) {
            log("reJsonerrorCode: ${reJson.error.errorCode}", "debug")

            // Token deleted - clear tokens and indicate failure
            if (reJson.error.errorCode == "7602") {
                log("Token deleted (7602) - need full re-authentication", "warn")
                state.access_token = null
                state.refresh_token = null
                return false
            }

            // Login incorrect
            if (reJson.error.errorCode == "7404") {
                log("Login incorrect (7404) - check username/password", "error")
                state.access_token = null
                state.refresh_token = null
                return false
            }
        }
        return false
    }
    // FIXED: Check that result and token exist before accessing
    if (reCode == 200 && reJson?.result?.token?.accessToken) {
        state.access_token = reJson.result.token.accessToken
        state.refresh_token = reJson.result.token.refreshToken
        Integer expireTime = reJson.result.token.expireIn - 180
        log("✓ Token obtained successfully, Next refresh in: ${expireTime} sec", "info")
        // Reset cooldown on successful authentication
        resetCooldown()
        if (stay_logged_in) {
            runIn(expireTime, refreshToken)
        }
        return true
    } else {
        log("LoginResponse Failed - No token in response. HTTP Status: ${reCode}", "error")
        return false
    }
}
// Helper to ensure we have valid auth before API calls
Boolean ensureAuthenticated() {
    if (!hasValidToken()) {
        log("No valid token, attempting authentication", "info")
        return authorize()
    }
    return true
}
def getVehicles(Boolean retry=false) {
    log("getVehicles called", "trace")
    if (!ensureAuthenticated()) {
        log("Cannot get vehicles - authentication failed", "error")
        return
    }
    rateLimitDelay()
    def uri = API_URL + "vhcllst"
    API_Headers.referer = "https://mybluelink.ca/login"
    def headers = API_Headers
    headers.put('accessToken', state.access_token)
    headers.put("Deviceid", generateDeviceId())
    if (state.sessionCookies) {
        headers.put("Cookie", state.sessionCookies)
    }
    def params = [
        uri: uri,
        headers: headers,
        requestContentType: "application/json",
        ignoreSSLIssues: true,
        timeout: 90
    ]
    LinkedHashMap reJson = []
    try {
        httpPost(params) { response ->
            def reCode = response.getStatus()
            reJson = response.getData()
            log("reCodegetVeh: ${reCode}", "debug")
            log("reJsongetVeh: ${reJson}", "debug")
            def cookies = response.headers['Set-Cookie']
            if (cookies) {
                state.sessionCookies = cookies
                log.debug "✓ Cookies saved (getVehicles)"
            }
        }

        // Check for API errors in response
        if (reJson.containsKey('error')) {
            log("getVehicles API error: ${reJson.error.errorDesc} (${reJson.error.errorCode})", "error")
            if ((reJson.error.errorCode == "7602" || reJson.error.errorCode == "7404" || reJson.error.errorCode == "7606") && !retry) {            
                log('Token issue, will refresh and retry.', 'warn')
                if (authorize()) {
                    pauseExecution(3000)
                    getVehicles(true)
                }
            }
            return
        }

    } catch (groovyx.net.http.HttpResponseException e) {
        if (e.getStatusCode() == 429) {
            log("RATE LIMITED: Wait 15-30 minutes before trying again", "error")
            return
        } else if (e.getStatusCode() == 401 && !retry) {
            log('Authorization token expired, will refresh and retry.', 'warn')
            if (refreshToken()) {
                pauseExecution(3000)
                getVehicles(true)
            }
        } else {
            log("getVehicles failed -- ${e.getLocalizedMessage()}: ${e.response.data}", "error")
        }
        return
    }
    if (reJson.result?.vehicles == null) {
        log("No enrolled vehicles found.", "info")
    } else {
        reJson.result.vehicles.each { vehicle ->
            log("Found vehicle: ${vehicle.nickName} with VIN: ${vehicle.vin}", "info")
            def newDevice = CreateChildDriver(vehicle.nickName, vehicle.vin)
            if (newDevice != null) {
                state.vehicleId = vehicle.vehicleId
                sendEvent(newDevice, [name: "NickName", value: vehicle.nickName])
                sendEvent(newDevice, [name: "VIN", value: vehicle.vin])
                sendEvent(newDevice, [name: "modelCode", value: vehicle.modelCode])
                sendEvent(newDevice, [name: "modelName", value: vehicle.modelName])
                sendEvent(newDevice, [name: "modelYear", value: vehicle.modelYear])
                sendEvent(newDevice, [name: "fuelKindCode", value: vehicle.fuelKindCode])
                sendEvent(newDevice, [name: "trim", value: vehicle.trim])
                sendEvent(newDevice, [name: "exteriorColor", value: vehicle.exteriorColor])
                sendEvent(newDevice, [name: "subscriptionStatus", value: vehicle.subscriptionStatus])
                sendEvent(newDevice, [name: "subscriptionEndDate", value: vehicle.subscriptionEndDate])
                sendEvent(newDevice, [name: "mileageForNextService", value: vehicle.mileageForNextService])
                sendEvent(newDevice, [name: "daysForNextService", value: vehicle.daysForNextService])
                sendEvent(newDevice, [name: "overviewMessage", value: vehicle.overviewMessage])
                sendEvent(newDevice, [name: "valetParkingModeOn", value: vehicle.valetParkingModeOn])
            }
        }
    }
}
void getVehicleStatus(com.hubitat.app.DeviceWrapper device, Boolean forceRefresh = false, Boolean retry=false)
{
    log("getVehicleStatus() called with forceRefresh=${forceRefresh}", "trace")

    // NEW: Check rate limit cooldown
    if (isRateLimited()) {
        log("Skipping refresh - in rate limit cooldown", "warn")
        return
    }

    if (!stay_logged_in) {
        if (!authorize()) {
            log("Cannot get vehicle status - authentication failed", "error")
            return
        }
    }

    // Verify we have a valid token
    if (!hasValidToken()) {
        log("No valid token, attempting to authenticate", "warn")
        if (!authorize()) {
            log("Authentication failed, cannot get vehicle status", "error")
            return
        }
    }

    rateLimitDelay()

    if (forceRefresh) {
        log.info "Force refresh requested - will ping vehicle then read cache"

        def pingUri = API_URL + "rltmvhclsts"
        def pingHeaders = [
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
            "content-type": "application/json",
            "accept": "application/json",
            "accept-encoding": "gzip, deflate, br",
            "host": "mybluelink.ca",
            "client_id": "HATAHSPACA0232141ED9722C67715A0B",
            "client_secret": "CLISCR01AHSPA",
            "from": "SPA",
            "language": "0",
            "offset": "-5",
            "accessToken": state.access_token,
            "vehicleId": state.vehicleId,
            "Deviceid": generateDeviceId()
        ]

        if (state.sessionCookies) {
            pingHeaders.put("Cookie", state.sessionCookies)
        }

        try {
            def pingParams = [ uri: pingUri, headers: pingHeaders, timeout: 90 ]
            log.info "Pinging vehicle (may take 30-60 seconds)..."

            rateLimitDelay()

            httpPost(pingParams) { response ->
                log.info "✓ Vehicle pinged successfully"
                def cookies = response.headers['Set-Cookie']
                if (cookies) {
                    state.sessionCookies = cookies
                }
            }

            pauseExecution(5000)

        } catch (groovyx.net.http.HttpResponseException e) {
            if (e.getStatusCode() == 429) {
                log.error "RATE LIMITED during vehicle ping"
                return
            }
            log.warn "Vehicle ping failed: ${e.message}"
        }
    }

    def uri = API_URL + "lstvhclsts"

    def headers = [
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        "content-type": "application/json",
        "accept": "application/json",
        "accept-encoding": "gzip, deflate, br",
        "host": "mybluelink.ca",
        "client_id": "HATAHSPACA0232141ED9722C67715A0B",
        "client_secret": "CLISCR01AHSPA",
        "from": "SPA",
        "language": "0",
        "offset": "-5",
        "accessToken": state.access_token,
        "vehicleId": state.vehicleId,
        "Deviceid": generateDeviceId()
    ]

    if (state.sessionCookies) {
        headers.put("Cookie", state.sessionCookies)
    }

    int valTimeout = 60
    def params = [ uri: uri, headers: headers, timeout: valTimeout ]

    log.debug "Reading vehicle status from cache"

    rateLimitDelay()

    LinkedHashMap reJson = []
    try
    {
        httpPost(params) { response ->
            def reCode = response.getStatus()
            reJson = response.getData()

            def cookies = response.headers['Set-Cookie']
            if (cookies) {
                state.sessionCookies = cookies
            }

            log.debug "reCode: ${reCode}"

            // Check for API errors
            if (reJson.containsKey('error')) {
                log("getVehicleStatus API error: ${reJson.error.errorDesc} (${reJson.error.errorCode})", "warn")
                if ((reJson.error.errorCode == "7602" || reJson.error.errorCode == "7404" || reJson.error.errorCode == "7606" || reJson.error.errorCode == "7406") && !retry) {
                    log('Token issue (error ${reJson.error.errorCode}), will force full re-authentication and retry.', 'warn')
                    // Clear tokens to force full re-auth
                    state.access_token = null
                    state.refresh_token = null
                    if (authorize()) {
                        pauseExecution(3000)
                        getVehicleStatus(device, forceRefresh, true)
                    }
                }
                return
            }

            if (reJson?.result?.status?.evStatus) {
                log.info "✓ evStatus found in cached data"
                def lastUpdate = reJson.result.status.lastStatusDate
                log.info "Data age: ${lastUpdate}"
            }
        }

        // Only process if we have valid data
        if (!reJson?.result?.status) {
            log("No status data in response", "warn")
            return
        }

        sendEvent(device, [name: 'Engine', value: reJson.result.status.engine ? 'On' : 'Off'])
        sendEvent(device, [name: 'DoorLocks', value: reJson.result.status.doorLock ? 'Locked' : 'Unlocked'])
        sendEvent(device, [name: 'Trunk', value: reJson.result.status.trunkOpen ? 'Open' : 'Closed'])
        sendEvent(device, [name: "LastRefreshTime", value: Date.parse('yyyyMMddHHmmSS', reJson.result.status.lastStatusDate).format('E MMM dd HH:mm:ss z yyyy')])

        def batSoc = reJson?.result?.status?.battery?.batSoc
        if (batSoc != null) {
            sendEvent(device, [name: 'BatteryLevel', value: batSoc])
        }

        def evStatus = reJson?.result?.status?.evStatus
        if (evStatus != null) {
            log.info "Processing evStatus data"

            def batteryCharge = evStatus?.batteryCharge
            if (batteryCharge != null) {
                sendEvent(device, [name : 'BatteryInCharge', value: batteryCharge ? 'On' : 'Off'])
                log.info "✓ BatteryInCharge: ${batteryCharge ? 'On' : 'Off'}"
            }

            def batteryStatus = evStatus?.batteryStatus
            if (batteryStatus != null) {
                sendEvent(device, [name : 'BatteryPercent', value: batteryStatus])
                log.info "✓ BatteryPercent: ${batteryStatus}%"
            }

            def evRange = evStatus?.drvDistance?.getAt(0)?.rangeByFuel?.evModeRange?.value
            if (evRange != null) {
                sendEvent(device, [name: 'DCRange', value: evRange])
                log.info "✓ DCRange: ${evRange}"
            }

            def batteryPlugin = evStatus?.batteryPlugin
            if (batteryPlugin != null) {
                def plugStatus = ["Not Connected", "Slow Charge", "Fast Charge", "Portable"][batteryPlugin] ?: "Unknown"
                log.info "Battery Plugin: ${plugStatus} (${batteryPlugin})"
            }

            def remainTimeValue = evStatus?.remainTime2?.etc3?.value
            if (remainTimeValue != null && remainTimeValue > 0) {
                int hours = remainTimeValue / 60
                int minutes = remainTimeValue % 60
                def timeToDisplay = String.format("%02d:%02d", hours, minutes)
                sendEvent(device, [name: 'BatteryTimeToCharge', value: timeToDisplay])
                log.info "✓ BatteryTimeToCharge: ${timeToDisplay}"
            } else {
                sendEvent(device, [name: 'BatteryTimeToCharge', value: 'N/A'])
            }
        } else {
            log.warn "evStatus not found"
            sendEvent(device, [name: 'BatteryInCharge', value: 'N/A'])
            sendEvent(device, [name: 'BatteryTimeToCharge', value: 'N/A'])
        }
    }
    catch (groovyx.net.http.HttpResponseException e)
    {
        if (e.getStatusCode() == 429) {
            log.error "Rate limited (429) - Wait 15-30 minutes"
            return
        }
        else if (e.getStatusCode() == 401 && !retry)
        {
            log.warn 'Authorization token expired, will refresh and retry.'
            if (refreshToken()) {
                pauseExecution(2000)
                getVehicleStatus(device, forceRefresh, true)
            }
            return
        }
        log.error "getVehicleStatus failed -- ${e.getLocalizedMessage()}: ${e.response?.data}"
        return
    }
    catch (Exception e)
    {
        log.error "Unexpected error: ${e.message}"
        return
    }

// Call additional data fetchers - but only if we have valid token
    if (hasValidToken()) {
        pauseExecution(5000)  // 5 secondes au lieu de 2
        try { getNextService(device) } catch (Exception e) { log.warn "getNextService failed: ${e.message}" }

        pauseExecution(5000)  // 5 secondes au lieu de 2
        try { ClimFavoritesDisplay(device) } catch (Exception e) { log.warn "ClimFavoritesDisplay failed: ${e.message}" }

        // ADDED: Call getChargeLimits to get AC/DC levels
        pauseExecution(5000)  // 5 secondes au lieu de 2
        try { getChargeLimits(device) } catch (Exception e) { log.warn "getChargeLimits failed: ${e.message}" }
    }
}
void getNextService(com.hubitat.app.DeviceWrapper device, Boolean retry=false, Boolean refresh=false) {
    log("getNextService() called", "trace")
    if (!hasValidToken()) {
        log("getNextService: No valid token", "warn")
        return
    }
    rateLimitDelay()
    API_Headers.referer = "https://mybluelink.ca/login"
    def headers = API_Headers
    headers.put('accessToken', state.access_token)
    headers.put('vehicleId', state.vehicleId)
    headers.put('offset', '-5')
    headers.put('REFRESH', refresh.toString())
    headers.put('Deviceid', generateDeviceId())
    if (state.sessionCookies) {
        headers.put("Cookie", state.sessionCookies)
    }
    int valTimeout = refresh ? 240 : 60
    def uri = API_URL + "nxtsvc"
    def params = [ uri: uri, headers: headers, timeout: valTimeout ]
    LinkedHashMap reJson = []
    try {
        httpPost(params) { response ->
            def reCode = response.getStatus()
            reJson = response.getData()
            log("reCode NextServ: ${reCode}", "debug")
            log("reJson NextServ: ${reJson}", "debug")
            def cookies = response.headers['Set-Cookie']
            if (cookies) {
                state.sessionCookies = cookies
            }
        }
        // Check for API errors
        if (reJson.containsKey('error')) {
            log("getNextService API error: ${reJson.error.errorDesc} (${reJson.error.errorCode})", "warn")
            if ((reJson.error.errorCode == "7602" || reJson.error.errorCode == "7404" || reJson.error.errorCode == "7606") && !retry) {
                log('Token issue, will refresh and retry.', 'warn')
                if (authorize()) {
                    pauseExecution(2000)
                    getNextService(device, true, refresh)
                }
            }
            return
        }
        if (reJson?.result?.maintenanceInfo?.currentOdometer) {
            sendEvent(device, [name: 'Odometer', value: reJson.result.maintenanceInfo.currentOdometer])
        }
    } catch (groovyx.net.http.HttpResponseException e) {
        if (e.getStatusCode() == 429) {
            log.error "Rate limited during getNextService"
            return
        }
        if (e.getStatusCode() == 401 && !retry) {
            log('Authorization token expired, will refresh and retry.', 'warn')
            if (refreshToken()) {
                getNextService(device, true, refresh)
            }
        }
        log("getNextService(device) failed -- ${e.getLocalizedMessage()}: ${e.response.data}", "error")
    }
}
void getLocation(com.hubitat.app.DeviceWrapper device, Boolean refresh=false, Boolean retry=false) {
    log("getLocation() called", "trace")
    if (!stay_logged_in) {
        if (!authorize()) {
            log("Cannot get location - authentication failed", "error")
            sendEventHelper(device, "Location", false)
            return
        }
    }

    if (!hasValidToken()) {
        if (!authorize()) {
            sendEventHelper(device, "Location", false)
            return
        }
    }
    rateLimitDelay()

    // IMPORTANT: Get PIN token FIRST - it may trigger re-authentication
    def pAuth = getPinToken(device)
    if (!pAuth) {
        log("Cannot get location - failed to get PIN token", "error")
        sendEventHelper(device, "Location", false)
        return
    }
    // Build headers AFTER getPinToken (which may have refreshed the access token)
    def uri = API_URL + "fndmcr"
    API_Headers.referer = "https://mybluelink.ca/login"
    def headers = API_Headers
    headers.put('accessToken', state.access_token)  // Uses fresh token if re-auth happened
    headers.put('vehicleId', state.vehicleId)
    headers.put('offset', '-5')
    headers.put('pAuth', pAuth)
    headers.put('Deviceid', generateDeviceId())
    if (state.sessionCookies) {
        headers.put("Cookie", state.sessionCookies)
    }
    def thePin = ["pin": bluelink_pin]
    def params = [
        uri: uri,
        headers: headers,
        requestContentType: "application/json",
        body: thePin,
        ignoreSSLIssues: true,
        timeout: 120
    ]  
    LinkedHashMap reJson = []
    try {
        httpPost(params) { response ->
            int reCode = response.getStatus()
            reJson = response.getData()
            log("reCode getLocation: ${reCode}", "debug")
            log("reJson getLocation: ${reJson}", "debug")
            def cookies = response.headers['Set-Cookie']
            if (cookies) {
                state.sessionCookies = cookies
            }

            // Check for API errors
            if (reJson.containsKey('error')) {
                log("getLocation API error: ${reJson.error.errorDesc} (${reJson.error.errorCode})", "warn")
                sendEventHelper(device, "Location", false)
                return
            }
            if (reCode == 200 && reJson.result?.coord != null) {
                log("getLocation successful.","info")
                sendEventHelper(device, "Location", true)
                sendEvent(device, [name: 'locLatitude', value: reJson.result.coord.lat])
                sendEvent(device, [name: 'locLongitude', value: reJson.result.coord.lon])
                sendEvent(device, [name: 'locAltitude', value: reJson.result.coord.alt])
                sendEvent(device, [name: 'locSpeed', value: reJson.result.speed.value])
                sendEvent(device, [name: 'locUpdateTime', value: reJson.result.time])
            }
        }
    } catch (groovyx.net.http.HttpResponseException e) {
        if (e.getStatusCode() == 429) {
            log.error "Rate limited during getLocation"
            sendEventHelper(device, "Location", false)
            return
        }
        if (e.getStatusCode() == 401 && !retry) {
            log('Authorization token expired, will refresh and retry.', 'warn')
            if (refreshToken()) {
                getLocation(device, refresh, true)
            }
            return
        }
        log("getLocation failed -- ${e.getLocalizedMessage()}: Status: ${e.response.getStatus()}", "error")
        sendEventHelper(device, "Location", false)
    }
}
// MODIFIED: Now returns null on failure instead of potentially invalid pAuth
def getPinToken(com.hubitat.app.DeviceWrapper device, Boolean retry=false) {
    log("getPinToken() called", "trace")
    if (!hasValidToken()) {
        log("getPinToken: No valid access token", "warn")
        return null
    }
    rateLimitDelay()
    def uri = API_URL + "vrfypin"
    API_Headers.referer = "https://mybluelink.ca/remote/climate"
    def headers = API_Headers
    headers.put('accessToken', state.access_token)
    headers.put('vehicleId', state.vehicleId)
    headers.put('Deviceid', generateDeviceId())
    if (state.sessionCookies) {
        headers.put("Cookie", state.sessionCookies)
    }
    def thePin = [pin: bluelink_pin]

    def params = [
        uri: uri,
        headers: headers,
        requestContentType: "application/json",
        body: thePin,
        ignoreSSLIssues: true
    ]  
    LinkedHashMap reJson = []
    try {
        httpPost(params) { response ->
            int reCode = response.getStatus()
            reJson = response.getData()
            log("reCode getPinToken: ${reCode}", "debug")
            log("reJson getPinToken: ${reJson}", "debug")
            def cookies = response.headers['Set-Cookie']
            if (cookies) {
                state.sessionCookies = cookies
            }
        }

        // FIXED: Check for errors in response
        if (reJson.containsKey('error')) {
            log("getPinToken API error: ${reJson.error.errorDesc} (${reJson.error.errorCode})", "warn")
            if ((reJson.error.errorCode == "7602" || reJson.error.errorCode == "7404") && !retry) {
                log('Token issue in getPinToken, will refresh and retry.', 'warn')
                if (authorize()) {
                    pauseExecution(2000)
                    return getPinToken(device, true)
                }
            }
            return null
        }

        if (reJson?.result?.pAuth) {
            log("getPinToken successful.","info")
            return reJson.result.pAuth
        } else {
            log("getPinToken: No pAuth in response", "warn")
            return null
        }

    } catch (groovyx.net.http.HttpResponseException e) {
        if (e.getStatusCode() == 429) {
            log.error "Rate limited during getPinToken"
            return null
        }
        if (e.getStatusCode() == 401 && !retry) {
            log('Authorization token expired, will refresh and retry.', 'warn')
            if (refreshToken()) {
                return getPinToken(device, true)
            }
        }
        log("getPinToken failed -- ${e.getLocalizedMessage()}: Status: ${e.response.getStatus()}", "error")
        return null
    }
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
        log("Unlock call made to car -- can take some time to unlock", "info")
        sendEventHelper(device, "Unlock", true)
    }
}
void ClimFavoritesDisplay(com.hubitat.app.DeviceWrapper device, Boolean retry=false, Boolean refresh=false) {
    log("ClimFavoritesDisplay() called", "trace")
    if (!hasValidToken()) {
        log("ClimFavoritesDisplay: No valid token", "warn")
        return
    }
    rateLimitDelay()
    def uri = API_URL + "gtfvsttng"
    API_Headers.referer = "https://mybluelink.ca/charge"
    def headers = API_Headers
    headers.put('accessToken', state.access_token)
    headers.put('vehicleId', state.vehicleId)
    headers.put('offset', '-5')
    headers.put('REFRESH', refresh.toString())
    headers.put('Deviceid', generateDeviceId())
    if (state.sessionCookies) {
        headers.put("Cookie", state.sessionCookies)
    }
    int valTimeout = refresh ? 240 : 60
    def params = [ uri: uri, headers: headers, timeout: valTimeout ]
    LinkedHashMap reJson = []
    try {
        httpPost(params) { response ->
            def reCode = response.getStatus()
            reJson = response.getData()
            log("ClimFavoritesDisplay reCode: ${reCode}", "debug")
            log("ClimFavoritesDisplay reJson: ${reJson}", "debug")
            def cookies = response.headers['Set-Cookie']
            if (cookies) {
                state.sessionCookies = cookies
            }

            // Check for API errors FIRST
            if (reJson.containsKey('error')) {
                log("ClimFavoritesDisplay API error: ${reJson.error.errorDesc} (${reJson.error.errorCode})", "warn")
                if ((reJson.error.errorCode == "7602" || reJson.error.errorCode == "7404" || reJson.error.errorCode == "7606") && !retry) {
                    log('Token issue, will refresh and retry.', 'warn')
                    if (authorize()) {
                        pauseExecution(2000)
                        ClimFavoritesDisplay(device, true, refresh)
                    }
                }
                return
            }
            if (reCode == 200) {
                log("ClimFavoritesDisplay successful.","info")
            }

            if (reJson?.result && reJson.result[0] != null) {
                def tempHex1 = reJson.result[0].airTemp.value
                def tempIndex1 = tempHex1.substring(0,2)
                int index1 = Integer.parseInt(tempIndex1, 16) - 6
                def temperature1 = CA_TEMP_RANGE.get(index1)
                sendEvent(device, [name: 'Fav1-Name', value: reJson.result[0].settingName])
                sendEvent(device, [name: 'Fav1Defrost', value: reJson.result[0].defrost])                
                sendEvent(device, [name: 'Fav1Temp', value: temperature1])                
                sendEvent(device, [name: 'Fav1Heating', value: reJson.result[0].heating1])
            }
            if (reJson?.result && reJson.result[1] != null) {
                def tempHex2 = reJson.result[1].airTemp.value
                def tempIndex2 = tempHex2.substring(0,2)
                int index2 = Integer.parseInt(tempIndex2, 16) - 6
                def temperature2 = CA_TEMP_RANGE.get(index2)
                sendEvent(device, [name: 'Fav2-Name', value: reJson.result[1].settingName])
                sendEvent(device, [name: 'Fav2Defrost', value: reJson.result[1].defrost])                
                sendEvent(device, [name: 'Fav2Temp', value: temperature2])                
                sendEvent(device, [name: 'Fav2Heating', value: reJson.result[1].heating1])                
            }
            if (reJson?.result && reJson.result[2] != null) {
                def tempHex3 = reJson.result[2].airTemp.value
                def tempIndex3 = tempHex3.substring(0,2)
                int index3 = Integer.parseInt(tempIndex3, 16) - 6
                def temperature3 = CA_TEMP_RANGE.get(index3)
                sendEvent(device, [name: 'Fav3-Name', value: reJson.result[2].settingName])
                sendEvent(device, [name: 'Fav3Defrost', value: reJson.result[2].defrost])                
                sendEvent(device, [name: 'Fav3Temp', value: temperature3])                
                sendEvent(device, [name: 'Fav3Heating', value: reJson.result[2].heating1])                
            }
        }
    } catch (groovyx.net.http.HttpResponseException e) {
        if (e.getStatusCode() == 429) {
            log.error "Rate limited during ClimFavoritesDisplay"
            return
        }
        if (e.getStatusCode() == 401 && !retry) {
            log('Authorization token expired, will refresh and retry.', 'warn')
            if (refreshToken()) {
                ClimFavoritesDisplay(device, true, refresh)
            }
        }
        log("ClimFavoritesDisplay failed -- ${e.getLocalizedMessage()}: ${e.response.data}", "error")
    }
}
void ClimFavoritesStart(com.hubitat.app.DeviceWrapper device, pcmdFavoriteNbr, Boolean retry=false, Boolean refresh=false) {
    log("ClimFavoritesStart() called", "trace")
    if (!stay_logged_in) {
        if (!authorize()) {
            sendEventHelper(device, "ClimFavoritesStart", false)
            return
        }
    }

    if (!hasValidToken()) {
        if (!authorize()) {
            sendEventHelper(device, "ClimFavoritesStart", false)
            return
        }
    }
    rateLimitDelay()

    // IMPORTANT: Get PIN token FIRST - it may trigger re-authentication
    def pAuth = getPinToken(device)
    if (!pAuth) {
        log("Cannot start climate - failed to get PIN token", "error")
        sendEventHelper(device, "ClimFavoritesStart", false)
        return
    }
    // Build headers AFTER getPinToken (which may have refreshed the access token)
    def uri = API_URL + "evc/rfon"
    API_Headers.referer = "https://mybluelink.ca/remote/climate"
    def headers = API_Headers
    headers.put('accessToken', state.access_token)  // Uses fresh token if re-auth happened
    headers.put('vehicleId', state.vehicleId)
    headers.put('pAuth', pAuth)
    headers.put('offset', '-5')
    headers.put('REFRESH', refresh.toString())
    headers.put('Deviceid', generateDeviceId())
    if (state.sessionCookies) {
        headers.put("Cookie", state.sessionCookies)
    }
    int valTimeout = refresh ? 240 : 60
    def FavTemp = ""
    def FavDefrost = ""
    def FavHeating = ""
    if (pcmdFavoriteNbr == "1") {
        FavTemp = device.currentValue("Fav1Temp") 
        FavDefrost = device.currentValue("Fav1Defrost")
        FavHeating = device.currentValue("Fav1Heating")
    } else if (pcmdFavoriteNbr == "2") {
        FavTemp = device.currentValue("Fav2Temp") 
        FavDefrost = device.currentValue("Fav2Defrost")
        FavHeating = device.currentValue("Fav2Heating")
    } else if (pcmdFavoriteNbr == "3") {
        FavTemp = device.currentValue("Fav3Temp") 
        FavDefrost = device.currentValue("Fav3Defrost")
        FavHeating = device.currentValue("Fav3Heating")
    }

    def temperatureIndex = CA_TEMP_RANGE.indexOf(FavTemp) + 6
    def indexHex = Integer.toHexString(temperatureIndex).toUpperCase()
    if (indexHex.length() == 1) {
        indexHex = '0' + indexHex
    }
    indexHex = indexHex + "H"
    def hvacInfo = [
        "hvacInfo": [
            "airCtrl": 1, 
            "airTemp": [
                "hvacTempType": 1, 
                "unit": 0, 
                "value": indexHex
            ],
            "defrost": FavDefrost, 
            "heating1": FavHeating
        ], 
        "pin": bluelink_pin
    ]
    def params = [
        uri: uri,
        headers: headers,
        requestContentType: "application/json",
        body: hvacInfo,
        ignoreSSLIssues: true,
        timeout: valTimeout
    ]  
    LinkedHashMap reJson = []
    try {
        httpPost(params) { response ->
            def reCode = response.getStatus()
            reJson = response.getData()
            state.transactionId = response.headers['transactionId']
            log("ClimFavoritesStart reCode: ${reCode}", "debug")
            log("ClimFavoritesStart reJson: ${reJson}", "debug")
            def cookies = response.headers['Set-Cookie']
            if (cookies) {
                state.sessionCookies = cookies
            }

            // Check for API errors
            if (reJson.containsKey('error')) {
                log("ClimFavoritesStart API error: ${reJson.error.errorDesc} (${reJson.error.errorCode})", "warn")
                sendEventHelper(device, "ClimFavoritesStart", false)
                return
            }
            if (reCode == 200) {
                log("ClimFavoritesStart successful.","info")
                sendEventHelper(device, "ClimFavoritesStart", true)                
            } else {
                sendEventHelper(device, "ClimFavoritesStart", false)
            }
        }
    } catch (groovyx.net.http.HttpResponseException e) {
        if (e.getStatusCode() == 429) {
            log.error "Rate limited during ClimFavoritesStart"
            sendEventHelper(device, "ClimFavoritesStart", false)
            return
        }
        if (e.getStatusCode() == 401 && !retry) {
            log('Authorization token expired, will refresh and retry.', 'warn')
            if (refreshToken()) {
                ClimFavoritesStart(device, pcmdFavoriteNbr, true, refresh)
            }
        }
        log("ClimFavoritesStart failed -- ${e.getLocalizedMessage()}: ${e.response.data}", "error")
    }
}
void ClimManual(com.hubitat.app.DeviceWrapper device, pcmdTemp, pstrCmdDefrost, pcmdHeating, Boolean retry=false, Boolean refresh=false) {
    log("ClimManual() called", "trace")
    if (!stay_logged_in) {
        if (!authorize()) {
            sendEventHelper(device, "ClimManual", false)
            return
        }
    }

    if (!hasValidToken()) {
        if (!authorize()) {
            sendEventHelper(device, "ClimManual", false)
            return
        }
    }
    rateLimitDelay()

    // IMPORTANT: Get PIN token FIRST - it may trigger re-authentication
    def pAuth = getPinToken(device)
    if (!pAuth) {
        log("Cannot start climate - failed to get PIN token", "error")
        sendEventHelper(device, "ClimManual", false)
        return
    }
    // Build headers AFTER getPinToken (which may have refreshed the access token)
    def uri = API_URL + "evc/rfon"
    API_Headers.referer = "https://mybluelink.ca/remote/climate"
    def headers = API_Headers
    headers.put('accessToken', state.access_token)  // Uses fresh token if re-auth happened
    headers.put('vehicleId', state.vehicleId)
    headers.put('pAuth', pAuth)
    headers.put('offset', '-5')
    headers.put('REFRESH', refresh.toString())
    headers.put('Deviceid', generateDeviceId())
    if (state.sessionCookies) {
        headers.put("Cookie", state.sessionCookies)
    }
    int valTimeout = refresh ? 240 : 60
    def temperatureIndex = CA_TEMP_RANGE.indexOf(pcmdTemp) + 6
    def indexHex = Integer.toHexString(temperatureIndex).toUpperCase()
    if (indexHex.length() == 1) {
        indexHex = '0' + indexHex
    }
    indexHex = indexHex + "H"
    def boolean pcmdDefrost
    if (pstrCmdDefrost == "false") {
        pcmdDefrost = false
    } else {
        pcmdDefrost = true
    }
    if (pcmdHeating == "false") {
        pcmdHeating = 0
    } else {
        pcmdHeating = 1
    }
    def hvacInfo = [
        "hvacInfo": [
            "airCtrl": 1, 
            "airTemp": [
                "hvacTempType": 1, 
                "unit": 0, 
                "value": indexHex
            ],
            "defrost": pcmdDefrost, 
            "heating1": pcmdHeating
        ], 
        "pin": bluelink_pin
    ]
    def params = [
        uri: uri,
        headers: headers,
        requestContentType: "application/json",
        body: hvacInfo,
        ignoreSSLIssues: true,
        timeout: valTimeout
    ]   
    LinkedHashMap reJson = []
    try {
        httpPost(params) { response ->
            def reCode = response.getStatus()
            reJson = response.getData()
            state.transactionId = response.headers['transactionId']
            log("ClimManual reCode: ${reCode}", "debug")
            log("ClimManual reJson: ${reJson}", "debug")
            def cookies = response.headers['Set-Cookie']
            if (cookies) {
                state.sessionCookies = cookies
            }

            // Check for API errors
            if (reJson.containsKey('error')) {
                log("ClimManual API error: ${reJson.error.errorDesc} (${reJson.error.errorCode})", "warn")
                sendEventHelper(device, "ClimManual", false)
                return
            }
            if (reCode == 200) {
                log("ClimManual successful.","info")
                sendEventHelper(device, "ClimManual", true)                
            } else {
                sendEventHelper(device, "ClimManual", false)
            }
        }
    } catch (groovyx.net.http.HttpResponseException e) {
        if (e.getStatusCode() == 429) {
            log.error "Rate limited during ClimManual"
            sendEventHelper(device, "ClimManual", false)
            return
        }
        if (e.getStatusCode() == 401 && !retry) {
            log('Authorization token expired, will refresh and retry.', 'warn')
            if (refreshToken()) {
                ClimManual(device, pcmdTemp, pstrCmdDefrost, pcmdHeating, true, refresh)
            }
        }
        log("ClimManual failed -- ${e.getLocalizedMessage()}: ${e.response.data}", "error")
    }
}
void ClimStop(com.hubitat.app.DeviceWrapper device, Boolean retry=false, Boolean refresh=false) {
    log("ClimStop() called", "trace")
    if (!stay_logged_in) {
        if (!authorize()) {
            sendEventHelper(device, "ClimStop", false)
            return
        }
    }

    if (!hasValidToken()) {
        if (!authorize()) {
            sendEventHelper(device, "ClimStop", false)
            return
        }
    }
    rateLimitDelay()

    // IMPORTANT: Get PIN token FIRST - it may trigger re-authentication
    def pAuth = getPinToken(device)
    if (!pAuth) {
        log("Cannot stop climate - failed to get PIN token", "error")
        sendEventHelper(device, "ClimStop", false)
        return
    }
    // Build headers AFTER getPinToken (which may have refreshed the access token)
    def uri = API_URL + "evc/rfoff"
    API_Headers.referer = "https://mybluelink.ca/remote/climate"
    def headers = API_Headers
    headers.put('accessToken', state.access_token)  // Uses fresh token if re-auth happened
    headers.put('vehicleId', state.vehicleId)
    headers.put('pAuth', pAuth)
    headers.put('offset', '-5')
    headers.put('REFRESH', refresh.toString())
    headers.put('Deviceid', generateDeviceId())
    if (state.sessionCookies) {
        headers.put("Cookie", state.sessionCookies)
    }
    int valTimeout = refresh ? 240 : 60
    def thePin = ["pin": bluelink_pin]
    def params = [
        uri: uri,
        headers: headers,
        requestContentType: "application/json",
        body: thePin,
        ignoreSSLIssues: true,
        timeout: valTimeout
    ]  
    LinkedHashMap reJson = []
    try {
        httpPost(params) { response ->
            def reCode = response.getStatus()
            reJson = response.getData()
            log("ClimStop reCode: ${reCode}", "debug")
            log("ClimStop reJson: ${reJson}", "debug")
            def cookies = response.headers['Set-Cookie']
            if (cookies) {
                state.sessionCookies = cookies
            }

            // Check for API errors
            if (reJson.containsKey('error')) {
                log("ClimStop API error: ${reJson.error.errorDesc} (${reJson.error.errorCode})", "warn")
                sendEventHelper(device, "ClimStop", false)
                return
            }
            if (reCode == 200) {
                log("ClimStop successful.","info")
                sendEventHelper(device, "ClimStop", true)                
            } else {
                sendEventHelper(device, "ClimStop", false)
            }
        }
    } catch (groovyx.net.http.HttpResponseException e) {
        if (e.getStatusCode() == 429) {
            log.error "Rate limited during ClimStop"
            sendEventHelper(device, "ClimStop", false)
            return
        }
        if (e.getStatusCode() == 401 && !retry) {
            log('Authorization token expired, will refresh and retry.', 'warn')
            if (refreshToken()) {
                ClimStop(device, true, refresh)
            }
        }
        log("ClimStop failed -- ${e.getLocalizedMessage()}: ${e.response.data}", "error")
    }
}
void StartCharge(com.hubitat.app.DeviceWrapper device, Boolean retry=false, Boolean refresh=false) {
    log("StartCharge() called", "trace")
    if (!stay_logged_in) {
        if (!authorize()) {
            sendEventHelper(device, "StartCharge", false)
            return
        }
    }

    if (!hasValidToken()) {
        if (!authorize()) {
            sendEventHelper(device, "StartCharge", false)
            return
        }
    }
    rateLimitDelay()

    // IMPORTANT: Get PIN token FIRST - it may trigger re-authentication
    def pAuth = getPinToken(device)
    if (!pAuth) {
        log("Cannot start charge - failed to get PIN token", "error")
        sendEventHelper(device, "StartCharge", false)
        return
    }
    // Build headers AFTER getPinToken (which may have refreshed the access token)
    def uri = API_URL + "evc/rcstrt"
    API_Headers.referer = "https://mybluelink.ca/login"
    def headers = API_Headers
    headers.put('accessToken', state.access_token)  // Uses fresh token if re-auth happened
    headers.put('vehicleId', state.vehicleId)
    headers.put('pAuth', pAuth)
    headers.put('offset', '-5')
    headers.put('REFRESH', refresh.toString())
    headers.put('Deviceid', generateDeviceId())
    if (state.sessionCookies) {
        headers.put("Cookie", state.sessionCookies)
    }
    int valTimeout = refresh ? 240 : 60
    def thePin = ["pin": bluelink_pin]
    def params = [
        uri: uri,
        headers: headers,
        requestContentType: "application/json",
        body: thePin,
        ignoreSSLIssues: true,
        timeout: valTimeout
    ]  
    LinkedHashMap reJson = []
    try {
        httpPost(params) { response ->
            def reCode = response.getStatus()
            reJson = response.getData()
            log("StartCharge reCode: ${reCode}", "debug")
            log("StartCharge reJson: ${reJson}", "debug")
            def cookies = response.headers['Set-Cookie']
            if (cookies) {
                state.sessionCookies = cookies
            }

            // Check for API errors
            if (reJson.containsKey('error')) {
                log("StartCharge API error: ${reJson.error.errorDesc} (${reJson.error.errorCode})", "warn")
                sendEventHelper(device, "StartCharge", false)
                return
            }
            if (reCode == 200) {
                log("StartCharge successful.","info")
                sendEventHelper(device, "StartCharge", true)                
            } else {
                sendEventHelper(device, "StartCharge", false)
            }
        }
    } catch (groovyx.net.http.HttpResponseException e) {
        if (e.getStatusCode() == 429) {
            log.error "Rate limited during StartCharge"
            sendEventHelper(device, "StartCharge", false)
            return
        }
        if (e.getStatusCode() == 401 && !retry) {
            log('Authorization token expired, will refresh and retry.', 'warn')
            if (refreshToken()) {
                StartCharge(device, true, refresh)
            }
        }
        log("StartCharge failed -- ${e.getLocalizedMessage()}: ${e.response.data}", "error")
    }
}
void StopCharge(com.hubitat.app.DeviceWrapper device, Boolean retry=false, Boolean refresh=false) {
    log("StopCharge() called", "trace")
    if (!stay_logged_in) {
        if (!authorize()) {
            sendEventHelper(device, "StopCharge", false)
            return
        }
    }

    if (!hasValidToken()) {
        if (!authorize()) {
            sendEventHelper(device, "StopCharge", false)
            return
        }
    }
    rateLimitDelay()

    // IMPORTANT: Get PIN token FIRST - it may trigger re-authentication
    def pAuth = getPinToken(device)
    if (!pAuth) {
        log("Cannot stop charge - failed to get PIN token", "error")
        sendEventHelper(device, "StopCharge", false)
        return
    }
    // Build headers AFTER getPinToken (which may have refreshed the access token)
    def uri = API_URL + "evc/rcstp"
    API_Headers.referer = "https://mybluelink.ca/login"
    def headers = API_Headers
    headers.put('accessToken', state.access_token)  // Uses fresh token if re-auth happened
    headers.put('vehicleId', state.vehicleId)
    headers.put('pAuth', pAuth)
    headers.put('offset', '-5')
    headers.put('REFRESH', refresh.toString())
    headers.put('Deviceid', generateDeviceId())
    if (state.sessionCookies) {
        headers.put("Cookie", state.sessionCookies)
    }
    int valTimeout = refresh ? 240 : 60
    def thePin = ["pin": bluelink_pin]
    def params = [
        uri: uri,
        headers: headers,
        requestContentType: "application/json",
        body: thePin,
        ignoreSSLIssues: true,
        timeout: valTimeout
    ]  
    LinkedHashMap reJson = []
    try {
        httpPost(params) { response ->
            def reCode = response.getStatus()
            reJson = response.getData()
            log("StopCharge reCode: ${reCode}", "debug")
            log("StopCharge reJson: ${reJson}", "debug")
            def cookies = response.headers['Set-Cookie']
            if (cookies) {
                state.sessionCookies = cookies
            }

            // Check for API errors
            if (reJson.containsKey('error')) {
                log("StopCharge API error: ${reJson.error.errorDesc} (${reJson.error.errorCode})", "warn")
                sendEventHelper(device, "StopCharge", false)
                return
            }
            if (reCode == 200) {
                log("StopCharge successful.","info")
                sendEventHelper(device, "StopCharge", true)                
            } else {
                sendEventHelper(device, "StopCharge", false)
            }
        }
    } catch (groovyx.net.http.HttpResponseException e) {
        if (e.getStatusCode() == 429) {
            log.error "Rate limited during StopCharge"
            sendEventHelper(device, "StopCharge", false)
            return
        }
        if (e.getStatusCode() == 401 && !retry) {
            log('Authorization token expired, will refresh and retry.', 'warn')
            if (refreshToken()) {
                StopCharge(device, true, refresh)
            }
        }
        log("StopCharge failed -- ${e.getLocalizedMessage()}: ${e.response.data}", "error")
    }
}
void getChargeLimits(com.hubitat.app.DeviceWrapper device, Boolean retry=false)
{
    log("getChargeLimits() called", "trace")
    if (!hasValidToken()) {
        log("getChargeLimits: No valid token", "warn")
        return
    }
    rateLimitDelay()
    def uri = API_URL + "evc/selsoc"

    def headers = [
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        "content-type": "application/json",
        "accept": "application/json",
        "accept-encoding": "gzip, deflate, br",
        "host": "mybluelink.ca",
        "client_id": "HATAHSPACA0232141ED9722C67715A0B",
        "client_secret": "CLISCR01AHSPA",
        "from": "SPA",
        "language": "0",
        "offset": "-5",
        "accessToken": state.access_token,
        "vehicleId": state.vehicleId,
        "Deviceid": generateDeviceId()
    ]
    if (state.sessionCookies) {
        headers.put("Cookie", state.sessionCookies)
    }

    def params = [ uri: uri, headers: headers, timeout: 60 ]

    LinkedHashMap reJson = []
    try
    {
        httpPost(params) { response ->
            def reCode = response.getStatus()
            reJson = response.getData()
            log("getChargeLimits reCode: ${reCode}", "debug")
            log("getChargeLimits reJson: ${reJson}", "debug")
            def cookies = response.headers['Set-Cookie']
            if (cookies) {
                state.sessionCookies = cookies
            }
        }

        // Check for API errors
        if (reJson.containsKey('error')) {
            log("getChargeLimits API error: ${reJson.error.errorDesc} (${reJson.error.errorCode})", "warn")
            if ((reJson.error.errorCode == "7602" || reJson.error.errorCode == "7404" || reJson.error.errorCode == "7606") && !retry) {            
                log('Token issue, will refresh and retry.', 'warn')
                if (authorize()) {
                    pauseExecution(2000)
                    getChargeLimits(device, true)
                }
            }
            return
        }

        if (reJson?.result && reJson.result.size() >= 2) {
            sendEvent(device, [name: 'DCLevel', value: reJson.result[0].level])
            sendEvent(device, [name: 'ACLevel', value: reJson.result[1].level])
            sendEvent(device, [name: 'ACRange', value: reJson.result[1].dte.rangeByFuel.totalAvailableRange.value])

            log("✓ Charge limits updated - DC: ${reJson.result[0].level}%, AC: ${reJson.result[1].level}%", "info")
        }
    }
    catch (groovyx.net.http.HttpResponseException e)
    {
        if (e.getStatusCode() == 429) {
            log.error "Rate limited during getChargeLimits"
            return
        }
        if (e.getStatusCode() == 401 && !retry)
        {
            log.warn 'Authorization token expired, will refresh and retry.'
            if (refreshToken()) {
                pauseExecution(2000)
                getChargeLimits(device, true)
            }
            return
        }
        log.error "getChargeLimits failed -- ${e.getLocalizedMessage()}: ${e.response?.data}"
    }
    catch (Exception e)
    {
        log.error "getChargeLimits error: ${e.message}"
    }
}
void setChargeLimits(com.hubitat.app.DeviceWrapper device, pcmdACLevel, pcmdDCLevel, Boolean retry=false, Boolean refresh=false) {
    log("setChargeLimits() called - AC: ${pcmdACLevel}%, DC: ${pcmdDCLevel}%", "info")
    if (!stay_logged_in) {
        if (!authorize()) {
            log("Cannot set charge limits - authentication failed", "error")
            return
        }
    }

    if (!hasValidToken()) {
        if (!authorize()) {
            log("Cannot set charge limits - authentication failed", "error")
            return
        }
    }
    rateLimitDelay()

    // IMPORTANT: Get PIN token FIRST - it may trigger re-authentication
    def pAuth = getPinToken(device)
    if (!pAuth) {
        log("Cannot set charge limits - failed to get PIN token", "error")
        return
    }
    // Build headers AFTER getPinToken (which may have refreshed the access token)
    def uri = API_URL + "evc/setsoc"
    def headers = API_Headers
    headers.put('accessToken', state.access_token)  // Uses fresh token if re-auth happened
    headers.put('vehicleId', state.vehicleId)
    headers.put('pAuth', pAuth)
    headers.put('offset', '-5')
    headers.put('REFRESH', refresh.toString())
    headers.put('Deviceid', generateDeviceId())
    if (state.sessionCookies) {
        headers.put("Cookie", state.sessionCookies)
    }
    API_Headers.referer = 'https://mybluelink.ca/remote/tsoc'
    API_Headers.from = 'SPA'
    int valTimeout = refresh ? 240 : 60
    def theBody = [
        "pin": bluelink_pin,
        "tsoc": [
            ["level": pcmdDCLevel, "plugType": "0"],
            ["level": pcmdACLevel, "plugType": "1"]
        ]
    ]
    def params = [
        uri: uri,
        headers: headers,
        requestContentType: "application/json",
        body: theBody,
        ignoreSSLIssues: true,
        timeout: 120
    ]  
    LinkedHashMap reJson = []
    try {
        httpPost(params) { response ->
            def reCode = response.getStatus()
            reJson = response.getData()
            log("setChargeLimits reCode: ${reCode}", "debug")
            log("setChargeLimits reJson: ${reJson}", "debug")
            def cookies = response.headers['Set-Cookie']
            if (cookies) {
                state.sessionCookies = cookies
            }

            // Check for API errors
            if (reJson.containsKey('error')) {
                log("setChargeLimits API error: ${reJson.error.errorDesc} (${reJson.error.errorCode})", "warn")
                return
            }

            if (reCode == 200 && reJson?.result) {
                log("✓ setChargeLimits successful", "info")
                // Update the device attributes immediately
                if (reJson.result.size() >= 2) {
                    sendEvent(device, [name: 'DCLevel', value: reJson.result[0].level])
                    sendEvent(device, [name: 'ACLevel', value: reJson.result[1].level])
                    log("✓ Charge limits set - DC: ${reJson.result[0].level}%, AC: ${reJson.result[1].level}%", "info")
                }
            }
        }
    } catch (groovyx.net.http.HttpResponseException e) {
        if (e.getStatusCode() == 429) {
            log.error "Rate limited during setChargeLimits"
            return
        }
        if (e.getStatusCode() == 401 && !retry) {
            log('Authorization token expired, will refresh and retry.', 'warn')
            if (refreshToken()) {
                setChargeLimits(device, pcmdACLevel, pcmdDCLevel, true, refresh)
            }
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
private Boolean LockUnlockHelper(com.hubitat.app.DeviceWrapper device, String urlSuffix, Boolean retry=false, Boolean refresh=false) {
    log("LockUnlockHelper() called", "trace")
    if (!stay_logged_in) {
        if (!authorize()) {
            return false
        }
    }

    if (!hasValidToken()) {
        if (!authorize()) {
            return false
        }
    }
    rateLimitDelay()

    // IMPORTANT: Get PIN token FIRST - it may trigger re-authentication
    def pAuth = getPinToken(device)
    if (!pAuth) {
        log("Cannot lock/unlock - failed to get PIN token", "error")
        return false
    }
    // Build headers AFTER getPinToken (which may have refreshed the access token)
    def uri = API_URL + urlSuffix
    API_Headers.referer = "https://mybluelink.ca/login"
    def headers = API_Headers
    headers.put('accessToken', state.access_token)  // Uses fresh token if re-auth happened
    headers.put('vehicleId', state.vehicleId)
    headers.put('pAuth', pAuth)
    headers.put('offset', '-5')
    headers.put('Deviceid', generateDeviceId())
    if (state.sessionCookies) {
        headers.put("Cookie", state.sessionCookies)
    }
    def thePin = ["pin": bluelink_pin]
    def params = [
        uri: uri,
        headers: headers,
        requestContentType: "application/json",
        body: thePin,
        ignoreSSLIssues: true,
        timeout: 120
    ]  
    int reCode = 0
    try {
        httpPost(params) { response ->
            reCode = response.getStatus()
            state.transactionId = response.headers['transactionId']    
            def cookies = response.headers['Set-Cookie']
            if (cookies) {
                state.sessionCookies = cookies
            }

            def reJson = response.getData()
            // Check for API errors
            if (reJson?.containsKey('error')) {
                log("LockUnlock API error: ${reJson.error.errorDesc} (${reJson.error.errorCode})", "warn")
                return false
            }
        }
    } catch (groovyx.net.http.HttpResponseException e) {
        if (e.getStatusCode() == 429) {
            log.error "Rate limited during Lock/Unlock"
            return false
        }
        if (e.getStatusCode() == 401 && !retry) {
            log('Authorization token expired, will refresh and retry.', 'warn')
            if (refreshToken()) {
                return LockUnlockHelper(device, urlSuffix, true, refresh)
            }
        }
        log("LockUnlockHelper failed -- ${e.getLocalizedMessage()}: Status: ${e.response.getStatus()}", "error")
        return false
    }
    return (reCode == 200)
}
private void listDiscoveredVehicles() {
    def children = getChildDevices()
    if (children.isEmpty()) {
        section {
            paragraph "No vehicles discovered yet."
        }
        return
    }
    def builder = new StringBuilder("<ul>")
    children.each { child ->
        if (child != null) {
            builder << "<li><a href='/device/edit/${child.getId()}'>${child.getLabel()}</a></li>"
        }
    }
    builder << "</ul>"
    def theCars = builder.toString()
    section {
        paragraph "Discovered vehicles are listed below:"
        paragraph theCars
    }
}
private com.hubitat.app.ChildDeviceWrapper CreateChildDriver(String name, String vin) {
    log("CreateChildDriver called", "trace")
    String vehicleNetId = "Hyundai_" + vin
    com.hubitat.app.ChildDeviceWrapper newDevice = null
    try {
        newDevice = addChildDevice(
            'jbilodea',
            'Canadian Hyundai Bluelink Driver',
            vehicleNetId,
            [
                name : "Canadian Hyundai Bluelink Driver",
                label: name
            ]
        )
        log("Child device created successfully: ${name} (${vin})", "info")
    }
    catch (com.hubitat.app.exception.UnknownDeviceTypeException e) {
        log("${e.message} - You need to install the 'Canadian Hyundai Bluelink Driver'.", "error")
    }
    catch (IllegalArgumentException e) {
        log("Child device already exists: ${vehicleNetId}. Ignored: ${e.message}", "warn")
        newDevice = getChildDevice(vehicleNetId)
    }
    catch (Exception e) {
        log("Unexpected error while creating child device: ${e.message}", "error")
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
def getFormat(type, myText="") {
    if(type == "header-green") return "<div style='color:#ffffff; border-radius: 5px 5px 5px 5px; font-weight: bold; padding-left: 10px; background-color:#81BC00; border: 1px solid;box-shadow: 2px 3px #A9A9A9'>${myText}</div>"
    if(type == "header-light-grey") return "<div style='color:#000000; border-radius: 5px 5px 5px 5px; font-weight: bold; padding-left: 10px; background-color:#D8D8D8; border: 1px solid;box-shadow: 2px 3px #A9A9A9'>${myText}</div>"
    if(type == "header-blue-grad") return "<div style='color:#000000; border-radius: 5px 5px 5px 5px; line-height: 2.0; font-weight: bold; padding-left: 10px; background: linear-gradient(to bottom, #d4e4ef 0%,#86aecc 100%);  border: 2px'>${myText}</div>"
    if(type == "header-center-blue-grad") return "<div style='text-align:center; color:#000000; border-radius: 5px 5px 5px 5px; font-weight: bold; padding-left: 10px; background: linear-gradient(to bottom, #d4e4ef 0%,#86aecc 100%);  border: 2px'>${myText}</div>"
    if(type == "item-light-grey") return "<div style='color:#000000; border-radius: 5px 5px 5px 5px; font-weight: normal; padding-left: 10px; background-color:#D8D8D8; border: 1px solid'>${myText}</div>"
    if(type == "line") return "<hr style='background-color:#1A77C9; height: 1px; border: 0;'>"
    if(type == "title") return "<h2 style='color:#1A77C9;font-weight: bold'>${myText}</h2>"
}
