/**
 *  Simple Relocker
 *
 *  Copyright 2017 Eric Henson
 *
 */
definition(
    name: "Simple Relocker",
    namespace: "AgentOddball",
    author: "Eric Henson",
    description: "Reengage locks some time after they are disengaged.",
    category: "My Apps",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")


preferences {
	section("Lock to control") {
		input("deviceLock", "capability.lock", title: "Which?", required: true)
        input("relockSeconds", "number", title: "Seconds?", required: true, range: "1..*", defaultValue: "300")
	}
}

// App freshly installed
def installed() {
	log.debug "Installed with settings: ${settings}"

	// Init with settings
	initialize()
}

// Settings updated
def updated() {
	log.debug "Updated with settings: ${settings}"

	// Remove listeners and cancel timers
	unsubscribe()
    unschedule()
    
    // Re-init with new settings
	initialize()
}

// Configure app with current settings
def initialize() {
	// Attach a listener to the lock device's unlock event
	subscribe(deviceLock, "lock.unlocked", unlockHandler)
}

// Handle unlock event on the lock device
def unlockHandler(evt) {
	log.debug "Unlock detected. Starting ${relockSeconds} second relock timer."
    
    // Cancel any pending timers and start a new relock timer
    unschedule()
    runIn(relockSeconds, relockTimerElapsed)
}

// Runs after the relock timer has fully elapsed.  Locks device if conditions are met.
def relockTimerElapsed() {
	log.debug "Relock timer elapsed."
    
    // Relock device if all conditions are met
	if(shouldRelock()) {
    	log.debug "Relocking."
        deviceLock.lock()
    }
}

// Checks to see if a relock should happen
def shouldRelock() {
	return checkLockOkay()
}

// Returns true if the lock device state is not currently locked
def checkLockOkay() {
	def lockOkay = (deviceLock.currentState("lock").value != "locked")
    if(!lockOkay) {
    	log.debug "Already locked."
    }
    return lockOkay
}
