import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import groovy.json.*

public static String rootTopic() { return "zigbee2mqtt" }

metadata {
  definition(
    name: "Zigbee2MQTT Link Driver for Arrival Sensor",
    namespace: "iharyadi",
    author: "Chris Lawson, et al",
    description: "A link between MQTT broker and MQTT Link app",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Connections/Cat-Connections.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Connections/Cat-Connections@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Connections/Cat-Connections@3x.png"
    ) {
    capability "Notification"

    preferences {
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
      input(
        name: "debugLogging", 
        type: "bool", 
        title: "Enable debug logging", 
        required: false, 
        default: false
      )
    }

    // Provided for broker setup and troubleshooting
    command "publish", [[name:"topic*",type:"STRING", title:"test",description:"Topic"],[name:"message",type:"STRING", description:"Message"]]
    command "subscribe",[[name:"topic*",type:"STRING", description:"Topic"]]
    command "unsubscribe",[[name:"topic*",type:"STRING", description:"Topic"]]
    command "connect"
    command "disconnect"
  }
}

void startConnection() {
  debug("startConnection...")
  
  try {   
    interfaces.mqtt.connect(getBrokerUri(),
    "hubitat_${getHubId()}", 
    settings?.brokerUser, 
    settings?.brokerPassword, 
lastWillTopic: "${getTopicPublishPrefix()}LWT",
lastWillQos: 0, 
lastWillMessage: "offline", 
lastWillRetain: true)
    
    // delay for connection
    pauseExecution(1000)
    
  } catch(Exception e) {
    error("[d:initialize] ${e}")
  }
}

def checkConnection()
{
  if (notMqttConnected()) {
    debug("[d:checkConnection] not connected")
    connect()
  }
}

def installed()
{
}

def uninstalled()
{
  unschedule()
}

def updated()
{
  unschedule()   
  if(mqttConnected())
  {
    disconnect();
    connect();
  }
}

// ========================================================
// MQTT COMMANDS
// ========================================================

def publish(topic, payload) {
  publishMqtt(topic, payload)
}

def subscribe(topic) {
  connect()
  if(mqttConnected())
  { 
    debug("[d:subscribe] full topic: ${getTopicPrefix()}${topic}")
    interfaces.mqtt.subscribe("${getTopicPrefix()}${topic}")
  }
}

def unsubscribe(topic) {
  connect()
  if(mqttConnected())
  {   
    debug("[d:unsubscribe] full topic: ${getTopicPrefix()}${topic}")
    interfaces.mqtt.unsubscribe("${getTopicPrefix()}${topic}")
  }
}

def connect() {
  if(mqttConnected())
  {
    return   
  }
  startConnection()
  if(mqttConnected())
  {
    connected()
  }
}

def disconnect() {
  
  if(notMqttConnected())
  {
    return   
  }
  
  try {
    disconnected()
    interfaces.mqtt.disconnect()
  } catch(e) {
    warn("Disconnection from broker failed", ${e.message})
    if (interfaces.mqtt.isConnected()) connected()
  }
}

// ========================================================
// MQTT METHODS
// ========================================================

// Parse incoming message from the MQTT broker
def parse(String event) {
  def message = interfaces.mqtt.parseMessage(event)  

  debug("[d:parse] Received MQTT message: ${message}")
  
  //log.info "${groovy.json.JsonOutput.toJson(parent)}"
  
  def json = new groovy.json.JsonOutput().toJson([
topic: message.topic,
value: message.payload
  ])
  
  parent.mqttLinkHandler([name: "message", value: json ])
}

def mqttClientStatus(status) {
  debug("[d:mqttClientStatus] status: ${status}")
}

def publishMqtt(topic, payload, qos = 0, retained = false) {
  connect()
  if(mqttConnected())
  {   
    def pubTopic = "${getTopicPublishPrefix()}${topic}"

    try {
      interfaces.mqtt.publish("${pubTopic}", payload, qos, retained)
      debug("[d:publishMqtt] topic: ${pubTopic} payload: ${payload}")
      
    } catch (Exception e) {
      error("[d:publishMqtt] Unable to publish message: ${e}")
    }
  }
}

// ========================================================
// ANNOUNCEMENTS
// ========================================================

def connected() {
  debug("[d:connected] Connected to broker")
  announceLwtStatus("online") 
  sendEvent (name: "connectionState", value: "connected")
  parent.mqttLinkConnectionHandler([name: "connectionState", value: "connected"])
  runEvery1Minute(checkConnection)
}

def disconnected() {
  debug("[d:disconnected] Disconnected from broker")
  announceLwtStatus("offline")
  sendEvent (name: "connectionState", value: "disconnected")
  parent.mqttLinkConnectionHandler([name: "connectionState", value: "disconnected"])
  unschedule(checkConnection)
}

def announceLwtStatus(String status) {
  publishMqtt("LWT", status)
  publishMqtt("FW", "${location.hub.firmwareVersionString}")
  publishMqtt("IP", "${location.hub.localIP}")
  publishMqtt("UPTIME", "${location.hub.uptime}")
}

// ========================================================
// HELPERS
// ========================================================

def normalize(name) {
  return name.replaceAll("[^a-zA-Z0-9]+","-").toLowerCase()
}

def getBrokerUri() {        
  return "tcp://${settings?.brokerIp}:${settings?.brokerPort}"
}

def getHubId() {
  def hub = location.hubs[0]
  def hubNameNormalized = normalize(hub.name)
  return "${hubNameNormalized}-${hub.hardwareID}".toLowerCase()
}

def getTopicPrefix() {
  return "${rootTopic()}/"
}

def getTopicPublishPrefix() {
  return "hubitat/${getHubId()}/"
}

def mqttConnected() {
  return interfaces.mqtt.isConnected()
}

def notMqttConnected() {
  return !mqttConnected()
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