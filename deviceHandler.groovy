/**
 *	TotalConnect Device API
 *
 *	Copyright 2015 Sebastian Gnagnarella
 *
 *	Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *	in compliance with the License. You may obtain a copy of the License at:
 *
 *		http://www.apache.org/licenses/LICENSE-2.0
 *
 *	Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *	on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *	for the specific language governing permissions and limitations under the License.
 *
 *	This Device is based on work by @mhatrey (https://github.com/mhatrey/TotalConnect/blob/master/TotalConnect.groovy)
 *	and Brian Wilson https://github.com/bdwilson/SmartThings-TotalConnect-Device
 *  The goal of this is to expose the TotalConnect Alarm to be used in other routines and in modes.	 To do this, I setup
 *	both lock and switch capabilities for it. Switch On = Armed Stay, Lock On = Armed Away, Switch/Lock Off = Disarm. 
 *	As added features to the original version I mostly refactored all the code and make it work using a cloud based REST API
 * instead of connecting directly from the smartthings cloud as this last one has been blocked by alarmnet
 * I also reduced the number of call to the webservices
 *	 https://community.smartthings.com/t/new-app-integration-with-honeywell-totalconnect-alarm-monitoring-system/
 *
 */
 
preferences {
	// See above ST thread above on how to configure the user/password.	 Make sure the usercode is configured
	// for whatever account you setup. That way, arming/disarming/etc can be done without passing a user code.
	input("userName", "text", title: "Username", description: "Your username for TotalConnect")
	input("password", "password", title: "Password", description: "Your Password for TotalConnect")
	input("deviceId", "text", title: "Device ID - You'll have to look up", description: "Device ID")
	input("locationId", "text", title: "Location ID - You'll have to look up", description: "Location ID")
	input("applicationId", "text", title: "Application ID - It is '14588' currently", description: "Application ID")
	input("applicationVersion", "text", title: "Application Version - use '3.0.32'", description: "Application Version")
	input("appServerBaseUrl", "appServerBaseUrl", title: "AppServerBaseUrl", description: "https://xxxxxxxx.herokuapp.com")
}
metadata {
	definition (name: "TotalConnect Device", namespace: "sgnagnarella", author: "Sebastian Gnagnarella") {
	capability "Lock"
	capability "Refresh"
	capability "Switch"
	capability "DoorControl"
	attribute "status", "string"
}

simulator {
	// TODO: define status and reply messages here
}

tiles {
		standardTile("toggle", "device.status", width: 2, height: 2) {
			state("unknown", label:'${name}', action:"device.refresh", icon:"st.Office.office9", backgroundColor:"#ffa81e")
			state("Armed Stay", label:'${name}', action:"switch.off", icon:"st.Home.home4", backgroundColor:"#79b821", nextState:"Disarmed")
			state("Disarmed", label:'${name}', action:"lock.lock", icon:"st.Home.home2", backgroundColor:"#a8a8a8", nextState:"Armed Away")
			state("Armed Away", label:'${name}', action:"switch.off", icon:"st.Home.home3", backgroundColor:"#79b821", nextState:"Disarmed")
            state("Arming", label:'${name}', icon:"st.Home.home4", backgroundColor:"#ffa81e")
			state("Disarming", label:'${name}', icon:"st.Home.home2", backgroundColor:"#ffa81e")
			state("Armed Max", label:'${name}', action:"switch.off", icon:"st.Home.home3", backgroundColor:"#e12116", nextState:"Disarmed")
		}
		standardTile("statusstay", "device.status", inactiveLabel: false, decoration: "flat") {
			state "default", label:'Arm Stay', action:"switch.on", icon:"st.Home.home4"
		}
		standardTile("statusaway", "device.status", inactiveLabel: false, decoration: "flat") {
			state "default", label:'Arm Away', action:"lock.lock", icon:"st.Home.home3"
		}
		standardTile("statusdisarm", "device.status", inactiveLabel: false, decoration: "flat") {
			state "default", label:'Disarm', action:"switch.off", icon:"st.Home.home2"
		}
		standardTile("statusarmmax", "device.status", inactiveLabel: false, decoration: "flat") {
			state "default", label:'Arm Max', action:"doorControl.close", icon:"st.Home.home3"
		}
		standardTile("refresh", "device.status", inactiveLabel: false, decoration: "flat") {
			state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
		}

		main "toggle"
		details(["toggle", "statusaway", "statusstay", "statusdisarm","statusarmmax", "refresh"])
	}
}

// Login Function. Returns SessionID for rest of the functions
def login(token) {
    log.debug "===== Executed login ====="

    def paramsLogin = [
        uri: settings.appServerBaseUrl + "/Login",
        body: [
            username: settings.userName,
            password: settings.password,
            ApplicationID: settings.applicationId,
            ApplicationVersion: settings.applicationVersion
        ]
    ]

    httpPostJson(paramsLogin) { resp ->
        def results = resp.data
        token = results.AuthenticateLoginResults.SessionID
    }
    
    log.debug "Smart Things has logged In. SessionID: ${token}" 
    return token
}       // returns token


// Logout Function. Called after every mutational command. Ensures the current user is always logged Out.
def logout(token) {
		log.debug "Doing logout - ${token}"
        def paramsLogout = [
            uri: settings.appServerBaseUrl + "/Logout",
            body: [
                SessionID: token,
            ]
        ]

    httpPostJson(paramsLogout) 
} //Takes token as arguement


