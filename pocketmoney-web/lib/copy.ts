export type Copy = {
  code: "en" | "sw" | "fr";
  welcomeTo: string;
  welcomeBody: string;
  welcomeHint: string;
  signInLead: string;
  useFingerprint: string;
  fingerprintLater: string;
  orWord: string;
  backWelcome: string;
  recentActivity: string;
  noActivity: string;
  loading: string;
  chooseLanguage: string;
  continueAction: string;
  morning: string;
  afternoon: string;
  evening: string;
  signIn: string;
  createAccount: string;
  signInHelp: string;
  createHelp: string;
  createCodeHelp: string;
  verifyCreate: string;
  familyTitle: string;
  familyHint: string;
  familyCount: (count: number) => string;
  openNumberHint: string;
  addByReg: string;
  father: string;
  mother: string;
  guardian: string;
  parentRole: string;
  phone: string;
  pin: string;
  yourName: string;
  pleaseWait: string;
  switchToSignIn: string;
  switchToCreate: string;
  forgotPin: string;
  sendCode: string;
  smsCode: string;
  newPin: string;
  backToSignIn: string;
  pinHelp: string;
  resetTitle: string;
  resetHelp: string;
  resetCodeHelp: string;
  savePin: string;
  resend: string;
  codeSent: string;
  homeHint: string;
  walletBalance: string;
  cardBalance: string;
  sendEasily: string;
  sendEasilyBody: string;
  navHome: string;
  navStudents: string;
  navSettings: string;
  yourStudents: string;
  agentWallets: string;
  addStudent: string;
  addAnother: string;
  regNo: string;
  sendMoney: string;
  activity: string;
  backToDashboard: string;
  took: string;
  withdrawReason: string;
  signOut: string;
  amount: string;
  amountHint: string;
  momoNumber: string;
  cardOnly: string;
  noCard: string;
  cardDisabled: string;
  paidNotice: string;
  sending: string;
  feeLine: (receive: number, transferFee: number, withdrawFee: number, debit: number) => string;
};

const english: Copy = {
  code: "en",
  welcomeTo: "Welcome to",
  welcomeBody: "Choose your language to continue and start managing your pocket money the smarter way.",
  welcomeHint: "Sign in or register to continue",
  signInLead: "Enter your phone number and PIN to access your Xander Pocket Money account.",
  useFingerprint: "Use Fingerprint",
  fingerprintLater: "Fingerprint sign-in is not available on this device.",
  orWord: "or",
  backWelcome: "Back",
  recentActivity: "Recent activity",
  noActivity: "No wallet activity yet.",
  loading: "Loading…",
  chooseLanguage: "Choose your language",
  continueAction: "Continue",
  morning: "Good morning",
  afternoon: "Good afternoon",
  evening: "Good evening",
  signIn: "Sign in",
  createAccount: "Create account",
  signInHelp: "Enter the phone number and PIN for your account.",
  createHelp: "Use your name, a valid Rwandan phone number, and a 4-digit PIN. The account opens straight away.",
  createCodeHelp: "Enter the 6-digit code from the SMS. The account opens only after this code matches.",
  verifyCreate: "Verify and create account",
  familyTitle: "Your students",
  familyHint: "These students are registered to your phone number.",
  familyCount: (count) =>
    count === 1
      ? "1 student is registered to this number."
      : `${count} students are registered to this number.`,
  openNumberHint: "No student is saved on this number yet. Enter a registration number to send money.",
  addByReg: "Enter the student's registration number, then send money.",
  father: "Father",
  mother: "Mother",
  guardian: "Guardian",
  parentRole: "Parent",
  phone: "Phone number",
  pin: "PIN",
  yourName: "Your name",
  pleaseWait: "Please wait…",
  switchToSignIn: "Already registered? Sign in",
  switchToCreate: "New here? Create an account",
  forgotPin: "Forgot PIN?",
  sendCode: "Send SMS code",
  smsCode: "SMS code",
  newPin: "New PIN",
  backToSignIn: "Back to sign in",
  pinHelp: "4 digits",
  resetTitle: "Reset your PIN",
  resetHelp: "We text a 6-digit code to this number.",
  resetCodeHelp: "The SMS should arrive in a few seconds. Type the code, then choose a new 4-digit PIN.",
  savePin: "Save new PIN",
  resend: "Resend SMS",
  codeSent: "Code sent",
  homeHint: "Send pocket money onto a student card.",
  walletBalance: "Wallet balance",
  cardBalance: "Card balance",
  sendEasily: "Send pocket money easily",
  sendEasilyBody: "Support your student's school journey.",
  navHome: "Home",
  navStudents: "Students",
  navSettings: "Settings",
  yourStudents: "Your students",
  agentWallets: "Student wallets",
  addStudent: "Add a student",
  addAnother: "Add another student",
  regNo: "Registration number",
  sendMoney: "Send money",
  activity: "Activity",
  backToDashboard: "Back to dashboard",
  took: "Took",
  withdrawReason: "Reason",
  signOut: "Sign out",
  amount: "Amount",
  amountHint: "Type any whole amount from 200 to 500,000 RWF.",
  momoNumber: "Mobile Money number",
  cardOnly: "The canteen only taps this card. No PIN.",
  noCard: "No card is saved for this student yet.",
  cardDisabled: "This card is disabled.",
  paidNotice: "Money is on the student card.",
  sending: "Sending money to the wallet…",
  feeLine: (_receive, _transferFee, _withdrawFee, debit) => `${debit.toLocaleString()} RWF`,
};

