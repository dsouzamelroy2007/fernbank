package com.mel.fernbank.ledger.api.mapper;

import com.mel.fernbank.ledger.api.dto.MoneyCodec;
import com.mel.fernbank.ledger.api.dto.MoneyMovementResponse;
import com.mel.fernbank.ledger.api.dto.ScheduledTransferResponse;
import com.mel.fernbank.ledger.api.dto.TransferResponse;
import com.mel.fernbank.ledger.banking.MoneyMovementResult;
import com.mel.fernbank.ledger.banking.TransferResult;
import com.mel.fernbank.ledger.domain.ScheduledTransfer;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = MoneyCodec.class)
public interface TransferMapper {

	TransferResponse toResponse(TransferResult result);

	MoneyMovementResponse toResponse(MoneyMovementResult result);

	ScheduledTransferResponse toResponse(ScheduledTransfer scheduledTransfer);
}
