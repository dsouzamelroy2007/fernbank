package com.mel.fernbank.ledger.api.mapper;

import com.mel.fernbank.ledger.api.dto.PayeeResponse;
import com.mel.fernbank.ledger.payee.PayeeResult;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PayeeMapper {

	PayeeResponse toResponse(PayeeResult result);
}