const swahili: Copy = {
  ...english,
  code: "sw",
  welcomeTo: "Karibu",
  welcomeBody: "Chagua lugha ili kuendelea na kuanza kusimamia pesa za mfukoni kwa urahisi zaidi.",
  welcomeHint: "Ingia au fungua akaunti ili kuendelea",
  signInLead: "Weka nambari ya simu na PIN ili kufungua akaunti yako ya Xander Pocket Money.",
  useFingerprint: "Tumia alama ya kidole",
  fingerprintLater: "Kuingia kwa alama ya kidole hakupatikani kwenye kifaa hiki.",
  orWord: "au",
  backWelcome: "Rudi",
  recentActivity: "Shughuli za hivi karibuni",
  noActivity: "Bado hakuna miamala.",
  loading: "Inapakia…",
  chooseLanguage: "Chagua lugha",
  continueAction: "Endelea",
  morning: "Habari za asubuhi",
  afternoon: "Habari za mchana",
  evening: "Habari za jioni",
  signIn: "Ingia",
  createAccount: "Fungua akaunti",
  signInHelp: "Weka nambari ya simu na PIN ya akaunti yako.",
  createHelp: "Weka jina lako, nambari halali ya simu ya Rwanda, na PIN ya tarakimu 4. Akaunti inafunguka mara moja.",
  createCodeHelp: "Weka nambari ya tarakimu 6 kutoka kwenye SMS. Akaunti inafunguka tu nambari hiyo ikilingana.",
  verifyCreate: "Thibitisha na ufungue akaunti",
  familyTitle: "Wanafunzi wako",
  familyHint: "Wanafunzi hawa wamesajiliwa kwenye nambari yako ya simu.",
  familyCount: (count) =>
    count === 1 ? "Mwanafunzi 1 amesajiliwa kwenye nambari hii." : `Wanafunzi ${count} wamesajiliwa kwenye nambari hii.`,
  openNumberHint: "Bado hakuna mwanafunzi aliyesajiliwa kwenye nambari hii. Weka nambari ya usajili ili kutuma pesa.",
  addByReg: "Weka nambari ya usajili ya mwanafunzi, kisha tuma pesa.",
  father: "Baba",
  mother: "Mama",
  guardian: "Mlezi",
  parentRole: "Mzazi",
  phone: "Nambari ya simu",
  pin: "PIN",
  yourName: "Jina lako",
  pleaseWait: "Tafadhali subiri…",
  switchToSignIn: "Tayari umesajiliwa? Ingia",
  switchToCreate: "Mgeni hapa? Fungua akaunti",
  forgotPin: "Umesahau PIN?",
  sendCode: "Tuma nambari ya SMS",
  smsCode: "Nambari ya SMS",
  newPin: "PIN mpya",
  backToSignIn: "Rudi kuingia",
  pinHelp: "Tarakimu 4",
  resetTitle: "Weka PIN upya",
  resetHelp: "Tunatuma nambari ya tarakimu 6 kwenye nambari hii.",
  resetCodeHelp: "SMS inafika baada ya sekunde chache. Weka nambari hiyo, kisha chagua PIN mpya ya tarakimu 4.",
  savePin: "Hifadhi PIN mpya",
  resend: "Tuma tena SMS",
  codeSent: "Nambari imetumwa",
  homeHint: "Tuma pesa za mfukoni kwenye kadi ya mwanafunzi.",
  walletBalance: "Salio la akaunti",
  cardBalance: "Salio la kadi",
  sendEasily: "Tuma pesa za mfukoni kwa urahisi",
  sendEasilyBody: "Saidia mwanafunzi wako katika safari ya shule.",
  navHome: "Mwanzo",
  navStudents: "Wanafunzi",
  navSettings: "Mipangilio",
  yourStudents: "Wanafunzi wako",
  agentWallets: "Akaunti za wanafunzi",
  addStudent: "Ongeza mwanafunzi",
  addAnother: "Ongeza mwanafunzi mwingine",
  regNo: "Nambari ya usajili",
  sendMoney: "Tuma pesa",
  activity: "Shughuli",
  backToDashboard: "Rudi kwenye dashibodi",
  took: "Alichukua",
  withdrawReason: "Sababu",
  signOut: "Toka",
  amount: "Kiasi",
  amountHint: "Weka kiasi chochote kamili kuanzia 200 hadi 500,000 RWF.",
  momoNumber: "Nambari ya Mobile Money",
  cardOnly: "Katika kantini, mwanafunzi anatumia kadi tu. Hakuna PIN.",
  noCard: "Bado hakuna kadi iliyohifadhiwa kwa mwanafunzi huyu.",
  cardDisabled: "Kadi hii imezimwa.",
  paidNotice: "Pesa ziko kwenye kadi ya mwanafunzi.",
  sending: "Inatuma pesa kwenye kadi…",
  feeLine: (_receive, _transferFee, _withdrawFee, debit) => `${debit.toLocaleString()} RWF`,
};

