package com.mel.fernbank.ledger.api.mapper;

import com.mel.fernbank.ledger.api.dto.AuditEventResponse;
import com.mel.fernbank.ledger.domain.AuditEvent;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AuditEventMapper {

	AuditEventResponse toResponse(AuditEvent auditEvent);
}