// Gets Panel Metadata. Takes token & location ID as an argument
Map panelMetaData(token, locationId) {
	def alarmCode
	def lastSequenceNumber
	def lastUpdatedTimestampTicks
	def partitionId

    def paramsGetPanelMetaDataAndFullStatus = [
        uri: settings.appServerBaseUrl + "/GetPanelMetaDataAndFullStatus",
		body: [ 
            SessionID: token, 
            LocationID: locationId, 
            LastSequenceNumber: 0, 
            LastUpdatedTimestampTicks: 0, 
            PartitionID: 1
            ]
    ]

    httpPostJson(paramsGetPanelMetaDataAndFullStatus) { resp ->
        def results = resp.data 
		lastUpdatedTimestampTicks = results.PanelMetadataAndStatusResults.PanelMetadataAndStatus.$.'@LastUpdatedTimestampTicks'
		lastSequenceNumber = results.PanelMetadataAndStatusResults.PanelMetadataAndStatus.$.'@ConfigurationSequenceNumber'
		partitionId = results.PanelMetadataAndStatusResults.PanelMetadataAndStatus.Partitions.PartitionInfo.PartitionID
		alarmCode = results.PanelMetadataAndStatusResults.PanelMetadataAndStatus[0].Partitions[0].PartitionInfo[0].ArmingState[0]
		log.debug results.PanelMetadataAndStatusResults.PanelMetadataAndStatus.Zones.inspect()
    }

	return [alarmCode: alarmCode, lastSequenceNumber: lastSequenceNumber, lastUpdatedTimestampTicks: lastUpdatedTimestampTicks]
} //Should return alarmCode, lastSequenceNumber & lastUpdateTimestampTicks


// Arm Function. Performs arming function
def armAway() {		   
	arm(0) //Arming Away
}

def armStay() {		   
	arm(1) //Arming Stay
}

def arm(armType) {
    def token = login(token)
    
	def locationId = settings.locationId
	def deviceId = settings.deviceId	

     def paramsArm = [
        uri: settings.appServerBaseUrl + "/ArmSecuritySystem",
        body: [
            SessionID: token, 
            LocationID: locationId, 
            DeviceID: deviceId, 
            ArmType: armType,
            UserCode: '-1'
            ]
    ]


    httpPostJson(paramsArm) 
	
    logout(token)
}

def disarm() {
	def token = login(token)
	def locationId = settings.locationId
	def deviceId = settings.deviceId

    def paramsDisarm = [
        uri: settings.appServerBaseUrl + "/DisarmSecuritySystem",
        body: [
            SessionID: token, 
            LocationID: locationId, 
            DeviceID: deviceId, 
            UserCode: '-1'
            ]
    ]


    httpPostJson(paramsDisarm) 
	logout(token)
}

def refresh() {		   
	def token = login(token)
	def locationId = settings.locationId
	def deviceId = settings.deviceId
	log.debug "Doing refresh"
	//httpPost(paramsArm) // Arming function in stay mode
	def metaData = panelMetaData(token, locationId) // Gets AlarmCode
	log.debug "Refresh AlarmCode: " + metaData.alarmCode
    def alarmCode = metaData.alarmCode as int
    
	if (alarmCode == 10200) {
		log.debug "Status is: Disarmed"
		sendEvent(name: "status", value: "Disarmed", displayed: "true", description: "Refresh: Alarm is Disarmed") 
	} else if (alarmCode == 10203) {
		log.debug "Status is: Armed Stay"
		sendEvent(name: "status", value: "Armed Stay", displayed: "true", description: "Refresh: Alarm is Armed Stay") 
	} else if (alarmCode == 10201) {
		log.debug "Status is: Armed Away"
		sendEvent(name: "status", value: "Armed Away", displayed: "true", description: "Refresh: Alarm is Armed Away")
	} else if (alarmCode == 10205) {
		log.debug "Status is: Armed Max"
		sendEvent(name: "status", value: "Armed Max", displayed: "true", description: "Refresh: Alarm is Armed Max") 
	}
	logout(token)
	sendEvent(name: "refresh", value: "true", displayed: "true", description: "Refresh Successful") 
}

// parse events into attributes
def parse(String description) {
	log.debug "Parsing '${description}'"
}

// handle commands
def lock() {
	log.debug "Executing 'Arm Away'"
	armAway()
	sendEvent(name: "lock", value: "lock", displayed: "true", description: "Arming Away") 
	sendEvent(name: "status", value: "Arming", displayed: "true", description: "Updating Status: Arming System")
	runIn(10,refresh)
}

def unlock() {
	log.debug "Executing 'Disarm'"
	disarm()
	sendEvent(name: "unlock", value: "unlock", displayed: "true", description: "Disarming") 
	sendEvent(name: "status", value: "Disarming", displayed: "true", description: "Updating Status: Disarming System") 
	runIn(10,refresh)
}

def on() {
	log.debug "Executing 'Arm Stay'"
	armStay()
	sendEvent(name: "switch", value: "on", displayed: "true", description: "Arming Stay") 
	sendEvent(name: "status", value: "Arming", displayed: "true", description: "Updating Status: Arming System") 
	runIn(10,refresh)
}

def off() {
	log.debug "Executing 'Disarm'"
	disarm()
	sendEvent(name: "switch", value: "off", displayed: "true", description: "Disarming") 
	sendEvent(name: "status", value: "Disarmed", displayed: "true", description: "Updating Status: Disarming System") 
	runIn(10,refresh)
}