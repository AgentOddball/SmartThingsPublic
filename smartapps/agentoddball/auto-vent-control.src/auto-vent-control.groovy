/**
 *  Auto Vent Control
 *
 *  A homebrew solution to control the exhaust fans in my master and guest bathrooms with one app.  There are two use cases:
 *    Case 1: The guest bathroom has one door and one vent switch.
 *    Case 2: The master bathroom has an outer and inner area, each with a door and a switch.
 *
 *  In case 1, the vent should run when the door is closed.  In case 2, both vents should run when the outer door is closed,
 *  to maximize venting during showers.  When the inner door is closed, only the inner vent should run, to provide venting
 *  to the inner room.  In both cases, an optional timeout can be configured.  This timeout must elapse before the vents are
 *  actually turned off after the doors are opened.
 *
 *  Copyright 2017 Eric Henson
 */
definition(
    name: "Auto Vent Control",
    namespace: "AgentOddball",
    author: "Eric Henson",
    description: "Controls exhaust vent based on master bathroom door activity.  Custom solution, and not intended for general release.",
    category: "My Apps",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")

// User-level configuration
preferences {
	section("Inner Devices") {
        input("deviceDoorInner", "capability.contactSensor", title: "Inner Door", required: true)
        input("deviceVentInner", "capability.switch", title: "Inner Exhaust Vent", required: true)
	}
    section("Outer Devices (Optional)") {
    	input("deviceDoorOuter", "capability.contactSensor", title: "Outer Door", required: false)
		input("deviceVentOuter", "capability.switch", title: "Outer Exhaust Vent", required: false)
    }
    section("Configuration") {
    	input("extraVentTimeMinutes", "number", title: "Minutes of extra venting after doors opened", required: true, range: "0..*", defaultValue: "15")
    }
}

// Run when the SmartApp has been loaded into a location
def installed() {
	log.debug "Installed with settings: ${settings}"

	initialize()
}

// Run when the settings have been updated
def updated() {
	log.debug "Updated with settings: ${settings}"

	// Re-init everything and start from a fresh state
	unsubscribe()
    unschedule()
	initialize()
}

// Perform initial work from a configured but uninitialized state
def initialize() {
	// Watch the inner and outer doors for changes
	subscribe(deviceDoorOuter, "contact", deviceStateChangedHandler)
    subscribe(deviceDoorInner, "contact", deviceStateChangedHandler)
}

// React to a state change in one of the doors. Control the vents in response.
def deviceStateChangedHandler(evt) {
	log.debug "${evt.displayName} changed state to ${evt.value}. Canceling any countdown in progress."
    
    // Clear any countdown currently in progress.  We will restart if we need a new one.
    unschedule()
    
    // Check to see if the inner vent should be turned on
    if (innerVentShouldTurnOn()) {
    	log.debug "Turning on ${deviceVentInner.displayName}"
    	deviceVentInner.on()
    }
    
    // Check to see if the outer vent should be turned on
    if (outerVentShouldTurnOn()) {
    	log.debug "Turning on ${deviceVentOuter.displayName}"
    	deviceVentOuter.on()
    }
    
    // If either vent should be turned off, do it immediately or start a countdown for the configured time
    if (innerVentShouldTurnOff() || outerVentShouldTurnOff()) {
    	if (extraVentTimeMinutes == 0) {
        	ventCountdownElapsed()
        } else {
        	log.debug "Starting ${extraVentTimeMinutes} minute countdown"
    		runIn(extraVentTimeMinutes * 60, ventCountdownElapsed)
        }
    }
}

// Determines if the inner vent is presently off but should be on
def innerVentShouldTurnOn() {
	def outerDoorClosed = getOuterDoorState() == "closed"
	def innerDoorClosed = deviceDoorInner.currentState("contact").value == "closed"
    def innerVentOff = deviceVentInner.currentState("switch").value == "off"
    
    return (innerDoorClosed || outerDoorClosed) && innerVentOff
}

// Determines if the outer vent is presently off but should be on
def outerVentShouldTurnOn() {
	if (!deviceDoorOuter || !deviceVentOuter) {
    	return false;
    }
	def outerDoorClosed = deviceDoorOuter.currentState("contact").value == "closed"
    def outerVentOff = deviceVentOuter.currentState("switch").value == "off"
    
    return outerDoorClosed && outerVentOff
}

// Determines if the inner vent is presenty on but should be off
def innerVentShouldTurnOff() {
	def outerDoorOpen = getOuterDoorState() == "open"
    def innerDoorOpen = deviceDoorInner.currentState("contact").value == "open"
    def innerVentOn = deviceVentInner.currentState("switch").value == "on"
    
    return innerDoorOpen && outerDoorOpen && innerVentOn
}

/**
 * Determines if the outer vent is on but should be off.
 * Always returns false if no outer devices are set.
 */
def outerVentShouldTurnOff() {
	if (!deviceDoorOuter || !deviceVentOuter) {
    	return false;
    }
    def outerDoorOpen = deviceDoorOuter.currentState("contact").value == "open"
    def outerVentOn = deviceVentOuter.currentState("switch").value == "on"
    
    return outerDoorOpen && outerVentOn
}

// Run when the vent turn-off countdown is finished.  Turns off any vents which still need it.
def ventCountdownElapsed() {
	log.debug "Vent timeout elapsed"
    
	if (innerVentShouldTurnOff()) {
		log.debug "Turning off ${deviceVentInner.displayName}"
    	deviceVentInner.off()
    }
    
    if (outerVentShouldTurnOff()) {
		log.debug "Turning off ${deviceVentOuter.displayName}"
    	deviceVentOuter.off()
    }
}

/**
 * Returns the state of the outer door, either as "open" or "closed".
 * If there is no outer door configured, then always returns "open".
 */
def getOuterDoorState() {
	if (!deviceDoorOuter) {
    	return "open"
    } else {
    	return deviceDoorOuter.currentState("contact").value
    }
}
