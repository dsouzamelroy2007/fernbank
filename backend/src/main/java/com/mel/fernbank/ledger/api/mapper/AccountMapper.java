package com.mel.fernbank.ledger.api.mapper;

import com.mel.fernbank.ledger.api.dto.AccountResponse;
import com.mel.fernbank.ledger.api.dto.MoneyCodec;
import com.mel.fernbank.ledger.banking.AccountDetail;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = MoneyCodec.class)
public interface AccountMapper {

	AccountResponse toResponse(AccountDetail detail);
}
