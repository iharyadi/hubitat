import groovy.transform.Field
@Field static volatile List commandQueue = []
@Field static volatile java.util.concurrent.Semaphore jmutex = new java.util.concurrent.Semaphore(1)
metadata {
    definition (name: "BluetoothLanConnector", namespace: "iharyadi", author: "iharyadi") {
        capability "Sensor"
    }   
}

def  parse(def origData) {     
    def childDevice = getChildDevice( "${device.deviceNetworkId}-LanDevice" )
    if(!childDevice)
    {
        return null
    }
    
    String [] data = null;
    try
    {
        data = parseJson(origData)
    }
    catch(Exception e)
    {
        log.error e
        log.error "data ${origData}"
    }
    return childDevice.parse([data].flatten().findAll { it != null });
}

def sendToSerialdevice(byte [] serialCmd)
{
    String serial = serialCmd.encodeHex().toString()
    
    return new hubitat.device.HubAction([
        method: "PUT",
        path: "/api/bleCommand",
        headers: [ HOST: parent.getDeviceUrl(device.deviceNetworkId), "Content-Type": "text/plain" ],
        body : serial], 
    parent.getDeviceUrl(device.deviceNetworkId),
    [timeout:30])
}
    
def sendCommandP(hubitat.device.HubAction cmd)
{    
    def cmds = [cmd]
    return parent.sendHubCommand(cmds)  
}

def sendCommandP()
{
    sendCommandP([])
}

def sendCommandP(List cmds)
{    
    jmutex.acquire()
    def it = null
    try
    {
        commandQueue += cmds
    
        if(!commandQueue.isEmpty())
        {
            it = commandQueue.remove(0) 
        }          
    }
    catch(e)
    {
        log.error "Error: ${e}"
    }
    jmutex.release()
    
    if(!it)
    {
        return   
    }
    
    if(it instanceof hubitat.device.HubAction)
    {
        parent.sendHubCommand(it) 
        runInMillis(0,'sendCommandP',[data:[]])
    }
    else if(it instanceof String  && it.startsWith("delay"))
    {
        String delay = it.replaceAll("\\D+","")
        runInMillis(Integer.parseInt(delay),'sendCommandP',[data:cmds])
    }
    else
    {
        runInMillis(0,'sendCommandP',[data:[]])
    }       
}

def initialize()
{
    def childDevice = parent.getChildDevice( "${device.deviceNetworkId}-LanDevice" )
    if(!childDevice)
    {
        childDevice = addChildDevice("iharyadi", 
        "Bluetooth", 
        "${device.deviceNetworkId}-LanDevice",
        [label: "Bluetooth-${device.deviceNetworkId}",
            isComponent: false, 
            componentName: "Bluetooth-${device.deviceNetworkId}", 
            componentLabel: "Bluetooth-${device.deviceNetworkId}"])
    }
}

def installed() {
    initialize();
}

def configure() {
    initialize();
}