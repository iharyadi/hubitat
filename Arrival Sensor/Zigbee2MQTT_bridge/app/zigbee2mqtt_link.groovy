import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import groovy.transform.Field

public static String version() { return "v2.0.0" }
public static String rootTopic() { return "hubitat" }

definition(
name: "Zigbee2MQTT Link for Arrival Sensor",
namespace: "iharyadi",
author: "iharyadi",
parent: "iharyadi:Zigbee2MQTT for Arrival Sensor",
description: "A link between Hubitat device events and MQTT Link Driver",
iconUrl: "https://s3.amazonaws.com/smartapp-icons/Connections/Cat-Connections.png",
iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Connections/Cat-Connections@2x.png",
iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Connections/Cat-Connections@3x.png",
singleInstance: true
)

preferences {
  page(name:"pageMain")
  page(name:"pageRemove")
}

def pageMain() {
  dynamicPage(name: "pageMain", title: "", install: true, uninstall: false) {

    section("<big><b>Zigbee2MQTT Link</b></big>") {
      paragraph "Please enter unique name for this link app."
      label title: "<b>Enter Name:</b>", required: true
      paragraph ""
    }
    
    /*section ("<h3>Specify MQTT Link Driver device</h3>") {
            paragraph "The MQTT Link Driver must be set up prior to the MQTT Link app otherwise the driver will not show up here."
        input (
                name: "mqttLink", 
                type: "capability.notification", 
                title: "Notify this driver", 
                required: true, 
                multiple: false,
            )
      }*/
    
    section ("<h3>Specify MQTT Link Driver Configuration</h3>") {
      input(
        name: "brokerIp", 
        type: "string",
        title: "MQTT Broker IP Address",
        description: "e.g. 192.168.1.200",
        required: true,
        displayDuringSetup: true
      )
      input(
        name: "brokerPort", 
        type: "string",
        title: "MQTT Broker Port",
        description: "e.g. 1883",
        required: true,
        displayDuringSetup: true
      )

      input(
        name: "brokerUser", 
        type: "string",
        title: "MQTT Broker Username",
        description: "e.g. mqtt_user",
        required: false,
        displayDuringSetup: true
      )
      input(
        name: "brokerPassword", 
        type: "password",
        title: "MQTT Broker Password",
        description: "e.g. ^L85er1Z7g&%2En!",
        required: false,
        displayDuringSetup: true
      )
    }
    
    section("Debug Settings") {
      input("debugLogging", "bool", title: "Enable debug logging", required: false, default:false) 
    }

    if (state.installed) {
      section() {
        href "pageRemove", title: "Remove Zigbee2MQTT Link", description: ""
      }
    }
  }
}

def pageRemove() {
  dynamicPage(name: "pageRemove", title: "", install: false, uninstall: true) {
    section() {
      paragraph "<b>WARNING:</b> You are about to remove Zigbee2MQTT Link.", required: true, state: null
    }
  }
}

private String mqttLinkDriverDNI()
{
  return "${app.getId()}-DONOT-UPDATE"
}

private def mqttLinkDriver()
{
  return getChildDevice(mqttLinkDriverDNI())
}

private def uninitialize() {
  unsubscribe()
  mqttLinkDriver()?.unsubscribe("bridge/devices")
  getAllChildDevices().each
  {
    if(it.getDeviceNetworkId() != mqttLinkDriverDNI())
    {
      mqttLinkDriver()?.unsubscribe("${it.getDisplayName()}")
    }
  }
}

private def initialize() {
  def driver = mqttLinkDriver()
  if(driver)
  {
    driver?.subscribe("bridge/devices")
    getAllChildDevices().each
    {
      if(it.getDeviceNetworkId() != mqttLinkDriverDNI())
      {
        driver?.subscribe("${it.getDisplayName()}")
      }
    }
  }
}