const french: Copy = {
  ...english,
  code: "fr",
  welcomeTo: "Bienvenue sur",
  welcomeBody: "Choisissez votre langue pour continuer et gérer l'argent de poche plus simplement.",
  welcomeHint: "Connectez-vous ou créez un compte pour continuer",
  signInLead: "Saisissez votre numéro de téléphone et votre PIN pour accéder à votre compte Xander Pocket Money.",
  useFingerprint: "Utiliser l'empreinte",
  fingerprintLater: "La connexion par empreinte n'est pas disponible sur cet appareil.",
  orWord: "ou",
  backWelcome: "Retour",
  recentActivity: "Activité récente",
  noActivity: "Aucun mouvement pour le moment.",
  loading: "Chargement…",
  chooseLanguage: "Choisissez votre langue",
  continueAction: "Continuer",
  morning: "Bonjour",
  afternoon: "Bon après-midi",
  evening: "Bonsoir",
  signIn: "Se connecter",
  createAccount: "Créer un compte",
  signInHelp: "Saisissez le numéro de téléphone et le PIN de votre compte.",
  createHelp: "Indiquez votre nom, un numéro rwandais valide et un PIN à 4 chiffres. Le compte s'ouvre tout de suite.",
  createCodeHelp: "Saisissez le code à 6 chiffres reçu par SMS. Le compte s'ouvre seulement si le code correspond.",
  verifyCreate: "Vérifier et créer le compte",
  familyTitle: "Vos élèves",
  familyHint: "Ces élèves sont enregistrés sur votre numéro de téléphone.",
  familyCount: (count) =>
    count === 1
      ? "1 élève est enregistré sur ce numéro."
      : `${count} élèves sont enregistrés sur ce numéro.`,
  openNumberHint: "Aucun élève n'est encore enregistré sur ce numéro. Saisissez un numéro d'inscription pour envoyer de l'argent.",
  addByReg: "Saisissez le numéro d'inscription de l'élève, puis envoyez l'argent.",
  father: "Père",
  mother: "Mère",
  guardian: "Tuteur",
  parentRole: "Parent",
  phone: "Numéro de téléphone",
  pin: "PIN",
  yourName: "Votre nom",
  pleaseWait: "Veuillez patienter…",
  switchToSignIn: "Déjà inscrit ? Se connecter",
  switchToCreate: "Nouveau ici ? Créer un compte",
  forgotPin: "PIN oublié ?",
  sendCode: "Envoyer le code SMS",
  smsCode: "Code SMS",
  newPin: "Nouveau PIN",
  backToSignIn: "Retour à la connexion",
  pinHelp: "4 chiffres",
  resetTitle: "Réinitialiser votre PIN",
  resetHelp: "Nous envoyons un code à 6 chiffres à ce numéro.",
  resetCodeHelp: "Le SMS arrive en quelques secondes. Saisissez le code, puis choisissez un nouveau PIN à 4 chiffres.",
  savePin: "Enregistrer le nouveau PIN",
  resend: "Renvoyer le SMS",
  codeSent: "Code envoyé",
  homeHint: "Envoyez l'argent de poche sur la carte d'un élève.",
  walletBalance: "Solde du compte",
  cardBalance: "Solde de la carte",
  sendEasily: "Envoyer l'argent de poche facilement",
  sendEasilyBody: "Accompagnez le parcours scolaire de votre élève.",
  navHome: "Accueil",
  navStudents: "Élèves",
  navSettings: "Réglages",
  yourStudents: "Vos élèves",
  agentWallets: "Comptes des élèves",
  addStudent: "Ajouter un élève",
  addAnother: "Ajouter un autre élève",
  regNo: "Numéro d'inscription",
  sendMoney: "Envoyer de l'argent",
  activity: "Activité",
  backToDashboard: "Retour au tableau de bord",
  took: "A pris",
  withdrawReason: "Motif",
  signOut: "Se déconnecter",
  amount: "Montant",
  amountHint: "Saisissez n'importe quel montant entier de 200 à 500 000 RWF.",
  momoNumber: "Numéro Mobile Money",
  cardOnly: "À la cantine, l'élève présente seulement la carte. Pas de PIN.",
  noCard: "Aucune carte n'est encore enregistrée pour cet élève.",
  cardDisabled: "Cette carte est désactivée.",
  paidNotice: "L'argent est sur la carte de l'élève.",
  sending: "Envoi de l'argent sur la carte…",
  feeLine: (_receive, _transferFee, _withdrawFee, debit) => `${debit.toLocaleString()} RWF`,
};

export function copyFor(code: string): Copy {
  if (code === "sw" || code === "rw") return swahili;
  if (code === "fr") return french;
  return english;
}

export function relationLabel(copy: Copy, code: string): string {
  if (code === "father") return copy.father;
  if (code === "mother") return copy.mother;
  if (code === "guardian") return copy.guardian;
  if (code === "parent") return copy.parentRole;
  return "";
}
