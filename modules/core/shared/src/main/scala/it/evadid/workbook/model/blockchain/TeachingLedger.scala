package it.evadid.workbook.model.blockchain

import upickle.default.ReadWriter

/** Integer-point bookkeeping for the cabin exercise, not Bitcoin transactions or signatures. */
case class LedgerTransfer(sender: String, recipient: String, amount: BigInt) derives ReadWriter

enum LedgerError derives ReadWriter {
  case InvalidInitialBalances, UnknownAccount, InvalidAmount, SelfTransfer, InsufficientFunds
}

object TeachingLedger {
  /** Applies transfers in order. A rejected history never returns partially updated balances. */
  def calculate(initial: Map[String, BigInt], transfers: List[LedgerTransfer]): Either[LedgerError, Map[String, BigInt]] = {
    if (initial.isEmpty || initial.exists((name, balance) => name.trim.isEmpty || name != name.trim || balance < 0))
      Left(LedgerError.InvalidInitialBalances)
    else transfers.foldLeft[Either[LedgerError, Map[String, BigInt]]](Right(initial)) { (result, transfer) =>
      result.flatMap { balances =>
        if (!balances.contains(transfer.sender) || !balances.contains(transfer.recipient)) Left(LedgerError.UnknownAccount)
        else if (transfer.amount <= 0) Left(LedgerError.InvalidAmount)
        else if (transfer.sender == transfer.recipient) Left(LedgerError.SelfTransfer)
        else if (balances(transfer.sender) < transfer.amount) Left(LedgerError.InsufficientFunds)
        else Right(balances.updated(transfer.sender, balances(transfer.sender) - transfer.amount)
          .updated(transfer.recipient, balances(transfer.recipient) + transfer.amount))
      }
    }
  }
}