def installed() {
  debug("[a:installed] Installed with settings: ${settings}")
  state.installed = true
  
  if(!driver)
  {
    driver = addChildDevice("iharyadi", 
    "Zigbee2MQTT Link Driver for Arrival Sensor", 
    mqttLinkDriverDNI(),
    [label: "${app.getLabel()}-Zigbee2MQTT Link",
isComponent: false,])
  }
  
  if(driver)
  {
    driver.updateSetting("brokerIp",[value:brokerIp,type:"string"])
    driver.updateSetting("brokerPort",[value:brokerPort,type:"string"])
    driver.updateSetting("brokerUser",[value:brokerUser,type:"string"])
    driver.updateSetting("brokerPassword",[value:brokerPassword,type:"password"])
    driver.updateSetting("debugLogging",[value:debugLogging,type:"bool"])
    initialize()
  }
}

def uninstalled() {
  debug("[a:uninstalled] Updated with settings: ${settings}")
  uninitialize()
  deleteChildDevice(mqttLinkDriverDNI())
}

def updated() {
  debug("[a:updated] Updated with settings: ${settings}")
  
  uninitialize()
  initialize()
}


def mqttLinkConnectionHandler(evt) {

  if(evt.value == "connected")
  {
    uninitialize()
    initialize()
  }
}
// Receive an inbound event from the MQTT Link Driver
def mqttLinkHandler(evt) {    
  def json = new JsonSlurper().parseText(evt.value)
  
  if(json.topic == "zigbee2mqtt/bridge/devices")
  {
    def devices = new JsonSlurper().parseText(json.value)
    devices?.each { 
      if (it.interview_completed && !it.interviewing && it.manufacturer == "KMPCIL" && it.model_id == "tagv1")
      {
        debug("[a:mqttLinkHandler] found KMPCIL: ${it.ieee_address}")
        
        String id = "${mqttLinkDriver()?.getId()}-${it.ieee_address}"
        def childDevice = getChildDevice(id)
        if(!childDevice)
        {
          childDevice = addChildDevice("iharyadi", 
          "Virtual Arrival Sensor", 
          id,
          [label: "${it.friendly_name}",
isComponent: false,])
          mqttLinkDriver()?.subscribe("${it.friendly_name}")
        }
        else
        {
          if(childDevice.getDisplayName() !=  "${it.friendly_name}")
          {
            mqttLinkDriver()?.unsubscribe("${childDevice.getDisplayName()}")
            childDevice.setDisplayName("${it.friendly_name}")
            mqttLinkDriver()?.subscribe("${childDevice.getDisplayName()}")
          }
        }
      }
    }
  }
  else
  {
    String friendly_name = json.topic.tokenize("/")[1]
    debug("[a:mqttLinkHandler] message from: ${friendly_name}")
    def device = getAllChildDevices().find{it.getDisplayName() == friendly_name}
    device?.parse(evt)       
  }
}

// ========================================================
// HELPERS
// ========================================================

def getDeviceObj(id) {
  def found
  settings.allDevices.each { device -> 
    if (device.getId() == id) {
      debug("[a:getDeviceObj] Found at $device for $id with id: ${device.id}")
      found = device
    }
  }
  return found
}

def getHubId() {
  def hub = location.hubs[0]
  def hubNameNormalized = normalize(hub.name)
  return "${hubNameNormalized}-${hub.hardwareID}".toLowerCase()
}

def getTopicPrefix() {
  return "${rootTopic()}/${getHubId()}/"
}

def upperCamel(str) {
  def c = str.charAt(0)
  return "${c.toUpperCase()}${str.substring(1)}".toString();
}

def lowerCamel(str) {
  def c = str.charAt(0)
  return "${c.toLowerCase()}${str.substring(1)}".toString();
}

def normalize(name) {
  return name.replaceAll("[^a-zA-Z0-9]+","-").toLowerCase()
}

def normalizeId(name, id) {
  def normalizedName = normalize(name)
  return "${normalizedName}-${id}".toString()
}

def normalizeId(device) {
  return normalizeId(device.displayName, device.id)
}

def normalizedId(com.hubitat.hub.domain.Event evt) {
  def deviceId = evt.deviceId
  
  if (!deviceId && evt.type == "LOCATION_MODE_CHANGE") {
    return normalizeId(evt.displayName, "mode")
  }
  
  return normalizeId(evt.displayName, deviceId)
}

// ========================================================
// LOGGING
// ========================================================

def debug(msg) {
  if (debugLogging) {
    log.debug msg
  }
}

def info(msg) {
  log.info msg
}

def warn(msg) {
  log.warn msg
}

def error(msg) {
  log.error msg
}

