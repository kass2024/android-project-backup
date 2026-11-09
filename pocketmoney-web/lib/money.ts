export function money(amount: number): string {
  return new Intl.NumberFormat("en-US").format(Math.round(amount)) + " RWF";
}

export function chargeQuote(receive: number, percent: number): { debit: number; fee: number; transferFee: number; withdrawFee: number } {
  const withdraw = payerTotal(receive, percent);
  const transfer = payerTotal(receive + withdraw.fee, percent);
  return {
    debit: transfer.debit,
    transferFee: transfer.fee,
    withdrawFee: withdraw.fee,
    fee: transfer.fee + withdraw.fee,
  };
}

export function payerTotal(receive: number, percent: number): { debit: number; fee: number } {
  if (receive < 1 || percent <= 0) return { debit: receive, fee: 0 };
  const rate = percent / 100;
  const guess = Math.ceil(receive / (1 - rate));
  const start = Math.max(receive + 1, guess - 8);
  for (let debit = start; debit <= guess + 40; debit += 1) {
    const fee = Math.round(debit * rate);
    if (debit - fee === receive) return { debit, fee };
  }
  const debit = Math.max(receive + 1, guess);
  return { debit, fee: debit - receive };
}

export function localPhone(phone: string): string {
  const digits = phone.replace(/\D/g, "");
  if (/^2507\d{8}$/.test(digits)) return "0" + digits.slice(3);
  return phone;
}
