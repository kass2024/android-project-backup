export type ParentAccount = {
  id: number;
  name: string;
  phone: string;
  role: string;
};

export type StudentWallet = {
  id: number;
  regno: string;
  name: string;
  className: string;
  school: string;
  schoolId: number;
  photoUrl: string;
  cardLinked: boolean;
  cardDisabled: boolean;
  hasSpendPin: boolean;
  balance: number | null;
  linked: boolean;
  relation: string;
};

export type LedgerLine = {
  name: string;
  qty: number;
  unit: string;
};

export type LedgerItem = {
  id: number;
  label: string;
  amount: number;
  balance: number | null;
  status: string;
  merchant: string;
  message: string;
  detail: string;
  reason: string;
  lines: LedgerLine[];
  createdAt: string;
  type: number;
};

export type Screen =
  | { name: "lang" }
  | { name: "auth" }
  | { name: "family"; registeredAs: string; relation: string; students: StudentWallet[] }
  | { name: "home" }
  | { name: "pay"; studentId: number }
  | { name: "activity"; studentId: number };
