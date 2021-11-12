import groovy.transform.Field

@Field static HashMap jmutex = [:]
@Field static HashMap commandQueue = [:]

metadata {
    definition (name: "BluetoothLanConnector", namespace: "iharyadi", author: "iharyadi") {
        singleThreaded: true
        capability "Sensor"
    }   
}

private int DEVICE_COMMAND()
{
    return 0
}

private int MANAGER_COMMAND()
{
    return 1
}

private int BLUETOOTH_DEVICE()
{
    return 2    
}

private int USB_DEVICE()
{
    return 3    
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
        def flatData = [data].flatten().findAll { it != null }
       
        if(Integer.parseInt(flatData[0]) == DEVICE_COMMAND())
        {
            if(Integer.parseInt(flatData[1]) == BLUETOOTH_DEVICE())
            {
                return childDevice.parse(flatData);
            }
        }    
    }
    catch(Exception e)
    {
        log.error e
        log.error "data ${origData}"
    }
        
    return null
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
    if(commandQueue[device.deviceNetworkId] == null)
    {
       commandQueue[device.deviceNetworkId] = [] 
    }
    
    def deviceCmdQueue = commandQueue[device.deviceNetworkId]
    
    def it = null
    
    if(jmutex[device.deviceNetworkId] == null)
    {
        jmutex[device.deviceNetworkId] = new java.util.concurrent.Semaphore(1)
    }
    
    def deviceMutext = jmutex[device.deviceNetworkId]
        
    deviceMutext.acquire()
    
    try
    {
        
        
        deviceCmdQueue += cmds
    
        if(!deviceCmdQueue.isEmpty())
        {
            it = deviceCmdQueue.remove(0) 
        }  
    }
    catch(e)
    {
        log.error "Error: ${e}"
    }
    
    deviceMutext.release()
    
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
    def childDevice = getChildDevice( "${device.deviceNetworkId}-LanDevice" )
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
    
    if(commandQueue[device.deviceNetworkId] == null)
    {
       commandQueue[device.deviceNetworkId] = [] 
    }
    
    if(jmutex[device.deviceNetworkId] == null)
    {
        jmutex[device.deviceNetworkId] = new java.util.concurrent.Semaphore(1)
    }
}

def installed() {
    initialize();
}

def configure() {
    initialize();
}

def uninstalled() {
    unschedule()
    commandQueue.remove(device.deviceNetworkId)
}
