# Xander Student (card attendance)

Student-only kiosk. Tap an RFID card to clock IN/OUT at the attendance location chosen in Settings. No staff face recognition.

Package: `com.xander.student` · version **4.7**

## Open in Android Studio

1. **File → Open**  
   `C:\Users\user\AndroidStudioProjects\card-student\Face recognition algorithm SDK V2.1.0\demo\MIPS_AI_Basic_BinocularIDCheckOffline_V2.1.0`
2. Run on the tablet. Grant NFC/USB if asked.

## Wireless ADB install

```powershell
cd "C:\Users\user\AndroidStudioProjects\card-student\Face recognition algorithm SDK V2.1.0\demo\MIPS_AI_Basic_BinocularIDCheckOffline_V2.1.0"
# First time (USB): adb tcpip 5555
# Then: adb connect <tablet-ip>:5555
.\install-wireless.ps1
# Or: .\install-wireless.ps1 -Ip 192.168.1.50
```

## First use

1. Enter the school acronym. Default server: `https://schoolmis.xanderglobalacademy.com`
2. Settings (password **HFSecurity**) → pick the student attendance location.
3. Leave the kiosk on and tap student cards.

Offline taps are saved on the device and uploaded when the internet returns. Student roster and today’s IN/OUT state sync both ways. Student photos are cached so the picture appears immediately. The app auto-starts after reboot. Card taps max volume and play chimes + Google TTS (“Welcome / Goodbye”).

USB NFC readers that type the UID like a keyboard work automatically. Built-in NFC uses the tag UID.
