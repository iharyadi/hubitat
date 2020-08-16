definition(
name:        "Bluetooth LAN Service Manager",
namespace:   "iharyadi",
author:      "iharyadi",
description: "Bluetooth LAN  Service Manager  ",
category:    "Convenience",
iconUrl: "",
iconX2Url: "",
singleInstance: true
)

mappings {
    path("/device/:mac/:command") { action: [ PUT: "deviceDataHandler" ] }
}

def installed() 
{
    unsubscribe()
    unschedule()
    initialize()
}

def updated() 
{
    unsubscribe()
    unschedule()
    initialize()
}

def uninstalled()
{
    unsubscribe()
    unschedule()
}

def initialize() 
{
    if(!state.accessToken) 
    { 
        state.accessToken= createAccessToken() 
    }
    
    if(!state.deviceURLMap) 
    { 
        state.deviceURLMap = [:] 
    }
    
    if(!state.apiUrl) 
    { 
        state.apiUrl = getFullLocalApiServerUrl()
    }
    
    discoverySearch()
    runEvery5Minutes(discoverySearch)
    runEvery5Minutes(configurationCheck)
    discoverySubscription()
}

def checkDeletedChild()
{
    def newMap = [:]
    state.deviceURLMap.each{
        def child = getChildDevice(it.key)
        if(child != null)
        {
            newMap[it.key] = it.value;
        }
    }
    state.deviceURLMap = newMap
}

def checkApiURL() 
{    
    def apiUrl = getFullLocalApiServerUrl()
    if(state.apiUrl == apiUrl)
    {
        return
    }
    
    state.apiUrl = apiUrl
    state.deviceURLMap.each{
        sendHubCommand(new hubitat.device.HubAction([
                    method: "PUT",
                    path: "/api/saveCallbackURL",
                    headers: [ HOST: it.value, "Content-Type": "text/plain" ],
                    body : apiUrl], 
                it.value,
                [timeout:5]))
    }        
}

def configurationCheck()
{
    checkDeletedChild()
    checkApiURL()
}

def discoverySearch() 
{
    sendHubCommand(new hubitat.device.HubAction("lan discovery urn:schemas-kmpcil-io:device:Gateway:1", hubitat.device.Protocol.LAN))
}

def discoverySubscription() 
{
    subscribe(location, "ssdpTerm.urn:schemas-kmpcil-io:device:Gateway:1", discoverySearchHandler, [filterEvents:false])
}

def getDeviceIpAndPort(device) 
{
    return "${convertHexToIP(device.networkAddress)}:${convertHexToInt(device.deviceAddress)}"
}

def discoverySearchHandler(evt) {
    def event = parseLanMessage(evt.description)
    event << ["hub":evt?.hubId]
    String ssdpUSN = event.ssdpUSN.toString()
    
    def childDevice = getChildDevice(event.mac.toUpperCase())
    if(childDevice)
    {
        if(!state.deviceURLMap) { state.deviceURLMap = [:] }
        state.deviceURLMap[event.mac.toUpperCase()] = getDeviceIpAndPort(event);
    }
    else
    {
        state.device =  event
        discoveryVerify(event)
    }
}

def discoveryVerify(Map device) 
{
    String host = getDeviceIpAndPort(device)
    
    sendHubCommand(
        new hubitat.device.HubAction([
            method: "GET",
            path: device.ssdpPath,
            headers: [ HOST: getDeviceIpAndPort(device), "Content-Type": "text/xml" ]], 
            getDeviceIpAndPort(device),
            [callback: discoveryVerificationHandler,timeout:5])
        )
}

def getDeviceUrl(String mac)
{
    if(!state.deviceURLMap)
    {
        return null;
    }
    
    return state.deviceURLMap[mac];   
}
    
def discoveryVerificationHandler(hubitat.device.HubResponse hubResponse) 
{
    def body = hubResponse.xml
    def device = state.device

    if (!device?.ssdpUSN.contains(body?.device?.UDN?.text())) 
    {        
        return null    
    }
    
    if(!state.deviceURLMap) 
    { 
        state.deviceURLMap = [:] 
    }
    
    state.deviceURLMap[device.mac.toUpperCase()] = getDeviceIpAndPort(device);
      
    try{
        childDevice = addChildDevice("iharyadi", 
            "BluetoothLanConnector", 
            "${device.mac.toUpperCase()}",
            [label: "BluetoothLanConnector-${device.mac}",
                isComponent: false, 
                componentName: "BluetoothLanConnector-${device.mac}", 
                componentLabel: "BluetoothLanConnector-${device.mac}"])
        
         if(childDevice)
         {
             def cmd = updateHubaddress(device)
             sendHubCommand(cmd)     
         }
         else
         {
             log.error "child not created"
         }    
     }
     catch(Exception e){
         log.error e
     } 
}

def updateHubaddressHandler(hubitat.device.HubResponse hubResponse)
{
    if(hubResponse.status == 200)
    {
        def cmd = updateToken(state.device)
        sendHubCommand(cmd)
    }
}

private def updateHubaddress(def device)
{
    return new hubitat.device.HubAction([
        method: "PUT",
        path: "/api/saveCallbackURL",
        headers: [ HOST: getDeviceIpAndPort(device), "Content-Type": "text/plain" ],
        body : getFullLocalApiServerUrl()], 
    getDeviceIpAndPort(device),
    [callback: updateHubaddressHandler,timeout:5]);
}

def updateTokenHandler(hubitat.device.HubResponse hubResponse)
{
}

private def updateToken(def device)
{
    if(!state.accessToken) 
    { 
        state.accessToken = createAccessToken(); 
    }
    
    return new hubitat.device.HubAction([
        method: "PUT",
        path: "/api/saveToken",
        headers: [ HOST: getDeviceIpAndPort(device), "Content-Type": "text/plain" ],
        body : state.accessToken], 
    getDeviceIpAndPort(device),
    [callback: updateTokenHandler,timeout:5])
}

def deviceDataHandler()
{
    String body = request.body
    
    if(params.command  != "bledata")
    {
         return null
    }

    def childDevice = getChildDevice(params.mac.toUpperCase())
    if(!childDevice)
    {
        return null;
    }
    
    try
    {
        childDevice.parse(body)
    }
    catch(e)
    {
        log.error "childDevice.parse(body) Error: ${e}"
    }
}

private Integer convertHexToInt(hex) 
{ 
    return Integer.parseInt(hex,16) 
}

private String convertHexToIP(hex) 
{ 
    return [convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".") 
}