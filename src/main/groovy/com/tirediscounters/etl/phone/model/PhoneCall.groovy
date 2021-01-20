package com.tirediscounters.etl.phone.model

import com.tirediscounters.etl.common.model.FactETLRecord
import com.tirediscounters.utils.string.TdStringUtils
import groovy.transform.AutoClone

@AutoClone
class PhoneCall extends FactETLRecord {
    String m_status
    String m_timeStartedDistribution
    String m_callRingTimeDistribution
	String m_callTalkTime
	String m_segmentNumber
	String m_deviceIdCalling
	String m_telephoneNumberCalling
	String m_deviceIdCalled
	String m_telephoneNumberCalled
	String m_deviceIdOffered
	String m_deviceNameOffered
	String m_answered
	String m_callType
	String m_deviceIdMovedFrom
	String m_telephoneNumberMovedTo
	String m_deviceNameCalled
	String m_partitionName
	String m_telephoneNumberCalledDescription
	String m_telephoneNumberCallingDescription
	String m_timeAnsweredAt
	String m_timeEndedAt
	String m_callTime
	String m_calHeldTime
	String m_agentIdCalled
	String m_agentNameCalled
	String m_agentNameMovedFrom
	String m_didDigits
	String m_dnisDescription
	String m_systemName
	String m_telephoneNumberMovedToDescription
	String m_deviceNameCalling
	String m_deviceNameMovedFrom
	String m_extensionCalled
	String m_extensionCalling
	String m_extensionMovedFrom
	String m_extensionOffered
	String m_agentExtensionCalled
	String m_agentExtensionCalling
	String m_agentExtensionMovedFrom
	String m_agentExtensionOffered
	String m_agentIdCalling
	String m_agentIdMovedFrom
	String m_agentIdOffered
	String m_agentNameCalling
	String m_agentNameOffered
	String m_reasonEnded
	String m_reasonStarted

	protected String getGetterName(final String fieldName) {
        if (fieldName == 'key') return super.getGetterName(fieldName)
        else if (fieldName == 'row_hash') return 'getRowHash'
        else if (fieldName == 'row_creation_timestamp') return 'getRowCreationTimestamp'
        String getterName = 'getM_'
        List<String> fieldParts = Arrays.asList(fieldName.replaceAll('_', ' ').split(' '))
        for (int index = 0; index < fieldParts.size(); index++) {
            if (index == 0) getterName += fieldParts.get(index)
            else getterName += fieldParts.get(index).capitalize()
        }
        return getterName
    }
}
